import { get, post, esc, money, num, getSession, initNav, showMsg } from './api.js';

initNav();
const $ = (id) => document.getElementById(id);
const FALLBACK_BADGE = '<span class="badge badge-neutral" title="No LLM key is configured, so this was produced by deterministic rules.">rule-based fallback &mdash; no model key configured</span>';
const aiBadge = (r) => (r && r.aiGenerated === false ? FALLBACK_BADGE : '');

// ---- theme: read CSS variables so charts stay legible in light and dark mode ----
function theme() {
  const cs = getComputedStyle(document.documentElement);
  const v = (n) => cs.getPropertyValue(n).trim();
  return {
    text: v('--text'), muted: v('--text-muted'), grid: v('--border'), surface: v('--surface'),
    accent: v('--accent'), up: v('--up'), down: v('--down'), warn: v('--warn'),
    palette: [1, 2, 3, 4, 5, 6, 7, 8].map((i) => v(`--c${i}`)),
  };
}
function applyChartDefaults() {
  const t = theme();
  Chart.defaults.color = t.muted;
  Chart.defaults.borderColor = t.grid;
  Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
  return t;
}

const charts = {};
function mount(id, config) {
  if (charts[id]) charts[id].destroy();
  charts[id] = new Chart($(id), config);
  return charts[id];
}

const cache = {};   // last data, used to repaint charts when the colour scheme changes

// ---- gate ----
const session = getSession();
if (!session || session.role !== 'MERCHANT') {
  const gate = $('gate');
  gate.hidden = false;
  gate.innerHTML = !session
    ? '<h1>Merchant dashboard</h1><p>Please <a href="/login">log in</a> as a merchant to see this page.</p>'
    : '<h1>Merchant dashboard</h1><p>This page is for merchant accounts. <a href="/login">Log in with a different account</a>.</p>';
} else {
  start();
}

function iso(d) { return d.toISOString().slice(0, 10); }

function start() {
  $('dash').hidden = false;
  const to = new Date();
  const from = new Date(); from.setDate(to.getDate() - 29);
  $('from').value = iso(from);
  $('to').value = iso(to);
  $('range-form').addEventListener('submit', (e) => { e.preventDefault(); loadAll(); });
  $('brief-regen').addEventListener('click', regenerateBriefing);
  $('ask-form').addEventListener('submit', (e) => { e.preventDefault(); ask($('ask-q').value.trim()); });
  $('examples').addEventListener('click', (e) => {
    const b = e.target.closest('[data-q]');
    if (b) { $('ask-q').value = b.dataset.q; ask(b.dataset.q); }
  });
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', repaint);
  loadAll();
}

