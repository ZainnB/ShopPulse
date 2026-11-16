// Shared client: session storage, fetch wrapper (JWT + RFC 7807 parsing), nav wiring, small helpers.
const KEY = 'shoppulse.token';
const FIELDS = { email: 'shoppulse.email', displayName: 'shoppulse.displayName', role: 'shoppulse.role' };

export function getSession() {
  try {
    const token = localStorage.getItem(KEY);
    if (!token) return null;
    return {
      token,
      email: localStorage.getItem(FIELDS.email) || '',
      displayName: localStorage.getItem(FIELDS.displayName) || '',
      role: localStorage.getItem(FIELDS.role) || '',
    };
  } catch (e) { return null; }
}

export function setSession(auth) {
  try {
    localStorage.setItem(KEY, auth.token);
    localStorage.setItem(FIELDS.email, auth.email || '');
    localStorage.setItem(FIELDS.displayName, auth.displayName || '');
    localStorage.setItem(FIELDS.role, auth.role || '');
  } catch (e) { /* storage blocked: caller will find no session */ }
}

export function clearSession() {
  try {
    localStorage.removeItem(KEY);
    Object.values(FIELDS).forEach((k) => localStorage.removeItem(k));
  } catch (e) { /* ignore */ }
}

export class ApiError extends Error {
  constructor(message, status, problem) {
    super(message);
    this.status = status;
    this.problem = problem || null;
  }
}

function problemMessage(problem, status) {
  if (problem) {
    const parts = [];
    if (problem.detail) parts.push(problem.detail);
    else if (problem.title) parts.push(problem.title);
    if (problem.errors && typeof problem.errors === 'object') {
      const list = Array.isArray(problem.errors)
        ? problem.errors.map((e) => (typeof e === 'string' ? e : e.defaultMessage || e.message || JSON.stringify(e)))
        : Object.entries(problem.errors).map(([k, v]) => `${k}: ${v}`);
      if (list.length) parts.push(list.join('; '));
    }
    if (parts.length) return parts.join(' - ');
  }
  const generic = {
    400: 'The request was not valid.',
    401: 'Your session has expired. Please log in again.',
    403: 'You do not have permission to do that.',
    404: 'Not found.',
    409: 'That change conflicts with the current state.',
  };
  return generic[status] || `Request failed (HTTP ${status}).`;
}

export async function api(path, { method = 'GET', body, auth = true, noRedirect = false } = {}) {
  const headers = { Accept: 'application/json, application/problem+json' };
  const session = getSession();
  if (auth && session) headers.Authorization = `Bearer ${session.token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  let res;
  try {
    res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch (e) {
    throw new ApiError('Cannot reach the server. Check your connection and try again.', 0);
  }
  let data = null;
  const type = res.headers.get('content-type') || '';
  if (res.status !== 204 && type.includes('json')) {
    try { data = await res.json(); } catch (e) { data = null; }
  }
  if (!res.ok) {
    if (res.status === 401 && !noRedirect) {
      clearSession();
      window.location.assign('/login?expired=1');
    }
    throw new ApiError(problemMessage(data, res.status), res.status, data);
  }
  return data;
}

export const get = (p, o) => api(p, o);
export const post = (p, body, o) => api(p, { ...o, method: 'POST', body: body === undefined ? {} : body });
export const put = (p, body, o) => api(p, { ...o, method: 'PUT', body });
export const del = (p, o) => api(p, { ...o, method: 'DELETE' });

// ---- helpers ----
export function esc(v) {
  return String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
export const money = (n) => usd.format(Number(n) || 0);
export const num = (n) => new Intl.NumberFormat('en-US').format(Number(n) || 0);

export function fmtDateTime(iso) {
  const d = new Date(iso);
  return isNaN(d) ? '' : d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

export function showMsg(el, text, kind = 'error') {
  if (!el) return;
  if (!text) { el.hidden = true; el.textContent = ''; return; }
  el.textContent = text;
  el.className = `alert alert-${kind}`;
  el.hidden = false;
}

export function requireLogin() {
  const s = getSession();
  if (!s) { window.location.assign('/login'); return null; }
  return s;
}

export async function refreshCartCount() {
  const badge = document.getElementById('cart-count');
  if (!badge || !getSession()) return;
  try {
    const cart = await api('/api/v1/cart', { noRedirect: true });
    badge.textContent = cart.itemCount ?? 0;
    badge.hidden = !cart.itemCount;
  } catch (e) { badge.hidden = true; }
}

export function initNav() {
  const page = document.body.dataset.page;
  document.querySelectorAll('[data-nav]').forEach((a) => {
    if (a.dataset.nav === page) a.setAttribute('aria-current', 'page');
  });
  const s = getSession();
  const box = document.getElementById('nav-user');
  if (!box) return;
  if (s) {
    const dash = document.getElementById('nav-dashboard');
    if (dash && s.role === 'MERCHANT') dash.hidden = false;
    box.innerHTML = `<span class="nav-name" title="${esc(s.email)}">${esc(s.displayName || s.email)}</span>
      <span class="pill pill-role">${esc((s.role || '').toLowerCase())}</span>
      <button type="button" class="btn btn-ghost btn-sm" id="logout-btn">Log out</button>`;
    document.getElementById('logout-btn').addEventListener('click', () => {
      clearSession();
      window.location.assign('/login');
    });
    refreshCartCount();
  }
}
