// SPA Router & Page Rendering
(function() {
  'use strict';

  const main = document.getElementById('main-content');
  const wsUrl = 'ws://' + location.host + '/ws/audit';
  let ws = null;
  let mcpConnected = false;

  // normalize backend status strings for display
  const isPassed = s => s === 'passed' || s === 'audit_passed';
  const isFailed = s => s === 'failed' || s === 'audit_failed';
  const isBlocked = s => s === 'blocked' || s === 'audit_rejected';
  const statusLabel = s => isPassed(s) ? 'Audit Passed' : isBlocked(s) ? 'AUDIT REJECTED' : 'Failed';
  const statusColor = s => isPassed(s) ? 'var(--accent2)' : isBlocked(s) ? 'var(--danger)' : 'var(--warn)';
  const entryClass = s => isBlocked(s) ? 'blocked' : (isFailed(s) ? 'failed' : 'passed');

  // ========== Router ==========
  function route() {
    const hash = location.hash.slice(1) || '/';
    const routeMap = {
      '/': renderSystem,
      '/keyservers': renderKeyServers,
      '/agents': renderAgents,
      '/files': renderFiles,
      '/audit-log': renderAuditLog,
      '/blockchain': renderBlockchain,
      '/mcp-monitor': renderMCPMonitor,
    };
    document.querySelectorAll('.nav-link').forEach(a => {
      a.classList.toggle('active', a.dataset.route === hash);
    });
    const renderer = routeMap[hash] || renderSystem;
    renderer();
  }

  window.addEventListener('hashchange', route);
  window.addEventListener('load', () => { route(); connectWS(); updateClock(); });
  setInterval(updateClock, 1000);

  function updateClock() {
    document.getElementById('clock').textContent = new Date().toLocaleString('zh-CN');
  }

  // ========== WebSocket ==========
  function connectWS() {
    try {
      ws = new WebSocket(wsUrl);
      ws.onopen = () => {
        mcpConnected = true;
        const badge = document.getElementById('mcp-badge');
        badge.textContent = 'MCP: Connected';
        badge.className = 'mcp-online';
      };
      ws.onclose = () => {
        mcpConnected = false;
        document.getElementById('mcp-badge').textContent = 'MCP: Disconnected';
        document.getElementById('mcp-badge').className = 'mcp-offline';
        setTimeout(connectWS, 3000);
      };
      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === 'attack_alert') {
            showAttackAlert(data);
          }
          if (data.type === 'mcp_call' && location.hash === '#/mcp-monitor') {
            appendMCPEntry(data);
          }
        } catch(e) {}
      };
    } catch(e) {}
  }

  function showAttackAlert(data) {
    const alert = document.getElementById('attack-alert');
    alert.classList.remove('hidden');
    alert.textContent = 'Attack Blocked! ' + data.attack_type;
    toast(data.detail || data.attack_type + ' was intercepted', 'error');
    setTimeout(() => alert.classList.add('hidden'), 5000);
  }

  // ========== Toast ==========
  window.toast = function(msg, type) {
    type = type || 'success';
    const container = document.getElementById('toast-container');
    const el = document.createElement('div');
    el.className = 'toast ' + type;
    el.textContent = msg;
    container.appendChild(el);
    setTimeout(() => el.remove(), 4000);
  };

  // ========== Page: System ==========
  async function renderSystem() {
    let status;
    try { status = await API.status(); } catch(e) { main.innerHTML = '<div class="empty-state"><div class="icon">Connection Error</div><p>Backend not reachable. Ensure the server is running.</p></div>'; return; }

    const initSection = status.initialized ? '' : `
      <div class="panel">
        <h2>Initialize System</h2>
        <div class="form-row">
          <div class="form-group">
            <label>Number of Key Servers</label>
            <input type="number" id="init-ks" value="10" min="3" max="100">
          </div>
          <div class="form-group">
            <label>Threshold (t)</label>
            <input type="number" id="init-threshold" value="5" min="1" max="10">
          </div>
          <button class="btn btn-primary" onclick="doInit()">Initialize (DKG)</button>
        </div>
        <p style="color:var(--text2);font-size:0.8rem;margin-top:8px">
          Initializes decentralized key generation: each KS generates Shamir polynomials,
          shares are verified against commitments, private shares distributed securely.
          Threshold ${document.getElementById('init-threshold')?.value || 5}/${document.getElementById('init-ks')?.value || 10} fault-tolerant.
        </p>
      </div>`;

    main.innerHTML = `
      <h1 class="section-title">System Overview</h1>
      <div class="stats-grid">
        <div class="stat-card ${status.initialized ? 'success' : 'warn'}">
          <div class="value">${status.initialized ? 'Ready' : 'Not Init'}</div>
          <div class="label">System Status</div>
        </div>
        <div class="stat-card accent">
          <div class="value">${status.online_ks || 0}/${status.num_ks || 0}</div>
          <div class="label">KS Online / Total</div>
        </div>
        <div class="stat-card">
          <div class="value">${status.num_users || 0}</div>
          <div class="label">Registered Agents</div>
        </div>
        <div class="stat-card">
          <div class="value">${status.num_files || 0}</div>
          <div class="label">Protected Files</div>
        </div>
        <div class="stat-card success">
          <div class="value">${status.audits_passed || 0}</div>
          <div class="label">Audits Passed</div>
        </div>
        <div class="stat-card danger">
          <div class="value">${status.audits_failed || 0}</div>
          <div class="label">Audits Failed</div>
        </div>
      </div>
      ${initSection}
      <div class="panel">
        <h2>Quick Actions</h2>
        <div class="btn-group">
          <button class="btn btn-primary" onclick="location.hash='/agents'">Register Agent</button>
          <button class="btn btn-success" onclick="location.hash='/files'">Upload File</button>
          <button class="btn btn-warn" onclick="location.hash='/mcp-monitor'">View MCP Monitor</button>
          ${status.initialized ? '<button class="btn btn-danger" onclick="doReset()">Reset System</button>' : ''}
        </div>
      </div>
    `;
  }

  window.doInit = async function() {
    const numKs = parseInt(document.getElementById('init-ks').value) || 10;
    const threshold = parseInt(document.getElementById('init-threshold').value) || 5;
    try {
      const result = await API.init(numKs, threshold);
      toast('DKG Initialized: ' + result.status + ' with ' + numKs + ' KS, threshold=' + threshold);
      route();
    } catch(e) { toast('Init failed: ' + e.message, 'error'); }
  };

  window.doReset = async function() {
    if (!confirm('Reset will clear ALL data: agents, files, key servers, audit logs. Continue?')) return;
    try {
      await API.reset();
      toast('System has been reset. You can now re-initialize.');
      route();
    } catch(e) { toast('Reset failed: ' + e.message, 'error'); }
  };

  // ========== Page: Key Servers ==========
  async function renderKeyServers() {
    const status = await API.status();
    if (!status.initialized) { main.innerHTML = '<div class="empty-state"><div class="icon">Not Initialized</div><p>Go to <a href="#/">System page</a> to initialize first.</p></div>'; return; }

    const ksList = await API.ksList();
    const polys = await API.ksPolynomials();

    let rows = ksList.map(ks => `
      <tr>
        <td>KS-${ks.id}</td>
        <td><code>${ks.pubkey_hash}&hellip;</code></td>
        <td><span class="tag ${ks.status}">${ks.status}</span></td>
        <td><button class="btn btn-sm ${ks.status === 'online' ? 'btn-danger' : 'btn-success'}" onclick="toggleKs(${ks.id})">
          ${ks.status === 'online' ? 'Take Offline' : 'Bring Online'}
        </button></td>
      </tr>
    `).join('');

    let polyRows = polys.map(p => `
      <tr>
        <td>KS-${p.ks_id}</td>
        <td><code>f(x) = ${p.coefficients.join(' + ')}x&hellip;</code></td>
      </tr>
    `).join('');

    main.innerHTML = `
      <h1 class="section-title">Key Server Management</h1>
      <div class="stats-grid">
        <div class="stat-card success">
          <div class="value">${ksList.filter(k=>k.status==='online').length}/${ksList.length}</div>
          <div class="label">Online / Total</div>
        </div>
        <div class="stat-card accent">
          <div class="value">${status.threshold}/${ksList.length}</div>
          <div class="label">Threshold / Total</div>
        </div>
      </div>
      <div class="panel">
        <h2>Key Server Nodes <span class="badge online">Simulate downtime to test threshold fault tolerance</span></h2>
        <table><thead><tr><th>ID</th><th>Public Key Hash</th><th>Status</th><th>Action</th></tr></thead>
        <tbody>${rows}</tbody></table>
      </div>
      <div class="panel">
        <h2>DKG Polynomial Coefficients</h2>
        <table><thead><tr><th>KS</th><th>Polynomial</th></tr></thead>
        <tbody>${polyRows}</tbody></table>
      </div>
    `;
  }

  window.toggleKs = async function(id) {
    await API.ksToggle(id);
    toast('KS-' + id + ' toggled');
    renderKeyServers();
  };

  // ========== Page: Agents ==========
  async function renderAgents() {
    const status = await API.status();
    if (!status.initialized) { main.innerHTML = '<div class="empty-state"><div class="icon">Not Initialized</div><p>Go to <a href="#/">System page</a> to initialize first.</p></div>'; return; }

    const users = await API.users();
    let rows = users.map(u => `
      <tr>
        <td><strong>${u.id}</strong></td>
        <td><code>${u.pubkey_hash}&hellip;</code></td>
        <td>${u.policy}</td>
        <td>${u.valid_until}</td>
        <td><span class="tag ${u.status}">${u.status}</span></td>
        <td>
          ${u.status === 'active' ? `<button class="btn btn-sm btn-danger" onclick="revokeAgent('${u.id}')">Revoke</button>` : ''}
        </td>
      </tr>
    `).join('');

    main.innerHTML = `
      <h1 class="section-title">Agent Management</h1>
      <div class="panel">
        <h2>Register New Agent</h2>
        <div class="form-row">
          <div class="form-group"><label>Agent ID</label><input type="text" id="reg-user" placeholder="e.g. claude_code"></div>
          <div class="form-group"><label>Access Policy</label>
            <select id="reg-policy">
              <option value="top_secret">Top Secret</option>
              <option value="confidential" selected>Confidential</option>
              <option value="secret">Secret</option>
              <option value="public">Public</option>
            </select>
          </div>
          <div class="form-group"><label>Valid Until</label><input type="date" id="reg-until" value="2026-12-31"></div>
          <button class="btn btn-primary" onclick="doRegister()">Register & Keygen</button>
        </div>
      </div>
      <div class="panel">
        <h2>Registered Agents</h2>
        <table><thead><tr><th>Agent ID</th><th>Public Key</th><th>Policy</th><th>Valid Until</th><th>Status</th><th>Action</th></tr></thead>
        <tbody>${rows || '<tr><td colspan="6">No agents registered yet.</td></tr>'}</tbody></table>
      </div>
    `;
  }

  window.doRegister = async function() {
    const userId = document.getElementById('reg-user').value.trim();
    const policy = document.getElementById('reg-policy').value;
    const validUntil = document.getElementById('reg-until').value;
    if (!userId) return toast('Please enter an agent ID', 'error');
    try {
      const result = await API.registerUser(userId, policy, validUntil);
      toast('Agent registered: ' + userId + ' | Policy: ' + policy + ' | Until: ' + validUntil);
      renderAgents();
    } catch(e) { toast('Registration failed: ' + e.message, 'error'); }
  };

  window.revokeAgent = async function(userId) {
    if (!confirm('Revoke agent ' + userId + '? They will be added to denySet immediately.')) return;
    await API.revokeUser(userId);
    toast('Agent ' + userId + ' has been revoked and added to denySet');
    renderAgents();
  };

  // ========== Page: Files ==========
  async function renderFiles() {
    const status = await API.status();
    if (!status.initialized) { main.innerHTML = '<div class="empty-state"><div class="icon">Not Initialized</div><p>Go to <a href="#/">System page</a> to initialize first.</p></div>'; return; }

    const files = await API.files();
    const users = await API.users();
    const userOpts = users.filter(u => u.status === 'active').map(u =>
      `<option value="${u.id}">${u.id} (${u.policy})</option>`
    ).join('');

    let rows = files.map(f => `
      <tr>
        <td><strong>${f.name}</strong></td>
        <td>${f.owner}</td>
        <td>${f.num_blocks} blocks</td>
        <td><code>${f.tag_hash}&hellip;</code></td>
        <td><span class="tag ${f.last_audit === 'passed' ? 'passed' : (f.last_audit === 'failed' ? 'failed' : '')}">${f.last_audit}</span></td>
        <td>
          <div class="btn-group">
            <button class="btn btn-sm btn-primary" onclick="doAudit('${f.id}')">Audit</button>
            ${!f.tampered ? `<button class="btn btn-sm btn-danger" onclick="doTamper('${f.id}')">Inject Fault</button>` : '<span class="tag failed">Tampered</span>'}
          </div>
        </td>
      </tr>
    `).join('');

    main.innerHTML = `
      <h1 class="section-title">File Management</h1>
      <div class="panel">
        <h2>Upload File</h2>
        <div class="form-row">
          <div class="form-group"><label>Select File</label><input type="file" id="upload-file"></div>
          <div class="form-group"><label>Associate Agent</label><select id="upload-user">${userOpts || '<option>No active agents</option>'}</select></div>
          <button class="btn btn-primary" onclick="doUpload()">Upload & Sign</button>
        </div>
        <p style="color:var(--text2);font-size:0.8rem;margin-top:8px">
          File is split into 64KB blocks, each block gets an authentication tag bound to metadata policy.
          Tags are stored on-chain (blockchain). Estimated ~770 bytes on-chain per file.
        </p>
      </div>
      <div class="panel">
        <h2>Protected Files</h2>
        <table><thead><tr><th>File</th><th>Owner</th><th>Blocks</th><th>Tag Hash</th><th>Last Audit</th><th>Actions</th></tr></thead>
        <tbody>${rows || '<tr><td colspan="6">No files uploaded yet.</td></tr>'}</tbody></table>
      </div>
    `;
  }

  window.doUpload = async function() {
    const fileInput = document.getElementById('upload-file');
    const userId = document.getElementById('upload-user').value;
    if (!fileInput.files[0]) return toast('Please select a file', 'error');
    if (!userId) return toast('Please select an agent', 'error');
    try {
      const result = await API.uploadFile(fileInput.files[0], userId);
      toast('File signed: ' + result.status + ' | Blocks: ' + result.num_blocks + ' | Tag: ' + result.tag_hash);
      renderFiles();
    } catch(e) { toast('Upload failed: ' + e.message, 'error'); }
  };

  window.doAudit = async function(fileId) {
    toast('Running audit on ' + fileId + '...');
    try {
      const result = await API.auditFile(fileId, 100);
      const msg = result.status === 'audit_passed'
        ? 'Audit PASSED: ' + fileId + ' (' + result.latency_ms + 'ms)'
        : 'Audit REJECTED: ' + fileId + ' - ' + (result.reason || 'integrity failure');
      toast(msg, result.status === 'audit_passed' ? 'success' : 'error');
      renderFiles();
    } catch(e) { toast('Audit error: ' + e.message, 'error'); }
  };

  window.doTamper = async function(fileId) {
    if (!confirm('Simulate tampering on ' + fileId + '? One block will be modified. Next audit should detect this.')) return;
    await API.tamperFile(fileId);
    toast('Fault injected in ' + fileId + '. Run Audit to verify detection.');
    renderFiles();
  };

  // ========== Page: Audit Log ==========
  async function renderAuditLog() {
    const logs = await API.auditLogs(50);
    const attacks = await API.attackLogs();
    const stats = await API.mcpStats();

    let logRows = logs.map(l => `
      <tr>
        <td>${l.createdAt}</td>
        <td><span class="tag ${l.eventType}">${l.eventType}</span></td>
        <td>${l.user || '-'}</td>
        <td>${l.file || '-'}</td>
        <td>${l.tool || '-'}</td>
        <td>${l.challengedBlocks}</td>
        <td class="audit-${l.status}"><strong>${l.status}</strong></td>
        <td>${l.latencyMs}ms</td>
      </tr>
    `).join('');

    let attackRows = attacks.map(l => `
      <tr style="background:rgba(244,33,46,0.08)">
        <td>${l.createdAt}</td>
        <td><span class="tag blocked">BLOCKED</span></td>
        <td>${l.user}</td>
        <td>${l.file}</td>
        <td>${l.reason}</td>
        <td class="audit-blocked"><strong>INTERCEPTED</strong></td>
        <td>${l.detail || ''}</td>
      </tr>
    `).join('');

    main.innerHTML = `
      <h1 class="section-title">Audit Log</h1>
      <div class="stats-grid">
        <div class="stat-card"><div class="value">${stats.total_calls}</div><div class="label">Total Calls</div></div>
        <div class="stat-card success"><div class="value">${stats.total_passed}</div><div class="label">Passed</div></div>
        <div class="stat-card danger"><div class="value">${stats.total_rejected}</div><div class="label">Rejected</div></div>
        <div class="stat-card accent"><div class="value">${stats.avg_latency_ms}ms</div><div class="label">Avg Latency</div></div>
      </div>
      <div class="btn-group" style="margin-bottom:16px">
        <button class="btn btn-primary" onclick="doExportCSV()">Export CSV Report</button>
      </div>
      ${attacks.length > 0 ? `
      <div class="panel">
        <h2>Blocked Attacks <span class="badge offline">${attacks.length} attacks intercepted</span></h2>
        <table><thead><tr><th>Time</th><th>Type</th><th>Agent</th><th>File</th><th>Attack</th><th>Status</th><th>Detail</th></tr></thead>
        <tbody>${attackRows}</tbody></table>
      </div>` : ''}
      <div class="panel">
        <h2>Complete Audit Trail</h2>
        <table><thead><tr><th>Time</th><th>Type</th><th>Agent</th><th>File</th><th>Tool</th><th>Blocks</th><th>Status</th><th>Latency</th></tr></thead>
        <tbody>${logRows || '<tr><td colspan="8">No audit records yet. Make some MCP calls to see activity.</td></tr>'}</tbody></table>
      </div>
    `;
  }

  window.doExportCSV = async function() {
    const csv = await API.exportCSV();
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = 'secuaudit_log.csv'; a.click();
    URL.revokeObjectURL(url);
    toast('CSV report downloaded');
  };

  // ========== Page: Blockchain ==========
  async function renderBlockchain() {
    try {
      const state = await API.blockchain();
      let tagRows = (state.file_tags || []).map(t => `
        <tr><td>${t.file_id}</td><td><code class="code-block">${t.tag}</code></td><td>${t.metadata}</td></tr>
      `).join('');

      let rhRows = (state.policy_rh || []).map(r => `
        <tr><td>${r.user_id}</td><td>${r.policy}</td><td>${r.valid_until}</td></tr>
      `).join('');

      let denyItems = (state.deny_set || []).map(d => `<span class="tag revoked">${d}</span>`).join(' ') || 'None';

      main.innerHTML = `
        <h1 class="section-title">Blockchain State</h1>
        <div class="panel">
          <h2>On-Chain File Tags</h2>
          <table><thead><tr><th>File ID</th><th>Tag (agentID|A|filename|Z|hash)</th><th>Metadata</th></tr></thead>
          <tbody>${tagRows || '<tr><td colspan="3">No file tags on chain.</td></tr>'}</tbody></table>
        </div>
        <div class="panel">
          <h2>Policy Registry (RH values)</h2>
          <table><thead><tr><th>Agent</th><th>Policy</th><th>Valid Until</th></tr></thead>
          <tbody>${rhRows || '<tr><td colspan="3">No policies registered.</td></tr>'}</tbody></table>
        </div>
        <div class="panel">
          <h2>Denial Set (Revoked Agents)</h2>
          <p style="margin-top:8px">${denyItems}</p>
        </div>
      `;
    } catch(e) {
      main.innerHTML = '<div class="empty-state"><div class="icon">Blockchain</div><p>Initialize the system first.</p></div>';
    }
  };

  // ========== Page: MCP Monitor ==========
  async function renderMCPMonitor() {
    const stats = await API.mcpStats();
    const logs = await API.auditLogs(20);

    let entries = logs.map(l => `
      <div class="mcp-entry ${entryClass(l.status)}">
        <div class="time">${l.createdAt}</div>
        <div><span class="tool">${l.tool || 'read_file'}("${l.file || ''}")</span></div>
        <div class="steps">
          TPA: challenged ${l.challengedBlocks} blocks |
          Pairing: e(T,g)==e(Z,C) ${isPassed(l.status) ? '✓' : '✗'} |
          Policy check: ${l.reason && l.reason.includes('policy') ? '✗' : '✓'} |
          Revocation check: ${!isBlocked(l.status) ? '✓' : '✗'}
        </div>
        <div class="result" style="color:${statusColor(l.status)}">
          Result: ${statusLabel(l.status)} (${l.latencyMs}ms)
        </div>
      </div>
    `).join('');

    main.innerHTML = `
      <h1 class="section-title">MCP Call Monitor</h1>
      <div class="panel">
        <h2>
          MCP Server Status
          <span class="badge ${mcpConnected ? 'online' : 'offline'}">${mcpConnected ? 'Connected: Claude Code' : 'Disconnected'}</span>
        </h2>
        <p style="color:var(--text2);font-size:0.85rem">Started: ${stats.started_at} | Cumulative Calls: ${stats.total_calls}</p>
      </div>
      <div class="stats-grid">
        <div class="stat-card"><div class="value">${stats.total_calls}</div><div class="label">Total Calls</div></div>
        <div class="stat-card success"><div class="value">${stats.total_passed}</div><div class="label">Passed</div></div>
        <div class="stat-card danger"><div class="value">${stats.total_rejected}</div><div class="label">Rejected</div></div>
        <div class="stat-card accent"><div class="value">${stats.avg_latency_ms}ms</div><div class="label">Avg Latency</div></div>
      </div>
      <div class="panel">
        <h2>Live Call Stream (WebSocket Push)</h2>
        <div class="mcp-stream" id="mcp-stream">
          ${entries || '<p style="color:var(--text2);padding:20px;text-align:center">No calls yet. Configure Claude Code with mcp.json to start streaming.</p>'}
        </div>
      </div>
    `;

    // scroll to bottom
    setTimeout(() => {
      const stream = document.getElementById('mcp-stream');
      if (stream) stream.scrollTop = stream.scrollHeight;
    }, 100);
  }

  function appendMCPEntry(data) {
    const stream = document.getElementById('mcp-stream');
    if (!stream) return;
    const div = document.createElement('div');
    div.className = 'mcp-entry ' + entryClass(data.status);
    div.innerHTML = `
      <div class="time">${data.timestamp}</div>
      <div><span class="tool">${data.tool || 'read_file'}("${data.file}")</span></div>
      <div class="steps">
        TPA: challenged ${data.challenged_blocks} blocks |
        Pairing: e(T,g)==e(Z,C) ${isPassed(data.status) ? '✓' : '✗'} |
        ${data.reason ? `Reason: ${data.reason}` : 'All checks passed ✓'}
      </div>
      <div class="result" style="color:${statusColor(data.status)}">
        Result: ${statusLabel(data.status)} (${data.latency_ms}ms)
      </div>
    `;
    stream.insertBefore(div, stream.firstChild);
  }

})();
