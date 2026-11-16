import { get, esc, money, fmtDateTime, initNav, requireLogin, showMsg } from './api.js';

initNav();
const root = document.getElementById('orders-root');
const msg = document.getElementById('orders-msg');

const BADGE = { PENDING: 'warn', PAID: 'ok', SHIPPED: 'info', DELIVERED: 'ok', CANCELLED: 'bad' };

function order(o) {
  const lines = (o.items || []).map((i) => `<tr>
      <th scope="row">${esc(i.productName)}</th><td class="num">${esc(i.quantity)}</td>
      <td class="num">${money(i.unitPrice)}</td><td class="num">${money(i.lineTotal)}</td></tr>`).join('');
  return `<details class="card order">
    <summary>
      <span class="order-id">Order #${esc(o.id)}</span>
      <span class="muted">${esc(fmtDateTime(o.createdAt))}</span>
      <span class="badge badge-${BADGE[o.status] || 'info'}">${esc(o.status)}</span>
      <strong class="order-total">${money(o.total)}</strong>
    </summary>
    <div class="table-wrap"><table class="table">
      <thead><tr><th scope="col">Product</th><th scope="col" class="num">Qty</th><th scope="col" class="num">Unit price</th><th scope="col" class="num">Line total</th></tr></thead>
      <tbody>${lines}</tbody></table></div>
  </details>`;
}

async function load() {
  try {
    const orders = await get('/api/v1/orders');
    root.innerHTML = orders.length
      ? orders.map(order).join('')
      : '<p class="empty card">No orders yet. <a href="/">Start shopping</a>.</p>';
  } catch (e) {
    root.innerHTML = '';
    showMsg(msg, `Could not load orders: ${e.message}`);
  } finally {
    root.setAttribute('aria-busy', 'false');
  }
}

if (requireLogin()) load();
