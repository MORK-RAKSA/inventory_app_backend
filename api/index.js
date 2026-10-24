const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DB_FILE = path.join('/tmp', 'easysell-vercel-db.json');

function roundKhr(n) {
  const val = Number(n);
  if (!Number.isFinite(val) || val <= 0) return 0;
  return Math.max(100, Math.round(val / 100) * 100);
}

function getDefaultDb() {
  return {
    settings: {
      usdToKhr: roundKhr(Number(process.env.DEFAULT_USD_TO_KHR || 4100)),
    },
    products: [
      {
        id: '11111111-1111-4111-8111-111111111001',
        name: 'Angkor Puro Drinking Water 500ml',
        barcode: '8846000010012',
        category: 'Beverages',
        priceUsd: 0.2439,
        originalCcy: 'KHR',
        originalPrice: 1000,
        stock: 120,
        lowStockThreshold: 15,
        expirationDate: '2027-06-30',
        imageUrl: null,
        archived: false,
      },
      {
        id: '11111111-1111-4111-8111-111111111002',
        name: 'Cambodia Premium Jasmine Rice 5kg',
        barcode: '8846000020059',
        category: 'General',
        priceUsd: 5.0,
        originalCcy: 'KHR',
        originalPrice: 20500,
        stock: 35,
        lowStockThreshold: 8,
        expirationDate: '2027-12-31',
        imageUrl: null,
        archived: false,
      },
      {
        id: '11111111-1111-4111-8111-111111111003',
        name: 'Iced Espresso Canned Coffee 250ml',
        barcode: '8850228000124',
        category: 'Beverages',
        priceUsd: 0.7561,
        originalCcy: 'KHR',
        originalPrice: 3100,
        stock: 48,
        lowStockThreshold: 10,
        expirationDate: '2026-11-20',
        imageUrl: null,
        archived: false,
      },
      {
        id: '11111111-1111-4111-8111-111111111004',
        name: 'Crispy Seaweed Snack Pack',
        barcode: '8858705600121',
        category: 'Snacks',
        priceUsd: 0.5122,
        originalCcy: 'KHR',
        originalPrice: 2100,
        stock: 6,
        lowStockThreshold: 10,
        expirationDate: '2026-09-15',
        imageUrl: null,
        archived: false,
      },
    ],
    sales: [],
  };
}

function loadDb() {
  try {
    if (fs.existsSync(DB_FILE)) {
      return JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
    }
  } catch (_) {}
  const initial = getDefaultDb();
  saveDb(initial);
  return initial;
}

function saveDb(db) {
  try {
    fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2), 'utf8');
  } catch (_) {}
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
  // Support both `/api/products` and `/products` so EXPO_PUBLIC_API_URL works with or without `/api` suffix
  if (rawPath === '/' || rawPath === '/api' || rawPath === '/api/') return '/api/health';
  if (!rawPath.startsWith('/api/')) {
    return `/api${rawPath.startsWith('/') ? '' : '/'}${rawPath}`;
  }
  return rawPath;
}

module.exports = async function handler(req, res) {
  // CORS Headers for Web Portal & Mobile App
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,PATCH,DELETE,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  const urlObj = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = normalizePath(urlObj.pathname);

  // Health Check Endpoint
  if (pathname === '/api/health' && req.method === 'GET') {
    return res.status(200).json({
      status: 'UP',
      app: 'គ្រប់ហាង · EasySell Backend API',
      runtime: 'Vercel Serverless Node.js',
      timestamp: new Date().toISOString(),
    });
  }

  const db = loadDb();
  const validUser = process.env.RETAILFLOW_USERNAME || 'morkraksa';
  const validPass = process.env.RETAILFLOW_PASSWORD || 'admin1234';

  // ==================== 1. AUTH ENDPOINTS ====================
  if (pathname === '/api/auth/login' && req.method === 'POST') {
    const body = await readBody(req);
    const u = String(body.username || '').trim();
    const p = String(body.password || '');
    if (u !== validUser || p !== validPass) {
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

  // ==================== 3. PRODUCTS ENDPOINTS ====================
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
      id: crypto.randomUUID(),
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
    };
    db.products.unshift(created);
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
    return res.status(501).json({ error: 'Cloud image bucket not configured; keeping local device URI' });
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
      };
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
      // Case A: Restock existing product by productId
      if (item.productId) {
        const existingById = db.products.find((p) => !p.archived && p.id === item.productId);
        if (existingById) {
          const added = Math.max(1, Math.round(Number(item.quantityAdded ?? item.quantity ?? 1)));
          existingById.stock += added;
          restockedCount++;
          touched.push(existingById);
          continue;
        }
      }

      // Case B: Match by barcode or create new product
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
        };
        db.products.unshift(newProd);
        createdCount++;
        touched.push(newProd);
      }
    }
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
      const lineTotalUsd = Number((product.priceUsd * qty).toFixed(2));
      totalUsd += lineTotalUsd;
      saleItems.push({
        id: crypto.randomUUID(),
        productId: product.id,
        productName: product.name,
        quantity: qty,
        unitPriceUsd: product.priceUsd,
        lineTotalUsd,
      });
    }

    const sale = {
      id: crypto.randomUUID(),
      createdAt: new Date().toISOString(),
      rateSnapshot: rate,
      totalUsd: Number(totalUsd.toFixed(2)),
      totalKhr: roundKhr(totalUsd * rate),
      paidCurrency: body.paidCurrency || 'KHR',
      status: 'COMPLETED',
      items: saleItems,
    };
    db.sales.unshift(sale);
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