function rangeQuery() {
  const from = $('from').value, to = $('to').value;
  return `from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
}

function loadAll() {
  const from = $('from').value, to = $('to').value;
  if (!from || !to || from > to) { showMsg($('revenue-msg'), 'Choose a valid date range (from must not be after to).'); return; }
  showMsg($('revenue-msg'), '');
  $('range-label').textContent = `Showing ${from} to ${to}, compared with the previous period of equal length.`;
  $('kpis').setAttribute('aria-busy', 'true');
  loadKpis();
  loadRevenue();
  loadCategories();
  loadTop();
  loadLowStock();
  loadBriefing();
  $('explain-body').textContent = 'Select an anomaly on the revenue chart to see why it happened.';
}

// ---- KPIs ----
function fmtKpi(m) {
  const v = Number(m.value);
  if (m.unit === 'currency') return money(v);
  if (m.unit === 'percent') return `${v.toFixed(1)}%`;
  return num(Math.round(v));
}

async function loadKpis() {
  const box = $('kpis');
  try {
    const k = await get(`/api/v1/merchant/analytics/kpis?${rangeQuery()}`);
    box.innerHTML = [k.revenue, k.orders, k.averageOrderValue, k.repeatCustomerRate].map((m) => {
      const ch = m.changePercent == null ? null : Number(m.changePercent);
      let delta;
      if (ch == null || isNaN(ch)) delta = '<span class="delta flat">No prior data</span>';
      else {
        const dir = ch > 0 ? 'up' : ch < 0 ? 'down' : 'flat';
        const arrow = ch > 0 ? '&#9650;' : ch < 0 ? '&#9660;' : '&#9644;';
        delta = `<span class="delta ${dir}"><span aria-hidden="true">${arrow}</span> ${Math.abs(ch).toFixed(1)}%<span class="sr-only"> ${dir === 'flat' ? 'unchanged' : dir === 'up' ? 'increase' : 'decrease'}</span> <span class="muted">vs previous</span></span>`;
      }
      return `<div class="kpi"><div class="kpi-label">${esc(m.label)}</div><div class="kpi-value">${esc(fmtKpi(m))}</div>${delta}</div>`;
    }).join('');
  } catch (e) {
    box.innerHTML = `<div class="alert alert-error kpi-error" role="alert">Could not load KPIs: ${esc(e.message)}</div>`;
  } finally {
    box.setAttribute('aria-busy', 'false');
  }
}

// ---- revenue line + anomalies ----
async function loadRevenue() {
  try {
    const [series, anomalies] = await Promise.all([
      get(`/api/v1/merchant/analytics/series?${rangeQuery()}`),
      get(`/api/v1/merchant/analytics/anomalies?${rangeQuery()}`).catch((e) => { showMsg($('revenue-msg'), `Anomaly detection unavailable: ${e.message}`); return []; }),
    ]);
    cache.series = series; cache.anomalies = anomalies;
    drawRevenue();
    renderAnomalyChips();
  } catch (e) {
    showMsg($('revenue-msg'), `Could not load revenue: ${e.message}`);
  }
}

function drawRevenue() {
  if (!cache.series) return;
  const t = applyChartDefaults();
  const flagged = new Map((cache.anomalies || []).map((a) => [String(a.date), a]));
  const labels = cache.series.map((p) => p.date);
  const colorOf = (d) => { const a = flagged.get(d); return a ? (a.direction === 'DROP' ? t.down : t.warn) : t.accent; };
  mount('chart-revenue', {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'Revenue', data: cache.series.map((p) => Number(p.revenue)),
        borderColor: t.accent, backgroundColor: `${t.accent}22`, fill: true, tension: 0.25, borderWidth: 2,
        pointRadius: labels.map((d) => (flagged.has(d) ? 8 : 2)),
        pointHoverRadius: labels.map((d) => (flagged.has(d) ? 10 : 4)),
        pointBackgroundColor: labels.map(colorOf), pointBorderColor: labels.map((d) => (flagged.has(d) ? t.surface : t.accent)),
        pointBorderWidth: labels.map((d) => (flagged.has(d) ? 2 : 1)),
      }],
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      interaction: { mode: 'nearest', intersect: false, axis: 'x' },
      scales: {
        x: { grid: { display: false }, ticks: { maxTicksLimit: 8, maxRotation: 0 } },
        y: { beginAtZero: true, ticks: { callback: (v) => money(v).replace('.00', '') } },
      },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: (c) => `Revenue: ${money(c.parsed.y)}`,
            afterLabel: (c) => { const a = flagged.get(labels[c.dataIndex]); return a ? `Anomaly: ${a.direction} (${a.severity}), z = ${Number(a.zScore).toFixed(2)} - click to explain` : ''; },
          },
        },
      },
      onHover: (evt, els, chart) => {
        const hit = chart.getElementsAtEventForMode(evt, 'nearest', { intersect: true }, true)[0];
        evt.native.target.style.cursor = hit && flagged.has(labels[hit.index]) ? 'pointer' : 'default';
      },
      onClick: (evt, els, chart) => {
        const hit = chart.getElementsAtEventForMode(evt, 'nearest', { intersect: true }, true)[0];
        if (hit && flagged.has(labels[hit.index])) explain(labels[hit.index]);
      },
    },
  });
}

function renderAnomalyChips() {
  const box = $('anomaly-list');
  const list = cache.anomalies || [];
  if (!list.length) { box.innerHTML = '<span class="muted small">No anomalies detected in this range.</span>'; return; }
  box.innerHTML = '<span class="muted small">Anomalies:</span>' + list.map((a) =>
    `<button type="button" class="chip chip-${a.direction === 'DROP' ? 'down' : 'warn'}" data-date="${esc(a.date)}">${esc(a.date)} &middot; ${esc(a.direction)} (${esc(a.severity)})</button>`).join('');
  box.onclick = (e) => { const b = e.target.closest('[data-date]'); if (b) explain(b.dataset.date); };
}

async function explain(date) {
  const body = $('explain-body');
  body.innerHTML = `<span class="muted">Explaining ${esc(date)}...</span>`;
  $('explain-panel').scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  try {
    const r = await post('/api/v1/merchant/ai/anomalies/explain', { date });
    const a = (cache.anomalies || []).find((x) => String(x.date) === String(date));
    body.innerHTML = `<p class="explain-date"><strong>${esc(r.date || date)}</strong>${a ? ` <span class="badge badge-${a.direction === 'DROP' ? 'bad' : 'warn'}">${esc(a.direction)}</span>` : ''}</p>
      <p>${esc(r.explanation)}</p>
      ${(r.supportingFacts || []).length ? `<h3 class="sub">Supporting facts</h3><ul class="facts">${r.supportingFacts.map((f) => `<li>${esc(f)}</li>`).join('')}</ul>` : ''}
      ${aiBadge(r)}`;
  } catch (e) {
    body.innerHTML = `<div class="alert alert-error" role="alert">Could not explain ${esc(date)}: ${esc(e.message)}</div>`;
  }
}

// ---- category doughnut + top products ----
async function loadCategories() {
  try {
    cache.cats = await get(`/api/v1/merchant/analytics/by-category?${rangeQuery()}`);
    showMsg($('category-msg'), '');
    drawCategories();
  } catch (e) { showMsg($('category-msg'), `Could not load categories: ${e.message}`); }
}
function drawCategories() {
  if (!cache.cats) return;
  const t = applyChartDefaults();
  mount('chart-category', {
    type: 'doughnut',
    data: {
      labels: cache.cats.map((c) => c.categoryName),
      datasets: [{ data: cache.cats.map((c) => Number(c.revenue)), backgroundColor: cache.cats.map((_, i) => t.palette[i % t.palette.length]), borderColor: t.surface, borderWidth: 2 }],
    },
    options: {
      responsive: true, maintainAspectRatio: false, cutout: '62%',
      plugins: {
        legend: { position: 'right', labels: { color: t.text, boxWidth: 12 } },
        tooltip: { callbacks: { label: (c) => { const s = cache.cats[c.dataIndex]; return `${c.label}: ${money(c.parsed)} (${Number(s.sharePercent).toFixed(1)}%)`; } } },
      },
    },
  });
}

async function loadTop() {
  try {
    cache.top = await get(`/api/v1/merchant/analytics/top-products?limit=10&${rangeQuery()}`);
    showMsg($('top-msg'), '');
    drawTop();
  } catch (e) { showMsg($('top-msg'), `Could not load top products: ${e.message}`); }
}
function drawTop() {
  if (!cache.top) return;
  const t = applyChartDefaults();
  mount('chart-top', {
    type: 'bar',
    data: {
      labels: cache.top.map((p) => p.productName),
      datasets: [{ label: 'Revenue', data: cache.top.map((p) => Number(p.revenue)), backgroundColor: t.accent, borderRadius: 3 }],
    },
    options: {
      indexAxis: 'y', responsive: true, maintainAspectRatio: false,
      scales: { x: { beginAtZero: true, ticks: { callback: (v) => money(v).replace('.00', '') } }, y: { grid: { display: false } } },
      plugins: { legend: { display: false }, tooltip: { callbacks: { label: (c) => `${money(c.parsed.x)} - ${cache.top[c.dataIndex].unitsSold} units` } } },
    },
  });
}

function repaint() { drawRevenue(); drawCategories(); drawTop(); if (cache.ask) drawAskChart(); }

// ---- low stock ----
async function loadLowStock() {
  const box = $('low-stock');
  try {
    const items = await get('/api/v1/merchant/analytics/low-stock?threshold=10');
    box.innerHTML = items.length ? `<div class="table-wrap"><table class="table">
      <thead><tr><th scope="col">Product</th><th scope="col">Category</th><th scope="col" class="num">Stock</th><th scope="col" class="num">Price</th></tr></thead>
      <tbody>${items.map((i) => `<tr><th scope="row">${esc(i.productName)}</th><td>${esc(i.categoryName)}</td>
        <td class="num"><span class="badge badge-${i.stock <= 3 ? 'bad' : 'warn'}">${esc(i.stock)}</span></td><td class="num">${money(i.price)}</td></tr>`).join('')}</tbody></table></div>`
      : '<p class="muted">Nothing is running low. Every product has more than 10 units.</p>';
  } catch (e) {
    box.innerHTML = `<div class="alert alert-error" role="alert">Could not load low-stock items: ${esc(e.message)}</div>`;
  }
}

// ---- briefing ----
function md(text) {
  const lines = esc(text).split(/\r?\n/);
  let html = '', inList = false;
  const inline = (s) => s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  for (const raw of lines) {
    const line = raw.trim();
    const li = line.match(/^[-*]\s+(.*)/);
    if (li) { if (!inList) { html += '<ul>'; inList = true; } html += `<li>${inline(li[1])}</li>`; continue; }
    if (inList) { html += '</ul>'; inList = false; }
    const h = line.match(/^#{1,6}\s+(.*)/);
    if (h) html += `<h3 class="sub">${inline(h[1])}</h3>`;
    else if (line) html += `<p>${inline(line)}</p>`;
  }
  return html + (inList ? '</ul>' : '');
}

function renderBriefing(b) {
  $('briefing').innerHTML = `<div class="briefing-meta muted small">Week of ${esc(b.weekStart)} &middot; generated ${esc(new Date(b.createdAt).toLocaleString())} ${aiBadge(b)}</div>
    <div class="briefing-body">${md(b.content)}</div>`;
}

async function loadBriefing() {
  const box = $('briefing');
  box.setAttribute('aria-busy', 'true');
  try {
    renderBriefing(await get('/api/v1/merchant/ai/briefing/latest'));
  } catch (e) {
    if (e.status === 404) {
      box.innerHTML = '<p class="muted">No briefing has been generated yet.</p><button type="button" class="btn btn-primary" id="brief-first">Generate one now</button>';
      $('brief-first').addEventListener('click', regenerateBriefing);
    } else {
      box.innerHTML = `<div class="alert alert-error" role="alert">Could not load the briefing: ${esc(e.message)}</div>`;
    }
  } finally { box.setAttribute('aria-busy', 'false'); }
}

async function regenerateBriefing() {
  const btn = $('brief-regen');
  btn.disabled = true; btn.textContent = 'Generating...';
  $('briefing').setAttribute('aria-busy', 'true');
  try {
    renderBriefing(await post('/api/v1/merchant/ai/briefing/regenerate'));
  } catch (e) {
    $('briefing').innerHTML = `<div class="alert alert-error" role="alert">Could not generate the briefing: ${esc(e.message)}</div>`;
  } finally {
    btn.disabled = false; btn.textContent = 'Regenerate';
    $('briefing').setAttribute('aria-busy', 'false');
  }
}

// ---- Ask your store ----
async function ask(question) {
  if (!question) { $('ask-q').focus(); return; }
  const out = $('ask-result');
  const btn = $('ask-btn');
  btn.disabled = true; out.setAttribute('aria-busy', 'true');
  out.innerHTML = '<p class="muted">Thinking...</p>';
  try {
    const r = await post('/api/v1/merchant/ai/ask', { question });
    cache.ask = r;
    renderAsk(r);
  } catch (e) {
    cache.ask = null;
    out.innerHTML = `<div class="alert alert-error" role="alert">Could not answer that: ${esc(e.message)}</div>`;
  } finally { btn.disabled = false; out.setAttribute('aria-busy', 'false'); }
}

function renderAsk(r) {
  const cols = r.columns || [], rows = r.rows || [];
  const cell = (v) => (typeof v === 'number' ? `<td class="num">${esc(Number.isInteger(v) ? v : v.toFixed(2))}</td>` : `<td>${esc(v)}</td>`);
  const table = cols.length ? `<div class="table-wrap"><table class="table">
      <thead><tr>${cols.map((c) => `<th scope="col">${esc(c)}</th>`).join('')}</tr></thead>
      <tbody>${rows.length ? rows.map((row) => `<tr>${row.map(cell).join('')}</tr>`).join('') : `<tr><td colspan="${cols.length}" class="muted">No rows returned.</td></tr>`}</tbody></table></div>` : '';
  const charted = canChart(r);
  $('ask-result').innerHTML = `<div class="answer">
      <p class="answer-text">${esc(r.answer)}</p>
      ${aiBadge(r)}${r.note ? `<p class="muted small">${esc(r.note)}</p>` : ''}
      ${r.sql ? `<details class="sql"><summary>Generated SQL</summary><pre><code>${esc(r.sql)}</code></pre></details>` : ''}
      ${table}
      ${charted ? '<div class="chart-box"><canvas id="chart-ask" role="img" aria-label="Chart of the query result"></canvas></div>' : ''}
    </div>`;
  if (charted) drawAskChart();
}

function canChart(r) {
  return r.chartType && r.chartType !== 'none' && (r.rows || []).length > 0 &&
    (r.columns || []).includes(r.chartLabelColumn) && (r.columns || []).includes(r.chartValueColumn) && window.Chart;
}

function drawAskChart() {
  const r = cache.ask;
  if (!r || !$('chart-ask') || !canChart(r)) return;
  const t = applyChartDefaults();
  const li = r.columns.indexOf(r.chartLabelColumn), vi = r.columns.indexOf(r.chartValueColumn);
  const labels = r.rows.map((row) => String(row[li]));
  const data = r.rows.map((row) => Number(row[vi]));
  const pie = r.chartType === 'pie';
  const type = pie ? 'doughnut' : r.chartType === 'line' ? 'line' : 'bar';
  mount('chart-ask', {
    type,
    data: { labels, datasets: [{
      label: r.chartValueColumn, data,
      backgroundColor: pie ? labels.map((_, i) => t.palette[i % t.palette.length]) : (type === 'line' ? `${t.accent}22` : t.accent),
      borderColor: pie ? t.surface : t.accent, borderWidth: pie ? 2 : 2, fill: type === 'line', tension: 0.25,
    }] },
    options: {
      responsive: true, maintainAspectRatio: false,
      scales: pie ? {} : { x: { grid: { display: false }, ticks: { maxTicksLimit: 10 } }, y: { beginAtZero: true } },
      plugins: { legend: { display: pie, position: 'right', labels: { color: t.text } } },
    },
  });
}
