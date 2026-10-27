/** Legacy Vercel entry point. Spring owns all business data and authentication. */
module.exports = async function handler(req, res) {
  const upstream = process.env.BACKEND_ORIGIN;
  if (!upstream) return res.status(503).json({ error: 'BACKEND_ORIGIN is not configured' });
  try {
    const base = new URL(upstream);
    if (!['http:', 'https:'].includes(base.protocol)) throw new Error('Invalid backend origin');
    const incoming = new URL(req.url, 'http://gateway.local');
    const target = new URL(base.origin);
    target.pathname = incoming.pathname;
    target.search = incoming.search;
    const headers = {};
    for (const key of ['authorization', 'content-type', 'accept', 'x-client-id', 'origin']) {
      if (req.headers[key]) headers[key] = req.headers[key];
    }
    let body;
    if (!['GET', 'HEAD'].includes(req.method)) {
      if (req.body !== undefined) body = Buffer.isBuffer(req.body) || typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
      else {
        const chunks = []; let size = 0;
        for await (const chunk of req) {
          size += chunk.length;
          if (size > 12 * 1024 * 1024) return res.status(413).json({ error: 'Request too large' });
          chunks.push(chunk);
        }
        body = Buffer.concat(chunks);
      }
    }
    const response = await fetch(target, { method: req.method, headers, body, redirect: 'manual', signal: AbortSignal.timeout(30000) });
    for (const key of ['content-type', 'access-control-allow-origin', 'access-control-allow-methods', 'access-control-allow-headers', 'vary']) {
      const value = response.headers.get(key);
      if (value) res.setHeader(key, value);
    }
    res.status(response.status).send(Buffer.from(await response.arrayBuffer()));
  } catch {
    res.status(502).json({ error: 'Backend unavailable' });
  }
};
