const API = {
  base: '/api',

  async get(path) {
    const res = await fetch(this.base + path);
    if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
    return res.json();
  },

  async post(path, body) {
    const res = await fetch(this.base + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
    return res.json();
  },

  async upload(path, formData) {
    const res = await fetch(this.base + path, { method: 'POST', body: formData });
    if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
    return res.json();
  },

  // System
  status: () => API.get('/system/status'),
  init: (numKs, threshold) => API.post('/system/init', { num_ks: numKs, threshold }),
  reset: () => API.post('/system/reset', {}),

  // KS
  ksList: () => API.get('/keyservers'),
  ksPolynomials: () => API.get('/keyservers/polynomials'),
  ksToggle: (id) => API.post(`/keyservers/${id}/toggle`, {}),

  // Users
  users: () => API.get('/users'),
  registerUser: (userId, policy, validUntil) =>
    API.post('/users/register', { user_id: userId, policy, valid_until: validUntil }),
  revokeUser: (userId) => API.post(`/users/${userId}/revoke`, {}),

  // Files
  files: () => API.get('/files'),
  uploadFile: (file, userId) => {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('user_id', userId);
    return API.upload('/files/upload', fd);
  },
  auditFile: (fileId, challengeBlocks) =>
    API.post(`/files/${fileId}/audit`, { challenge_blocks: challengeBlocks }),
  tamperFile: (fileId) => API.post(`/files/${fileId}/tamper`, {}),

  // Audit Log
  auditLogs: (limit) => API.get(`/audit-log?limit=${limit}`),
  attackLogs: () => API.get('/audit-log/attacks'),
  exportCSV: () => fetch(API.base + '/audit-log/export').then(r => r.text()),
  mcpStats: () => API.get('/audit-log/mcp-stats'),

  // Blockchain
  blockchain: () => API.get('/blockchain'),

  // MCP
  mcpReadFile: (fileId, userId, challengeBlocks) =>
    API.post('/mcp/read_file', { file_id: fileId, user_id: userId, challenge_blocks: challengeBlocks }),
  mcpQueryDb: (query) =>
    API.post('/mcp/query_db', { query, user_id: 'claude_code' }),
  mcpStatus: () => API.get('/mcp/status'),
};
