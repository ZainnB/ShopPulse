import { post, setSession, getSession, showMsg, initNav } from './api.js';

initNav();
const msg = document.getElementById('form-msg');
const btn = document.getElementById('submit-btn');
const loginForm = document.getElementById('login-form');
const regForm = document.getElementById('register-form');

if (new URLSearchParams(location.search).get('expired')) {
  showMsg(msg, 'Your session expired. Please log in again.', 'info');
}

function landing(role) {
  return role === 'MERCHANT' ? '/merchant/dashboard' : '/';
}

async function submit(path, payload) {
  showMsg(msg, '');
  btn.disabled = true;
  const label = btn.textContent;
  btn.textContent = 'Please wait...';
  try {
    const auth = await post(path, payload, { auth: false, noRedirect: true });
    setSession(auth);
    if (!getSession()) throw new Error('Browser storage is blocked, so the login cannot be kept.');
    window.location.assign(landing(auth.role));
  } catch (e) {
    showMsg(msg, e.message);
    btn.disabled = false;
    btn.textContent = label;
  }
}

if (loginForm) {
  const email = document.getElementById('email');
  const password = document.getElementById('password');
  const fill = (e, p) => { email.value = e; password.value = p; password.focus(); };
  document.getElementById('fill-merchant').addEventListener('click', () => {
    fill('merchant@shoppulse.dev', 'merchant123');
    loginForm.requestSubmit();
  });
  document.getElementById('fill-customer').addEventListener('click', () => {
    fill('customer@shoppulse.dev', 'customer123');
    loginForm.requestSubmit();
  });
  loginForm.addEventListener('submit', (ev) => {
    ev.preventDefault();
    if (!email.value.trim() || !password.value) { showMsg(msg, 'Enter your email and password.'); return; }
    submit('/api/v1/auth/login', { email: email.value.trim(), password: password.value });
  });
}

if (regForm) {
  regForm.addEventListener('submit', (ev) => {
    ev.preventDefault();
    const displayName = document.getElementById('displayName').value.trim();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;
    if (!displayName || !email || password.length < 8) {
      showMsg(msg, 'Fill in every field; the password needs at least 8 characters.');
      return;
    }
    submit('/api/v1/auth/register', { email, password, displayName });
  });
}
