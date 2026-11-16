import { get, post, esc, money, getSession, initNav, showMsg, refreshCartCount } from './api.js';

initNav();
const grid = document.getElementById('product-grid');
const pager = document.getElementById('pager');
const count = document.getElementById('result-count');
const toast = document.getElementById('toast');
const state = { q: '', categoryId: '', page: 0, size: 12 };
let toastTimer;

function flash(text, kind) {
  showMsg(toast, text, kind);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => showMsg(toast, ''), 5000);
}

async function loadCategories() {
  try {
    const cats = await get('/api/v1/categories', { auth: false });
    const sel = document.getElementById('category');
    sel.insertAdjacentHTML('beforeend', cats.map((c) => `<option value="${esc(c.id)}">${esc(c.name)}</option>`).join(''));
  } catch (e) {
    flash(`Could not load categories: ${e.message}`, 'error');
  }
}

function stockLabel(p) {
  if (p.stock <= 0) return '<span class="badge badge-bad">Out of stock</span>';
  if (p.stock <= 5) return `<span class="badge badge-warn">Only ${esc(p.stock)} left</span>`;
  return `<span class="badge badge-ok">${esc(p.stock)} in stock</span>`;
}

function card(p) {
  const session = getSession();
  let action;
  if (!session) action = '<a class="btn btn-ghost btn-block" href="/login">Log in to buy</a>';
  else if (p.stock <= 0) action = '<button class="btn btn-ghost btn-block" disabled>Unavailable</button>';
  else action = `<button class="btn btn-primary btn-block" data-add="${esc(p.id)}" data-name="${esc(p.name)}">Add to cart</button>`;
  return `<article class="product">
    <div class="product-cat">${esc(p.categoryName)}</div>
    <h3>${esc(p.name)}</h3>
    <p class="muted product-desc">${esc(p.description || '')}</p>
    <div class="product-foot"><strong class="price">${money(p.price)}</strong>${stockLabel(p)}</div>
    ${action}
  </article>`;
}

async function load() {
  grid.setAttribute('aria-busy', 'true');
  const params = new URLSearchParams({ page: state.page, size: state.size });
  if (state.q) params.set('q', state.q);
  if (state.categoryId) params.set('categoryId', state.categoryId);
  try {
    const page = await get(`/api/v1/products?${params}`, { auth: false });
    const items = (page.content || []).filter((p) => p.active !== false);
    grid.innerHTML = items.length ? items.map(card).join('') : '<p class="muted">No products match your search.</p>';
    count.textContent = `${page.totalElements ?? items.length} product${page.totalElements === 1 ? '' : 's'}`;
    renderPager(page);
  } catch (e) {
    grid.innerHTML = `<div class="alert alert-error" role="alert">Could not load products: ${esc(e.message)}</div>`;
    pager.innerHTML = '';
  } finally {
    grid.setAttribute('aria-busy', 'false');
  }
}

function renderPager(page) {
  const total = page.totalPages || 1;
  const cur = page.number || 0;
  if (total <= 1) { pager.innerHTML = ''; return; }
  pager.innerHTML = `<button class="btn btn-ghost btn-sm" data-page="${cur - 1}" ${cur === 0 ? 'disabled' : ''}>Previous</button>
    <span aria-current="page">Page ${cur + 1} of ${total}</span>
    <button class="btn btn-ghost btn-sm" data-page="${cur + 1}" ${cur + 1 >= total ? 'disabled' : ''}>Next</button>`;
}

pager.addEventListener('click', (e) => {
  const b = e.target.closest('[data-page]');
  if (!b || b.disabled) return;
  state.page = Number(b.dataset.page);
  load();
  window.scrollTo({ top: 0, behavior: 'smooth' });
});

grid.addEventListener('click', async (e) => {
  const b = e.target.closest('[data-add]');
  if (!b) return;
  b.disabled = true;
  try {
    await post('/api/v1/cart/items', { productId: Number(b.dataset.add), quantity: 1 });
    flash(`Added "${b.dataset.name}" to your cart.`, 'success');
    refreshCartCount();
  } catch (err) {
    flash(err.message, 'error');
  } finally {
    b.disabled = false;
  }
});

document.getElementById('filter-form').addEventListener('submit', (e) => {
  e.preventDefault();
  state.q = document.getElementById('q').value.trim();
  state.categoryId = document.getElementById('category').value;
  state.page = 0;
  load();
});

loadCategories();
load();
