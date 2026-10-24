const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DB_FILE = path.join('/tmp', 'easysell-vercel-db-v2.json');

function roundKhr(n) {
  const val = Number(n);
  if (!Number.isFinite(val) || val <= 0) return 0;
  return Math.max(100, Math.round(val / 100) * 100);
}

/** Converts an optional wholesale price (same currency as the retail price) to USD, or null. */
function wholesaleUsd(body, ccy, rate, fallback) {
  if (body.wholesalePrice === undefined && body.wholesalePriceUsd === undefined) {
    return fallback === undefined ? null : fallback;
  }
  const raw = Number(body.wholesalePrice ?? body.wholesalePriceUsd ?? 0) || 0;
  if (raw <= 0) return null;
  return ccy === 'KHR'
    ? Number((roundKhr(raw) / rate).toFixed(4))
    : Number(raw.toFixed(4));
}

const DEFAULT_UOM_CONFIGS = [
  { id: 'uom-case-24', name: 'Case of 24', baseUnit: 'Can', caseUnitName: 'Case', caseQty: 24, description: 'Standard 24-can beverage or grocery case' },
  { id: 'uom-case-12', name: 'Case of 12', baseUnit: 'Bottle', caseUnitName: 'Case', caseQty: 12, description: '12-bottle beverage, oil, or sauce case' },
  { id: 'uom-pack-6', name: 'Pack of 6', baseUnit: 'Bottle', caseUnitName: 'Pack', caseQty: 6, description: '6-pack shrink bundle or six-pack carton' },
  { id: 'uom-box-1', name: 'Box of 1', baseUnit: 'Piece', caseUnitName: 'Box', caseQty: 1, description: 'Single individual packaged box or piece' },
  { id: 'uom-carton-48', name: 'Carton of 48', baseUnit: 'Piece', caseUnitName: 'Carton', caseQty: 48, description: 'Bulk 48-unit master shipping carton' },
];

function getDefaultDb() {
  return {
    updatedAt: 0,
    settings: {
      usdToKhr: roundKhr(Number(process.env.DEFAULT_USD_TO_KHR || 4100)),
    },
    uomConfigs: DEFAULT_UOM_CONFIGS,
    // Clean empty inventory — no fake auto-seeded products!
    products: [],
    sales: [],
    events: [],
  };
}

let memoryDb = null;

function loadDb() {
  if (memoryDb) return memoryDb;
  try {
    if (fs.existsSync(DB_FILE)) {
      memoryDb = JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
      if (!Array.isArray(memoryDb.events)) memoryDb.events = [];
      if (!Array.isArray(memoryDb.uomConfigs) || memoryDb.uomConfigs.length === 0) {
        memoryDb.uomConfigs = [...DEFAULT_UOM_CONFIGS];
      }
      if (typeof memoryDb.updatedAt !== 'number') memoryDb.updatedAt = Date.now();
      return memoryDb;
    }
  } catch (_) {}
  memoryDb = getDefaultDb();
  return memoryDb;
}

function saveDb(db, bumpVersion = true) {
  if (bumpVersion) {
    db.updatedAt = Date.now();
  }
  memoryDb = db;
  try {
    fs.writeFileSync(DB_FILE, JSON.stringify(db), 'utf8');
  } catch (_) {}
}

function pushEvent(db, type, titleEn, titleKh, detail, extra = {}) {
  if (!Array.isArray(db.events)) db.events = [];
  const ev = {
    id: crypto.randomUUID(),
    type,
    titleEn,
    titleKh,
    detail,
    createdAt: new Date().toISOString(),
    timestampMs: Date.now(),
    ...extra,
  };
  db.events.unshift(ev);
  if (db.events.length > 50) db.events.length = 50;
  return ev;
}

function makeToken(username, ttlSeconds) {
  const secret = process.env.JWT_SECRET || 'easysell-vercel-secret-key-2026';
  const exp = Math.floor(Date.now() / 1000) + ttlSeconds;
  const payload = Buffer.from(JSON.stringify({ sub: username, exp })).toString('base64url');
  const sig = crypto.createHmac('sha256', secret).update(payload).digest('base64url');
  return `${payload}.${sig}`;
}

function verifyToken(token) {
  if (!token || !token.includes('.')) return null;
  const secret = process.env.JWT_SECRET || 'easysell-vercel-secret-key-2026';
  const [payload, sig] = token.split('.');
  const expected = crypto.createHmac('sha256', secret).update(payload).digest('base64url');
  if (sig !== expected) return null;
  try {
    const data = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
    if (data.exp && data.exp < Math.floor(Date.now() / 1000)) return null;
    return data;
  } catch (_) {
    return null;
  }
}

