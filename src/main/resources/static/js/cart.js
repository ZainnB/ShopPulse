import { get, put, del, post, esc, money, initNav, requireLogin, showMsg, refreshCartCount } from './api.js';

initNav();
const root = document.getElementById('cart-root');
const msg = document.getElementById('cart-msg');

async function load() {
  root.setAttribute('aria-busy', 'true');
  try {
    render(await get('/api/v1/cart'));
  } catch (e) {
    root.innerHTML = '<p class="muted">Your cart could not be loaded.</p>';
    showMsg(msg, e.message);
  } finally {
    root.setAttribute('aria-busy', 'false');
  }
}

function render(cart) {
  refreshCartCount();
  if (!cart.items || !cart.items.length) {
    root.innerHTML = '<p class="empty">Your cart is empty. <a href="/">Browse the shop</a>.</p>';
    return;
  }
  const rows = cart.items.map((i) => `<tr>
      <th scope="row" class="item-name">${esc(i.name)}<div class="muted small">${money(i.unitPrice)} each &middot; ${esc(i.stockAvailable)} available</div></th>
      <td>
        <div class="qty">
          <button class="btn btn-ghost btn-sm" data-dec="${esc(i.productId)}" data-q="${esc(i.quantity)}" aria-label="Decrease quantity of ${esc(i.name)}">&minus;</button>
          <input type="number" min="1" max="${esc(i.stockAvailable)}" value="${esc(i.quantity)}" data-qty="${esc(i.productId)}" aria-label="Quantity of ${esc(i.name)}">
          <button class="btn btn-ghost btn-sm" data-inc="${esc(i.productId)}" data-q="${esc(i.quantity)}" aria-label="Increase quantity of ${esc(i.name)}">+</button>
        </div>
      </td>
      <td class="num">${money(i.lineTotal)}</td>
      <td class="num"><button class="btn btn-ghost btn-sm" data-remove="${esc(i.productId)}" aria-label="Remove ${esc(i.name)}">Remove</button></td>
    </tr>`).join('');
  root.innerHTML = `<div class="table-wrap"><table class="table">
      <thead><tr><th scope="col">Item</th><th scope="col">Quantity</th><th scope="col" class="num">Line total</th><th scope="col"><span class="sr-only">Actions</span></th></tr></thead>
      <tbody>${rows}</tbody></table></div>
    <div class="cart-foot">
      <button class="btn btn-ghost" id="clear-cart">Clear cart</button>
      <div class="cart-total"><span class="muted">Total</span> <strong>${money(cart.total)}</strong></div>
      <button class="btn btn-primary" id="checkout">Checkout (mock payment)</button>
    </div>`;
}

async function mutate(fn) {
  showMsg(msg, '');
  try { render(await fn()); } catch (e) { showMsg(msg, e.message); load(); }
}

root.addEventListener('click', async (e) => {
  const t = e.target.closest('button');
  if (!t) return;
  if (t.dataset.inc) mutate(() => put(`/api/v1/cart/items/${t.dataset.inc}`, { quantity: Number(t.dataset.q) + 1 }));
  else if (t.dataset.dec) {
    const q = Number(t.dataset.q) - 1;
    mutate(() => (q < 1 ? del(`/api/v1/cart/items/${t.dataset.dec}`) : put(`/api/v1/cart/items/${t.dataset.dec}`, { quantity: q })));
  } else if (t.dataset.remove) mutate(() => del(`/api/v1/cart/items/${t.dataset.remove}`));
  else if (t.id === 'clear-cart') {
    showMsg(msg, '');
    try { await del('/api/v1/cart'); load(); } catch (err) { showMsg(msg, err.message); }
  } else if (t.id === 'checkout') checkout(t);
});

root.addEventListener('change', (e) => {
  const input = e.target.closest('[data-qty]');
  if (!input) return;
  const q = Math.floor(Number(input.value));
  if (!(q >= 1)) { load(); return; }
  mutate(() => put(`/api/v1/cart/items/${input.dataset.qty}`, { quantity: q }));
});

async function checkout(btn) {
  showMsg(msg, '');
  btn.disabled = true;
  btn.textContent = 'Processing payment...';
  try {
    const order = await post('/api/v1/orders/checkout');
    refreshCartCount();
    root.innerHTML = `<div class="success-panel" role="status">
      <h2>Order #${esc(order.id)} placed</h2>
      <p>Payment accepted (mock). Total charged: <strong>${money(order.total)}</strong>. Status: ${esc(order.status)}.</p>
      <a class="btn btn-primary" href="/orders">View your orders</a></div>`;
  } catch (err) {
    if (err.status === 409) {
      showMsg(msg, `${err.message} Your cart has been refreshed. Adjust the quantities below and try again.`, 'warn');
    } else {
      showMsg(msg, err.message);
    }
    await load();
  }
}

if (requireLogin()) load();
