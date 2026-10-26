// Vercel catch-all API route: every /api/* request is served by the backend handler.
// Using a filesystem route (instead of a vercel.json rewrite) is what prevents the
// "INFINITE_LOOP_DETECTED" rewrite loop between /api/* and /api/index.js.
const handler = require('./index.js');

module.exports = handler;