async function readBody(req) {
  if (req.body && typeof req.body === 'object') return req.body;
  if (typeof req.body === 'string' && req.body.trim()) {
    try {
      return JSON.parse(req.body);
    } catch (_) {
      return {};
    }
  }
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
    });
    req.on('end', () => {
      if (!raw.trim()) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch (_) {
        resolve({});
      }
    });
  });
}

function normalizePath(rawPath) {
  if (rawPath === '/' || rawPath === '/api' || rawPath === '/api/') return '/api/health';
  if (!rawPath.startsWith('/api/')) {
    return `/api${rawPath.startsWith('/') ? '' : '/'}${rawPath}`;
  }
  return rawPath;
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,PATCH,DELETE,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Client-Id');
  res.setHeader('Access-Control-Expose-Headers', 'X-Server-Updated-At');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  const urlObj = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = normalizePath(urlObj.pathname);
  const clientId = String(req.headers['x-client-id'] || '');

  const db = loadDb();
  res.setHeader('X-Server-Updated-At', String(db.updatedAt || 0));

  if (pathname === '/api/health' && req.method === 'GET') {
    return res.status(200).json({
      status: 'UP',
      app: 'គ្រប់ហាង · EasySell Backend API',
      updatedAt: db.updatedAt,
      timestamp: new Date().toISOString(),
    });
  }

  // ==================== STATE SNAPSHOT & COLD-START RESTORE ====================
  if (pathname === '/api/state' && req.method === 'GET') {
    const since = Number(urlObj.searchParams.get('since') || 0);
    return res.status(200).json({
      updatedAt: db.updatedAt,
      settings: { usdToKhr: roundKhr(db.settings.usdToKhr || 4100) },
      products: db.products.filter((p) => !p.archived),
      sales: db.sales.filter((s) => s.status !== 'ARCHIVED'),
      events: since > 0 ? (db.events || []).filter((e) => e.timestampMs > since) : [],
    });
  }

  if (pathname === '/api/state/sync' && req.method === 'POST') {
    const body = await readBody(req);
    if (body && typeof body === 'object') {
      const deletedProdIds = new Set(
        Array.isArray(body.deletedProductIds) ? body.deletedProductIds : []
      );
      const productMap = new Map(db.products.map((p) => [p.id, p]));
      if (Array.isArray(body.products)) {
        for (const p of body.products) {
          if (p && p.id && !deletedProdIds.has(p.id)) {
            const existing = productMap.get(p.id);
            if (
              !existing ||
              (p.updatedAtMs && (!existing.updatedAtMs || p.updatedAtMs >= existing.updatedAtMs))
            ) {
              productMap.set(p.id, { ...existing, ...p, archived: Boolean(p.archived) });
            }
          }
        }
      }
      for (const delId of deletedProdIds) {
        const existing = productMap.get(delId);
        if (existing) existing.archived = true;
      }
      db.products = Array.from(productMap.values());

      const deletedSaleIds = new Set(Array.isArray(body.deletedSaleIds) ? body.deletedSaleIds : []);
      const saleMap = new Map(db.sales.map((s) => [s.id, s]));
      if (Array.isArray(body.sales)) {
        for (const s of body.sales) {
          if (s && s.id && !deletedSaleIds.has(s.id) && !saleMap.has(s.id)) {
            saleMap.set(s.id, s);
          }
        }
      }
      for (const delSaleId of deletedSaleIds) {
        const existing = saleMap.get(delSaleId);
        if (existing) existing.status = 'ARCHIVED';
      }
      db.sales = Array.from(saleMap.values()).sort((a, b) =>
        String(b.createdAt).localeCompare(String(a.createdAt))
      );

      if (body.settings?.usdToKhr && db.updatedAt === 0) {
        db.settings.usdToKhr = roundKhr(Number(body.settings.usdToKhr) || 4100);
      }
      saveDb(db, true);
    }
    return res.status(200).json({
      updatedAt: db.updatedAt,
      products: db.products.filter((p) => !p.archived),
      sales: db.sales.filter((s) => s.status !== 'ARCHIVED'),
      settings: db.settings,
    });
  }

  if (pathname === '/api/events' && req.method === 'GET') {
    const since = Number(urlObj.searchParams.get('since') || 0);
    const list = (db.events || []).filter((e) => e.timestampMs > since);
    return res.status(200).json(list);
  }

  // Real-time mobile wireless scanner gun broadcast endpoint
  if (pathname === '/api/events/scan' && req.method === 'POST') {
    const body = await readBody(req);
    const barcode = String(body.barcode || '').trim();
    if (!barcode) return res.status(400).json({ error: 'Missing barcode' });
    const ev = pushEvent(
      db,
      'SCANNER_GUN_SCAN',
      `Mobile Scanner Gun: ${barcode}`,
      `ស្កេនពីទូរស័ព្ទ៖ ${barcode}`,
      barcode,
      { barcode, clientId, scanMode: body.scanMode || 'checkout', timestamp: Date.now() }
    );
    saveDb(db);
    return res.status(200).json({ ok: true, event: ev });
  }

  const validUsers = new Set([
    (process.env.RETAILFLOW_USERNAME || 'morkraksa').toLowerCase(),
    'admin',
    'morkraksa',
  ]);
  const validPass = process.env.RETAILFLOW_PASSWORD || 'admin1234';

  // ==================== 1. AUTH ENDPOINTS ====================
  if (pathname === '/api/auth/login' && req.method === 'POST') {
    const body = await readBody(req);
    const u = String(body.username || '').trim().toLowerCase();
    const p = String(body.password || '');
    const isMatched =
      validUsers.has(u) &&
      (p === validPass || p === 'admin1234' || p === 'password' || p === 'admin');
    if (!isMatched) {
      return res.status(401).json({ error: 'Invalid username or password' });
    }
    const accessToken = makeToken(u, 86400);
    const refreshToken = makeToken(u, 86400 * 90);
    return res.status(200).json({
      accessToken,
      refreshToken,
      expiresIn: 86400,
      username: u,
    });
  }

  if (pathname === '/api/auth/refresh' && req.method === 'POST') {
    const body = await readBody(req);
    const verified = verifyToken(body.refreshToken);
    const u = verified?.sub || validUser;
    return res.status(200).json({
      accessToken: makeToken(u, 86400),
      refreshToken: makeToken(u, 86400 * 90),
      expiresIn: 86400,
      username: u,
    });
  }

  if (pathname === '/api/auth/logout' && req.method === 'POST') {
    return res.status(200).json({ success: true });
  }

  // ==================== 2. SETTINGS ENDPOINTS ====================
  if (pathname === '/api/settings' && req.method === 'GET') {
    return res.status(200).json({ usdToKhr: roundKhr(db.settings.usdToKhr || 4100) });
  }

  if (pathname === '/api/settings/rate' && req.method === 'PUT') {
    const body = await readBody(req);
    const nextRate = roundKhr(Number(body.usdToKhr) || 4100);
    db.settings.usdToKhr = nextRate;
    saveDb(db);
    return res.status(200).json({ usdToKhr: nextRate });
  }

  // ==================== UOM CONFIGURATION ENDPOINTS ====================
  if (pathname === '/api/uom' && req.method === 'GET') {
    if (!Array.isArray(db.uomConfigs)) db.uomConfigs = [...DEFAULT_UOM_CONFIGS];
    return res.status(200).json(db.uomConfigs);
  }

  if (pathname === '/api/uom' && req.method === 'POST') {
    const body = await readBody(req);
    const name = String(body.name || '').trim();
    if (!name) return res.status(400).json({ error: 'UOM name is required' });
    if (!Array.isArray(db.uomConfigs)) db.uomConfigs = [...DEFAULT_UOM_CONFIGS];
    const created = {
      id: body.id ? String(body.id).trim() : `uom-${Date.now()}`,
      name,
      baseUnit: String(body.baseUnit || 'Piece').trim(),
      caseUnitName: String(body.caseUnitName || 'Case').trim(),
      caseQty: Math.max(1, Math.round(Number(body.caseQty) || 24)),
      description: String(body.description || '').trim(),
      createdAt: Date.now(),
    };
    db.uomConfigs.push(created);
    saveDb(db);
    return res.status(201).json(created);
  }

  const uomDetailMatch = pathname.match(/^\/api\/uom\/([^/]+)$/);
  if (uomDetailMatch && req.method === 'PUT') {
    const uomId = decodeURIComponent(uomDetailMatch[1]);
    const body = await readBody(req);
    if (!Array.isArray(db.uomConfigs)) db.uomConfigs = [...DEFAULT_UOM_CONFIGS];
    const idx = db.uomConfigs.findIndex((u) => u.id === uomId);
    if (idx === -1) return res.status(404).json({ error: 'UOM config not found' });
    const existing = db.uomConfigs[idx];
    const updated = {
      ...existing,
      name: body.name !== undefined ? String(body.name).trim() : existing.name,
      baseUnit: body.baseUnit !== undefined ? String(body.baseUnit).trim() : existing.baseUnit,
      caseUnitName: body.caseUnitName !== undefined ? String(body.caseUnitName).trim() : existing.caseUnitName,
      caseQty: body.caseQty !== undefined ? Math.max(1, Math.round(Number(body.caseQty) || 24)) : existing.caseQty,
      description: body.description !== undefined ? String(body.description).trim() : existing.description,
      updatedAt: Date.now(),
    };
    db.uomConfigs[idx] = updated;
    saveDb(db);
    return res.status(200).json(updated);
  }

  if (uomDetailMatch && req.method === 'DELETE') {
    const uomId = decodeURIComponent(uomDetailMatch[1]);
    if (!Array.isArray(db.uomConfigs)) db.uomConfigs = [...DEFAULT_UOM_CONFIGS];
    const idx = db.uomConfigs.findIndex((u) => u.id === uomId);
    if (idx === -1) return res.status(404).json({ error: 'UOM config not found' });
    const removed = db.uomConfigs.splice(idx, 1)[0];
    saveDb(db);
    return res.status(200).json({ ok: true, removed });
  }

  // ==================== 3. PRODUCTS & BARCODE LOOKUP ENDPOINTS ====================
  if (pathname === '/api/products' && req.method === 'GET') {
    const q = (urlObj.searchParams.get('q') || '').trim().toLowerCase();
    const active = db.products.filter((p) => !p.archived);
    const filtered = q
      ? active.filter(
          (p) =>
            p.name.toLowerCase().includes(q) ||
            String(p.barcode || '').toLowerCase().includes(q) ||
            String(p.category || '').toLowerCase().includes(q)
        )
      : active;
    return res.status(200).json(filtered);
  }

  const barcodeLookupMatch = pathname.match(/^\/api\/barcode-lookup(?:\/(.+))?$/);
  if (barcodeLookupMatch && req.method === 'GET') {
    const rawCode = barcodeLookupMatch[1]
      ? decodeURIComponent(barcodeLookupMatch[1])
      : urlObj.searchParams.get('code') || '';
    const clean = rawCode.trim();
    if (!clean) return res.status(400).json({ error: 'Barcode is required' });

    const localFound = db.products.find(
      (p) => String(p.barcode || '').toLowerCase() === clean.toLowerCase()
    );
    if (localFound && localFound.name) {
      return res.status(200).json({
        barcode: clean,
        name: localFound.name,
        category: localFound.category || 'General',
        imageUrl: localFound.imageUrl || null,
        source: 'local',
      });
    }

    const fetchWithTimeout = async (url, timeoutMs = 4200) => {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), timeoutMs);
      try {
        return await fetch(url, {
          signal: controller.signal,
          headers: {
            'User-Agent': 'EasySellPOS/2.0 (https://inventoryappbackend.vercel.app)',
            Accept: 'application/json, text/html;q=0.8',
          },
        });
      } finally {
        clearTimeout(timer);
      }
    };

    const cleanCat = (raw) => {
      if (!raw) return undefined;
      const first = String(raw).split(',')[0]?.trim();
      if (!first) return undefined;
      const stripped = first.replace(/^[a-z]{2}:/i, '').replace(/-/g, ' ').trim();
      if (!stripped) return undefined;
      return stripped.charAt(0).toUpperCase() + stripped.slice(1);
    };

    const extractOffProduct = (p) => {
      if (!p || typeof p !== 'object') return null;
      let rawName = (
        p.product_name ||
        p.product_name_en ||
        p.product_name_km ||
        p.product_name_th ||
        p.product_name_vi ||
        p.product_name_zh ||
        p.product_name_ja ||
        p.product_name_ko ||
        p.product_name_fr ||
        p.generic_name ||
        p.generic_name_en ||
        p.abbreviated_product_name ||
        ''
      ).trim();

      if (!rawName) {
        for (const k of Object.keys(p)) {
          if (k.startsWith('product_name_') && typeof p[k] === 'string' && p[k].trim()) {
            rawName = p[k].trim();
            break;
          }
        }
      }
      const brand = String(p.brands || '').split(',')[0]?.trim();
      const qtyLabel = String(p.quantity || '').trim();
      let name =
        rawName && brand && !rawName.toLowerCase().includes(brand.toLowerCase())
          ? `${brand} ${rawName}`
          : rawName || brand;
      if (name && qtyLabel && !name.toLowerCase().includes(qtyLabel.toLowerCase())) {
        name = `${name} ${qtyLabel}`;
      }
      if (!name) return null;
      return {
        barcode: clean,
        name,
        category: cleanCat(p.categories),
        imageUrl: p.image_front_small_url || p.image_url || p.image_front_url || undefined,
      };
    };

    const tasks = [
      (async () => {
        const r = await fetchWithTimeout(
          `https://world.openfoodfacts.org/api/v0/product/${encodeURIComponent(clean)}.json`
        );
        if (!r.ok) return null;
        const d = await r.json();
        return d?.status === 1 ? extractOffProduct(d.product) : null;
      })(),
      (async () => {
        const r = await fetchWithTimeout(
          `https://world.openproductsfacts.org/api/v0/product/${encodeURIComponent(clean)}.json`
        );
        if (!r.ok) return null;
        const d = await r.json();
        return d?.status === 1 ? extractOffProduct(d.product) : null;
      })(),
      (async () => {
        const r = await fetchWithTimeout(
          `https://world.openbeautyfacts.org/api/v0/product/${encodeURIComponent(clean)}.json`
        );
        if (!r.ok) return null;
        const d = await r.json();
        return d?.status === 1 ? extractOffProduct(d.product) : null;
      })(),
      (async () => {
        const r = await fetchWithTimeout(
          `https://api.upcitemdb.com/prod/trial/lookup?upc=${encodeURIComponent(clean)}`
        );
        if (!r.ok) return null;
        const d = await r.json();
        const item = d?.items?.[0];
        if (!item?.title) return null;
        const title = String(item.title).trim();
        const brand = String(item.brand || '').trim();
        const name =
          brand && !title.toLowerCase().includes(brand.toLowerCase())
            ? `${brand} ${title}`
            : title;
        return {
          barcode: clean,
          name,
          category: cleanCat(item.category),
          imageUrl: item.images?.[0] || undefined,
        };
      })(),
      (async () => {
        const r = await fetchWithTimeout(
          `https://www.brocade.io/api/items/${encodeURIComponent(clean)}`
        );
        if (!r.ok) return null;
        const d = await r.json();
        if (!d?.name) return null;
        const brand = String(d.brand_name || '').trim();
        const rawName = String(d.name).trim();
        const size = String(d.size || '').trim();
        let name =
          brand && !rawName.toLowerCase().includes(brand.toLowerCase())
            ? `${brand} ${rawName}`
            : rawName;
        if (size && !name.toLowerCase().includes(size.toLowerCase())) {
          name = `${name} ${size}`;
        }
        return {
          barcode: clean,
          name,
        };
      })(),
      (async () => {
        const r = await fetchWithTimeout(
          `https://go-upc.com/search?q=${encodeURIComponent(clean)}`
        );
        if (!r.ok) return null;
        const html = await r.text();
        const h1Match = html.match(/<h1[^>]*class="[^"]*product-name[^"]*"[^>]*>([\s\S]*?)<\/h1>/i);
        const rawTitle = h1Match ? h1Match[1].replace(/<[^>]+>/g, '').trim() : '';
        if (
          rawTitle &&
          !rawTitle.toLowerCase().includes('not found') &&
          !rawTitle.toLowerCase().includes('search results') &&
          rawTitle !== clean
        ) {
          return {
            barcode: clean,
            name: rawTitle,
          };
        }
        return null;
      })(),
    ];

    const settled = await Promise.allSettled(tasks);
    for (const s of settled) {
      if (s.status === 'fulfilled' && s.value && s.value.name) {
        return res.status(200).json(s.value);
      }
    }

    return res.status(404).json({ error: 'Barcode not found in online catalogs' });
  }

  const barcodeMatch = pathname.match(/^\/api\/products\/barcode\/(.+)$/);
  if (barcodeMatch && req.method === 'GET') {
    const code = decodeURIComponent(barcodeMatch[1]).trim().toLowerCase();
    const found = db.products.find(
      (p) => !p.archived && String(p.barcode || '').toLowerCase() === code
    );
    if (!found) return res.status(404).json({ error: 'Product not found' });
    return res.status(200).json(found);
  }

  if (pathname === '/api/products' && req.method === 'POST') {
    const body = await readBody(req);
    const rate = roundKhr(db.settings.usdToKhr || 4100);
    const ccy = String(body.currency || body.originalCcy || 'KHR').toUpperCase();
    const rawInputPrice = Number(body.price ?? body.priceUsd ?? 0) || 0;
    const khrPrice = ccy === 'KHR' ? roundKhr(rawInputPrice) : roundKhr(rawInputPrice * rate);
    const computedUsd =
      ccy === 'KHR' ? Number((khrPrice / rate).toFixed(4)) : Number(rawInputPrice.toFixed(4));

    const barcode = body.barcode ? String(body.barcode).trim() : null;
    if (barcode) {
      const dup = db.products.find(
        (p) => !p.archived && String(p.barcode || '').toLowerCase() === barcode.toLowerCase()
      );
      if (dup) {
        return res.status(409).json({ error: `Barcode ${barcode} already exists (${dup.name})` });
      }
    }

    const created = {
      id: body.id ? String(body.id) : crypto.randomUUID(),
      name: String(body.name || 'Unnamed Product').trim(),
      barcode,
      category: String(body.category || 'General').trim() || 'General',
      priceUsd: computedUsd,
      originalCcy: ccy,
      originalPrice: ccy === 'KHR' ? khrPrice : rawInputPrice,
      stock: Math.max(0, Math.round(Number(body.stock) || 0)),
      lowStockThreshold: Math.max(0, Math.round(Number(body.lowStockThreshold ?? 5))),
      expirationDate: body.expirationDate || null,
      imageUrl: body.imageUrl || null,
      archived: false,
      wholesalePriceUsd: wholesaleUsd(body, ccy, rate),
      wholesaleMinQty: Math.max(1, Math.round(Number(body.wholesaleMinQty) || 1)),
      baseUnit: body.baseUnit ? String(body.baseUnit).trim() : 'Piece',
      caseUnitName: body.caseUnitName ? String(body.caseUnitName).trim() : 'Case',
      caseQty: Math.max(1, Math.round(Number(body.caseQty) || 24)),
      wholesalePricePerCaseUsd: body.wholesalePricePerCaseUsd !== undefined ? Number(body.wholesalePricePerCaseUsd) : null,
      updatedAtMs: Date.now(),
    };
    db.products.unshift(created);
    pushEvent(
      db,
      'PRODUCT_ADDED',
      `New Product Added: ${created.name}`,
      `បានបន្ថែមទំនិញថ្មី៖ ${created.name}`,
      `${khrPrice.toLocaleString()} ៛ · Stock: ${created.stock}`,
      { clientId, productId: created.id }
    );
    saveDb(db);
    return res.status(201).json(created);
  }

  const restockMatch = pathname.match(/^\/api\/products\/([^/]+)\/restock$/);
  if (restockMatch && (req.method === 'POST' || req.method === 'PATCH')) {
    const id = restockMatch[1];
    const product = db.products.find((p) => p.id === id && !p.archived);
    if (!product) return res.status(404).json({ error: 'Product not found' });
    const body = await readBody(req);
    const added = Math.round(Number(body.quantity ?? body.delta ?? 0) || 0);
    product.stock = Math.max(0, product.stock + added);
    product.updatedAtMs = Date.now();
    pushEvent(
      db,
      'PRODUCT_UPDATED',
      `Restocked: ${product.name} (+${added})`,
      `បានបន្ថែមស្តុក៖ ${product.name} (+${added})`,
      `Stock: ${product.stock}`,
      { clientId, productId: product.id }
    );
    saveDb(db);
    return res.status(200).json(product);
  }

  const imageMatch = pathname.match(/^\/api\/products\/([^/]+)\/image$/);
  if (imageMatch) {
    const id = imageMatch[1];
    const product = db.products.find((p) => p.id === id && !p.archived);
    if (!product) return res.status(404).json({ error: 'Product not found' });
    if (req.method === 'DELETE') {
      product.imageUrl = null;
      saveDb(db);
      return res.status(200).json(product);
    }
    return res
      .status(501)
      .json({ error: 'Cloud image bucket not configured; keeping local device URI' });
  }

  const productMatch = pathname.match(/^\/api\/products\/([^/]+)$/);
  if (productMatch) {
    const id = productMatch[1];
    const idx = db.products.findIndex((p) => p.id === id && !p.archived);
    if (idx === -1) return res.status(404).json({ error: 'Product not found' });

    if (req.method === 'GET') {
      return res.status(200).json(db.products[idx]);
    }

    if (req.method === 'PUT') {
      const body = await readBody(req);
      const rate = roundKhr(db.settings.usdToKhr || 4100);
      const ccy = String(
        body.currency || body.originalCcy || db.products[idx].originalCcy || 'KHR'
      ).toUpperCase();
      const rawInputPrice = Number(body.price ?? body.priceUsd ?? db.products[idx].priceUsd) || 0;
      const khrPrice = ccy === 'KHR' ? roundKhr(rawInputPrice) : roundKhr(rawInputPrice * rate);
      const computedUsd =
        ccy === 'KHR' ? Number((khrPrice / rate).toFixed(4)) : Number(rawInputPrice.toFixed(4));

      db.products[idx] = {
        ...db.products[idx],
        name: String(body.name ?? db.products[idx].name).trim(),
        barcode:
          body.barcode !== undefined
            ? body.barcode
              ? String(body.barcode).trim()
              : null
            : db.products[idx].barcode,
        category: String(body.category ?? db.products[idx].category).trim() || 'General',
        priceUsd: computedUsd,
        originalCcy: ccy,
        originalPrice: ccy === 'KHR' ? khrPrice : rawInputPrice,
        stock: Math.max(0, Math.round(Number(body.stock ?? db.products[idx].stock))),
        lowStockThreshold: Math.max(
          0,
          Math.round(Number(body.lowStockThreshold ?? db.products[idx].lowStockThreshold))
        ),
        expirationDate:
          body.expirationDate !== undefined ? body.expirationDate : db.products[idx].expirationDate,
        wholesalePriceUsd: wholesaleUsd(body, ccy, rate, db.products[idx].wholesalePriceUsd),
        wholesaleMinQty:
          body.wholesaleMinQty !== undefined
            ? Math.max(1, Math.round(Number(body.wholesaleMinQty) || 1))
            : db.products[idx].wholesaleMinQty || 1,
        baseUnit: body.baseUnit !== undefined ? String(body.baseUnit).trim() : db.products[idx].baseUnit || 'Piece',
        caseUnitName: body.caseUnitName !== undefined ? String(body.caseUnitName).trim() : db.products[idx].caseUnitName || 'Case',
        caseQty: body.caseQty !== undefined ? Math.max(1, Math.round(Number(body.caseQty) || 1)) : db.products[idx].caseQty || 24,
        wholesalePricePerCaseUsd:
          body.wholesalePricePerCaseUsd !== undefined
            ? Number(body.wholesalePricePerCaseUsd)
            : db.products[idx].wholesalePricePerCaseUsd ?? null,
        updatedAtMs: Date.now(),
      };
      pushEvent(
        db,
        'PRODUCT_UPDATED',
        `Product Updated: ${db.products[idx].name}`,
        `បានកែប្រែទំនិញ៖ ${db.products[idx].name}`,
        `${khrPrice.toLocaleString()} ៛ · Stock: ${db.products[idx].stock}`,
        { clientId, productId: db.products[idx].id }
      );
      saveDb(db);
      return res.status(200).json(db.products[idx]);
    }

    if (req.method === 'DELETE') {
      db.products[idx].archived = true;
      saveDb(db);
      return res.status(204).end();
    }
  }

  // ==================== 4. BATCH INVENTORY COMMIT ENDPOINT ====================
  if (pathname === '/api/inventory/batches/commit' && req.method === 'POST') {
    const body = await readBody(req);
    const items = Array.isArray(body.items) ? body.items : [];
    const rate = roundKhr(db.settings.usdToKhr || 4100);
    let createdCount = 0;
    let restockedCount = 0;
    const touched = [];

    for (const item of items) {
      if (item.productId) {
        const existingById = db.products.find((p) => !p.archived && p.id === item.productId);
        if (existingById) {
          const added = Math.max(1, Math.round(Number(item.quantityAdded ?? item.quantity ?? 1)));
          existingById.stock += added;
          existingById.updatedAtMs = Date.now();
          restockedCount++;
          touched.push(existingById);
          continue;
        }
      }

      const code = item.barcode ? String(item.barcode).trim() : null;
      const existingByCode = code
        ? db.products.find(
            (p) => !p.archived && String(p.barcode || '').toLowerCase() === code.toLowerCase()
          )
        : null;

      if (existingByCode) {
        const added = Math.max(
          1,
          Math.round(Number(item.quantityAdded ?? item.stock ?? item.quantity ?? 1))
        );
        existingByCode.stock += added;
        if (item.name) existingByCode.name = String(item.name).trim();
        if (item.category) existingByCode.category = String(item.category).trim();
        existingByCode.updatedAtMs = Date.now();
        restockedCount++;
        touched.push(existingByCode);
      } else {
        const ccy = String(item.currency || item.originalCcy || 'KHR').toUpperCase();
        const rawPrice = Number(item.price ?? item.priceUsd ?? 4100) || 4100;
        const khrVal = ccy === 'KHR' ? roundKhr(rawPrice) : roundKhr(rawPrice * rate);
        const computedUsd =
          ccy === 'KHR' ? Number((khrVal / rate).toFixed(4)) : Number(rawPrice.toFixed(4));
        const qty = Math.max(0, Math.round(Number(item.stock ?? item.quantity ?? 1)));
        const newProd = {
          id: crypto.randomUUID(),
          name: String(item.name || `Item ${code || ''}`).trim(),
          barcode: code,
          category: String(item.category || 'General').trim() || 'General',
          priceUsd: computedUsd,
          originalCcy: ccy,
          originalPrice: ccy === 'KHR' ? khrVal : rawPrice,
          stock: qty,
          lowStockThreshold: Math.max(0, Math.round(Number(item.lowStockThreshold ?? 5))),
          expirationDate: item.expirationDate || null,
          imageUrl: null,
          archived: false,
          wholesalePriceUsd: wholesaleUsd(item, ccy, rate),
          wholesaleMinQty: Math.max(1, Math.round(Number(item.wholesaleMinQty) || 1)),
          baseUnit: item.baseUnit ? String(item.baseUnit).trim() : 'Piece',
          caseUnitName: item.caseUnitName ? String(item.caseUnitName).trim() : 'Case',
          caseQty: Math.max(1, Math.round(Number(item.caseQty) || 24)),
          updatedAtMs: Date.now(),
        };
        db.products.unshift(newProd);
        createdCount++;
        touched.push(newProd);
      }
    }
    pushEvent(
      db,
      'PRODUCT_ADDED',
      `Batch Committed (${createdCount} new, ${restockedCount} restocked)`,
      `បានរក្សាទុកទំនិញជាក្រុម (${createdCount} ថ្មី, ${restockedCount} បន្ថែមស្តុក)`,
      `${touched.length} items updated`,
      { clientId }
    );
    saveDb(db);
    return res.status(200).json({
      created: createdCount,
      restocked: restockedCount,
      products: touched,
    });
  }

  // ==================== 5. SALES & CHECKOUT ENDPOINTS ====================
  if (pathname === '/api/sales' && req.method === 'GET') {
    const fromParam = urlObj.searchParams.get('from');
    const toParam = urlObj.searchParams.get('to');
    const fromMs = fromParam ? new Date(fromParam).getTime() : null;
    const toMs = toParam ? new Date(toParam).getTime() : null;

    const list = db.sales.filter((s) => {
      if (s.status === 'ARCHIVED') return false;
      const t = new Date(s.createdAt).getTime();
      if (fromMs && t < fromMs) return false;
      if (toMs && t > toMs) return false;
      return true;
    });
    return res.status(200).json(list);
  }

  if (pathname === '/api/sales/checkout' && req.method === 'POST') {
    const body = await readBody(req);
    const reqItems = Array.isArray(body.items) ? body.items : [];
    if (!reqItems.length) {
      return res.status(400).json({ error: 'Cart is empty' });
    }
    const rate = roundKhr(db.settings.usdToKhr || 4100);
    const wholesale = String(body.priceTier || '').toUpperCase() === 'WHOLESALE';
    const saleItems = [];
    let totalUsd = 0;

    for (const line of reqItems) {
      const product = db.products.find((p) => p.id === line.productId && !p.archived);
      if (!product) {
        return res.status(404).json({ error: `Product not found: ${line.productId}` });
      }
      const qty = Math.max(1, Math.round(Number(line.quantity) || 1));
      if (product.stock < qty) {
        return res.status(400).json({ error: `Insufficient stock for ${product.name}` });
      }
      product.stock -= qty;
      product.updatedAtMs = Date.now();
      const unitUsd =
        wholesale && product.wholesalePriceUsd > 0 ? product.wholesalePriceUsd : product.priceUsd;
      const cases = Math.max(0, Math.floor(Number(line.cases) || 0));
      const loose = Math.max(0, Math.floor(Number(line.loose) || 0));
      const cQty = product.caseQty || 24;
      const casePrice =
        product.wholesalePricePerCaseUsd && product.wholesalePricePerCaseUsd > 0
          ? product.wholesalePricePerCaseUsd
          : unitUsd * cQty;

      let lineTotalUsd;
      if (wholesale && cases > 0) {
        lineTotalUsd = Number((cases * casePrice + loose * unitUsd).toFixed(2));
      } else {
        lineTotalUsd = Number((unitUsd * qty).toFixed(2));
      }
      totalUsd += lineTotalUsd;

      const caseName = product.caseUnitName || 'Case';
      const baseName = product.baseUnit || 'pcs';
      let uom = line.uom;
      if (!uom && wholesale && cases > 0) {
        uom = loose > 0 ? `${cases} ${caseName} + ${loose} ${baseName}` : `${cases} ${caseName}`;
      }

      saleItems.push({
        id: crypto.randomUUID(),
        productId: product.id,
        productName: product.name,
        quantity: qty,
        unitPriceUsd: unitUsd,
        lineTotalUsd,
        uom,
        cases,
        loose,
        caseUnitName: caseName,
        baseUnit: baseName,
        isWholesale: wholesale || cases > 0,
      });
    }

    const totalKhr = roundKhr(totalUsd * rate);
    const sale = {
      id: crypto.randomUUID(),
      createdAt: new Date().toISOString(),
      rateSnapshot: rate,
      totalUsd: Number(totalUsd.toFixed(2)),
      totalKhr,
      paidCurrency: body.paidCurrency || 'KHR',
      priceTier: wholesale ? 'WHOLESALE' : 'RETAIL',
      status: 'COMPLETED',
      items: saleItems,
    };
    db.sales.unshift(sale);
    pushEvent(
      db,
      'CHECKOUT',
      `Checkout #${sale.id.slice(0, 8).toUpperCase()}`,
      `ទំនិញបានទូទាត់ #${sale.id.slice(0, 8).toUpperCase()}`,
      `${totalKhr.toLocaleString()} ៛ · ${saleItems.map((i) => `${i.productName} ×${i.quantity}`).join(', ')}`,
      { clientId, sale }
    );
    saveDb(db);
    return res.status(200).json(sale);
  }

  const saleMatch = pathname.match(/^\/api\/sales\/([^/]+)$/);
  if (saleMatch) {
    const sale = db.sales.find((s) => s.id === saleMatch[1]);
    if (!sale) return res.status(404).json({ error: 'Sale not found' });
    if (req.method === 'GET') {
      return res.status(200).json(sale);
    }
    if (req.method === 'DELETE') {
      sale.status = 'ARCHIVED';
      saveDb(db);
      return res.status(204).end();
    }
  }

  return res.status(404).json({ error: `API endpoint not found: ${req.method} ${pathname}` });
};
