/* ═══════════════════════════════════════════════════════════════════════
   Rentify — Production-grade auth (frontend-only)
   - JWT issued by /api/v1/auth/login & /register stored in localStorage
   - Brute-force lockout (per email) tracked in localStorage
   - Idle auto-logout on /rent, /return, /cars after 30 min of inactivity
   - Navbar session pill ("Signed in as X · 28:14 left")
   - Auto Authorization: Bearer header for every fetch via authFetch()
   ═══════════════════════════════════════════════════════════════════════ */

(() => {
  'use strict';

  // ── Constants ──────────────────────────────────────────────────────────
  const TOKEN_KEY  = 'rentify_jwt';
  const USER_KEY   = 'rentify_user';
  const EXP_KEY    = 'rentify_jwt_exp';          // epoch ms
  const IAT_KEY    = 'rentify_jwt_iat';
  const LOCK_KEY   = 'rentify_lockout';          // {email: {count, untilMs}}
  const ACTIVITY_KEY = 'rentify_last_activity';
  const PROTECTED_PATHS = ['/rent', '/return', '/cars'];
  const IDLE_LIMIT_MS = 30 * 60 * 1000;          // 30 min
  const IDLE_WARN_MS  = 28 * 60 * 1000;          // warn at 28 min
  const LOCK_TIERS = [
    { after: 3,  ms:     30_000, label: '30 seconds' },
    { after: 5,  ms:  5 * 60_000, label: '5 minutes'  },
    { after: 8,  ms: 24 * 60 * 60_000, label: '24 hours' },
  ];

  // ── Token helpers ──────────────────────────────────────────────────────
  const getToken = () => localStorage.getItem(TOKEN_KEY);
  const getUser  = () => { try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null'); } catch { return null; } };
  const isLoggedIn = () => {
    const t = getToken();
    const exp = Number(localStorage.getItem(EXP_KEY) || 0);
    return !!t && (!exp || Date.now() < exp);
  };

  function saveAuth(token, user, exp) {
    try {
      localStorage.setItem(TOKEN_KEY, token);
      localStorage.setItem(USER_KEY, JSON.stringify(user));
      // Decode iat from JWT payload (best-effort, base64url)
      const iat = decodeJwtIat(token) || Date.now();
      localStorage.setItem(IAT_KEY, String(iat));
      const finalExp = exp || (iat + 30 * 24 * 60 * 60 * 1000);
      localStorage.setItem(EXP_KEY, String(finalExp));
      touch();
    } catch (e) { console.warn('saveAuth failed', e); }
  }

  function clearAuth() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(EXP_KEY);
    localStorage.removeItem(IAT_KEY);
  }

  function decodeJwtIat(token) {
    try {
      const part = token.split('.')[1];
      const padded = part.replace(/-/g, '+').replace(/_/g, '/');
      const json = atob(padded);
      const claims = JSON.parse(json);
      if (claims.iat) return claims.iat * 1000;
      if (claims.exp) return (claims.exp * 1000) - 30 * 24 * 60 * 60 * 1000;
    } catch {}
    return null;
  }

  // ── authFetch: global wrapper that attaches Bearer header ──────────────
  window.authFetch = function authFetch(input, init = {}) {
    const headers = new Headers(init.headers || {});
    const t = getToken();
    if (t) headers.set('Authorization', 'Bearer ' + t);
    if (init.body && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }
    return fetch(input, { ...init, headers });
  };

  // ── Brute-force lockout ────────────────────────────────────────────────
  function readLockouts() {
    try { return JSON.parse(localStorage.getItem(LOCK_KEY) || '{}'); } catch { return {}; }
  }
  function writeLockouts(obj) { localStorage.setItem(LOCK_KEY, JSON.stringify(obj)); }

  function getLockout(email) {
    if (!email) return null;
    const map = readLockouts();
    const rec = map[email.toLowerCase()];
    if (!rec) return null;
    if (rec.untilMs && Date.now() < rec.untilMs) return rec;
    if (rec.untilMs && Date.now() >= rec.untilMs) {
      delete map[email.toLowerCase()];
      writeLockouts(map);
    }
    return null;
  }

  function bumpLockout(email) {
    const map = readLockouts();
    const key = email.toLowerCase();
    const rec = map[key] || { count: 0, untilMs: 0 };
    rec.count += 1;
    const tier = [...LOCK_TIERS].reverse().find(t => rec.count >= t.after);
    if (tier) rec.untilMs = Date.now() + tier.ms;
    map[key] = rec;
    writeLockouts(map);
    return tier ? { tier, untilMs: rec.untilMs, count: rec.count } : { count: rec.count };
  }

  function resetLockout(email) {
    const map = readLockouts();
    delete map[email.toLowerCase()];
    writeLockouts(map);
  }

  // Exposed for the UI handlers
  window.__authInternal = { getLockout, bumpLockout, resetLockout, isLoggedIn, getUser, getToken, clearAuth, saveAuth, touch };

  // ── Banners ────────────────────────────────────────────────────────────
  window.showBanner = function (msg, kind = 'error') {
    const b = document.getElementById('auth-banner');
    if (!b) return;
    b.className = 'auth-banner auth-banner-' + kind;
    document.getElementById('auth-banner-text').textContent = msg;
    b.hidden = false;
  };
  window.hideBanner = function () {
    const b = document.getElementById('auth-banner');
    if (b) b.hidden = true;
  };

  // ── Password visibility toggle ────────────────────────────────────────
  window.togglePassword = function (id, btn) {
    const input = document.getElementById(id);
    if (!input) return;
    if (input.type === 'password') { input.type = 'text'; btn.textContent = '🙈'; btn.setAttribute('aria-label', 'Hide password'); }
    else { input.type = 'password'; btn.textContent = '👁'; btn.setAttribute('aria-label', 'Show password'); }
  };

  // ── Tabs ───────────────────────────────────────────────────────────────
  let currentTab = 'login';
  window.switchTab = function (tab) {
    currentTab = tab;
    const loginForm = document.getElementById('login-form');
    const regForm = document.getElementById('register-form');
    const tabLogin = document.getElementById('tab-login');
    const tabReg = document.getElementById('tab-register');
    if (!loginForm || !regForm) return;
    if (tab === 'login') {
      loginForm.hidden = false;
      regForm.hidden = true;
      tabLogin.classList.add('active'); tabLogin.setAttribute('aria-selected', 'true');
      tabReg.classList.remove('active'); tabReg.setAttribute('aria-selected', 'false');
      setTimeout(() => document.getElementById('login-email')?.focus(), 30);
    } else {
      loginForm.hidden = true;
      regForm.hidden = false;
      tabReg.classList.add('active'); tabReg.setAttribute('aria-selected', 'true');
      tabLogin.classList.remove('active'); tabLogin.setAttribute('aria-selected', 'false');
      setTimeout(() => document.getElementById('reg-name')?.focus(), 30);
    }
    hideBanner();
  };

  // ── Strength meter (for register) ─────────────────────────────────────
  function paintStrength(pw) {
    const bar = document.querySelector('#pw-strength .auth-strength-bar');
    if (!bar) return;
    let score = 0;
    if (pw.length >= 6) score++;
    if (pw.length >= 10) score++;
    if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) score++;
    if (/\d/.test(pw)) score++;
    if (/[^A-Za-z0-9]/.test(pw)) score++;
    const pct = Math.min(100, (score / 5) * 100);
    bar.style.width = pct + '%';
    bar.dataset.score = String(score);
    bar.style.background =
      score <= 1 ? 'var(--red)' :
      score <= 2 ? '#f59e0b' :
      score <= 3 ? '#facc15' :
                   'var(--green)';
  }
  document.addEventListener('input', (e) => {
    if (e.target && e.target.id === 'reg-password') paintStrength(e.target.value);
  });

  // ── Loading state on submit button ────────────────────────────────────
  function setSubmitting(btn, on) {
    if (!btn) return;
    btn.disabled = on;
    btn.classList.toggle('loading', on);
  }

  // ── Lockout overlay controller ─────────────────────────────────────────
  let lockoutTimer = null;
  function showLockoutOverlay(untilMs) {
    const overlay = document.getElementById('lockout-overlay');
    const timeEl = document.getElementById('lockout-time');
    if (!overlay) return;
    overlay.hidden = false;
    const tick = () => {
      const remaining = Math.max(0, untilMs - Date.now());
      if (remaining <= 0) { overlay.hidden = true; if (lockoutTimer) clearInterval(lockoutTimer); return; }
      const s = Math.ceil(remaining / 1000);
      const mm = Math.floor(s / 60).toString().padStart(2, '0');
      const ss = (s % 60).toString().padStart(2, '0');
      timeEl.textContent = `${mm}:${ss}`;
    };
    tick();
    lockoutTimer = setInterval(tick, 500);
  }
  function maybeShowLockout(email) {
    const rec = getLockout(email);
    if (rec && rec.untilMs) showLockoutOverlay(rec.untilMs);
  }
  // Re-evaluate lockout on load (in case page reloaded during cooldown)
  document.addEventListener('DOMContentLoaded', () => {
    const e = document.getElementById('login-email');
    if (e && e.value) maybeShowLockout(e.value);
  });

  // ── Form: Sign in ─────────────────────────────────────────────────────
  const loginForm = document.getElementById('login-form');
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      hideBanner();
      const email = document.getElementById('login-email').value.trim();
      const password = document.getElementById('login-password').value;
      const submitBtn = document.getElementById('login-submit');

      // Client-side validation
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showBanner('Enter a valid email address.', 'error'); return;
      }
      if (!password || password.length < 6) {
        showBanner('Password must be at least 6 characters.', 'error'); return;
      }

      // Lockout check
      const lock = getLockout(email);
      if (lock && lock.untilMs) {
        showLockoutOverlay(lock.untilMs);
        showBanner('This account is temporarily locked. See timer below.', 'error');
        return;
      }

      setSubmitting(submitBtn, true);
      try {
        const res = await fetch('/api/v1/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
          const outcome = bumpLockout(email);
          if (outcome.tier) {
            showLockoutOverlay(outcome.untilMs);
            showBanner(`Too many failed attempts. Locked for ${outcome.tier.label}.`, 'error');
          } else {
            const left = Math.max(0, (LOCK_TIERS[0].after) - outcome.count);
            showBanner(`${data.detail || 'Invalid email or password.'} (${left} attempt${left===1?'':'s'} left)`, 'error');
            loginForm.classList.remove('shake'); void loginForm.offsetWidth; loginForm.classList.add('shake');
          }
          return;
        }
        // success
        resetLockout(email);
        saveAuth(data.token, data.user, data.expiresAt);
        const remember = document.getElementById('login-remember')?.checked;
        if (remember) localStorage.setItem('rentify_remember', '1');
        // Toast then redirect
        showBanner('Welcome back, ' + (data.user.displayName || 'friend') + '!', 'success');
        const dest = new URLSearchParams(location.search).get('next') || '/';
        setTimeout(() => { window.location.href = dest; }, 350);
      } catch (err) {
        showBanner('Network error: ' + err.message + '. Please try again.', 'error');
      } finally {
        setSubmitting(submitBtn, false);
      }
    });
  }

  // ── Form: Register ────────────────────────────────────────────────────
  const regForm = document.getElementById('register-form');
  if (regForm) {
    regForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      hideBanner();
      const displayName = document.getElementById('reg-name').value.trim();
      const email = document.getElementById('reg-email').value.trim();
      const password = document.getElementById('reg-password').value;
      const password2 = document.getElementById('reg-password2').value;
      const submitBtn = document.getElementById('register-submit');

      if (displayName.length < 2) { showBanner('Display name must be at least 2 characters.', 'error'); return; }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { showBanner('Enter a valid email address.', 'error'); return; }
      if (password.length < 6) { showBanner('Password must be at least 6 characters.', 'error'); return; }
      if (password !== password2) { showBanner('Passwords do not match.', 'error'); return; }

      setSubmitting(submitBtn, true);
      try {
        const res = await fetch('/api/v1/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ displayName, email, password })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
          const msg = data.detail || (res.status === 409 ? 'An account with that email already exists.' : 'Could not create account.');
          showBanner(msg, 'error');
          regForm.classList.remove('shake'); void regForm.offsetWidth; regForm.classList.add('shake');
          return;
        }
        // success → user is now logged in
        saveAuth(data.token, data.user, data.expiresAt);
        showBanner('Account created. Welcome!', 'success');
        setTimeout(() => { window.location.href = '/'; }, 400);
      } catch (err) {
        showBanner('Network error: ' + err.message, 'error');
      } finally {
        setSubmitting(submitBtn, false);
      }
    });
  }

  // ── Idle activity tracking ────────────────────────────────────────────
  function touch() { localStorage.setItem(ACTIVITY_KEY, String(Date.now())); }

  ['mousemove', 'keydown', 'click', 'scroll', 'touchstart'].forEach(ev => {
    document.addEventListener(ev, touch, { passive: true });
  });

  // ── Idle enforcement on protected pages ────────────────────────────────
  const path = window.location.pathname;
  const isProtected = PROTECTED_PATHS.some(p => path === p || path.startsWith(p + '/'));
  let idleWarned = false;
  function checkIdle() {
    if (!isLoggedIn() || !isProtected) return;
    const last = Number(localStorage.getItem(ACTIVITY_KEY) || Date.now());
    const idle = Date.now() - last;
    if (idle >= IDLE_LIMIT_MS) {
      // Auto-logout: clear JWT and bounce to login
      clearAuth();
      const next = encodeURIComponent(window.location.pathname + window.location.search);
      window.location.href = '/login?next=' + next + '&reason=idle';
      return;
    }
    if (idle >= IDLE_WARN_MS && !idleWarned) {
      idleWarned = true;
      showSessionWarning();
    }
  }
  setInterval(checkIdle, 15_000);
  document.addEventListener('DOMContentLoaded', touch);

  function showSessionWarning() {
    // Lightweight, dismissable banner injected into the page
    if (document.getElementById('session-warn')) return;
    const bar = document.createElement('div');
    bar.id = 'session-warn';
    bar.className = 'session-warn';
    bar.setAttribute('role', 'alertdialog');
    bar.innerHTML = `
      <span>🛡 Still there? Your session will end in <strong id="session-warn-time">2:00</strong> due to inactivity.</span>
      <button type="button" onclick="document.getElementById('session-warn')?.remove(); window.__authInternal?.touch();">I'm here</button>`;
    document.body.appendChild(bar);
    let remaining = 120;
    const t = setInterval(() => {
      remaining -= 1;
      const el = document.getElementById('session-warn-time');
      if (!el) { clearInterval(t); return; }
      const mm = Math.floor(remaining/60).toString().padStart(2,'0');
      const ss = (remaining%60).toString().padStart(2,'0');
      el.textContent = `${mm}:${ss}`;
      if (remaining <= 0) clearInterval(t);
    }, 1000);
  }

  // ── Session pill in navbar ────────────────────────────────────────────
  function renderSessionPill() {
    const slot = document.getElementById('session-slot');
    if (!slot) return;

    // OIDC name (server-rendered from Spring Security principal)
    const oidcName = (slot.dataset.oidcName || '').trim();

    // Hide static Google fallback once we own the slot
    const fallback = slot.querySelector('[data-fallback-signin]');
    if (fallback) fallback.style.display = 'none';

    if (!isLoggedIn()) {
      if (oidcName) {
        // OIDC-authenticated user without a JWT — show name + Google sign-out
        slot.innerHTML = `
          <div class="session-pill" id="session-pill" title="Click to sign out">
            <span class="session-avatar">${escapeHtml(oidcName.charAt(0).toUpperCase())}</span>
            <span class="session-meta">
              <span class="session-name">${escapeHtml(oidcName)}</span>
              <span class="session-time">via Google</span>
            </span>
          </div>`;
        document.getElementById('session-pill')?.addEventListener('click', () => {
          if (confirm('Sign out of Google for Rentify?')) {
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '/logout';
            document.body.appendChild(form);
            form.submit();
          }
        });
        return;
      }
      // No JWT and no OIDC — show a fresh "Sign in" link
      const link = document.createElement('a');
      link.href = '/login';
      link.className = 'nav-link';
      link.style.cssText = 'display:inline-flex;align-items:center;gap:6px';
      link.textContent = '🔑 Sign in';
      slot.appendChild(link);
      return;
    }

    // JWT-authenticated user
    const user = getUser();
    slot.innerHTML = `
      <div class="session-pill" id="session-pill" title="Click to sign out">
        <span class="session-avatar">${escapeHtml((user?.displayName || user?.email || '?').charAt(0).toUpperCase())}</span>
        <span class="session-meta">
          <span class="session-name">${escapeHtml(user?.displayName || user?.email || 'User')}</span>
          <span class="session-time" id="session-time">—</span>
        </span>
      </div>`;
    document.getElementById('session-pill')?.addEventListener('click', () => {
      if (confirm('Sign out of Rentify?')) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = '/logout';
        document.body.appendChild(form);
        form.submit();
      }
    });
    updateSessionTime();
    setInterval(updateSessionTime, 1000);
  }

  function updateSessionTime() {
    const el = document.getElementById('session-time');
    if (!el) return;
    if (!isLoggedIn()) { el.textContent = ''; return; }
    const iat = Number(localStorage.getItem(IAT_KEY) || Date.now());
    const exp = Number(localStorage.getItem(EXP_KEY) || (iat + 30*24*60*60*1000));
    const last = Number(localStorage.getItem(ACTIVITY_KEY) || Date.now());
    const now = Date.now();
    const sinceLogin = formatRelative(now - iat);
    const leftHard = formatHMS(exp - now);
    const leftIdle = formatHMS(Math.max(0, (last + IDLE_LIMIT_MS) - now));
    el.textContent = `Signed in ${sinceLogin} · idle ${leftIdle}`;
    // Expiry banner if expiring soon
    if (exp - now < 5 * 60 * 1000 && !document.getElementById('expiry-warn')) {
      showExpiryBanner(Math.ceil((exp - now) / 1000));
    }
  }

  function showExpiryBanner(secs) {
    const bar = document.createElement('div');
    bar.id = 'expiry-warn';
    bar.className = 'session-warn session-warn-exp';
    bar.innerHTML = `<span>⏳ Your session expires in <strong id="expiry-time">${secs}s</strong>. Sign in again to refresh.</span>
      <a href="/login" class="btn btn-primary btn-xs" style="text-decoration:none">Refresh</a>`;
    document.body.appendChild(bar);
    const t = setInterval(() => {
      const el = document.getElementById('expiry-time');
      if (!el) { clearInterval(t); return; }
      secs -= 1;
      el.textContent = secs > 0 ? `${secs}s` : 'now';
      if (secs <= 0) { clearAuth(); window.location.href = '/login?reason=expired'; }
    }, 1000);
  }

  function formatRelative(ms) {
    if (ms < 60_000) return 'just now';
    const m = Math.floor(ms / 60_000);
    if (m < 60) return m + 'm ago';
    const h = Math.floor(m / 60);
    if (h < 24) return h + 'h ago';
    const d = Math.floor(h / 24); return d + 'd ago';
  }
  function formatHMS(ms) {
    if (ms <= 0) return '0:00';
    const s = Math.floor(ms / 1000);
    const mm = Math.floor(s / 60).toString().padStart(2, '0');
    const ss = (s % 60).toString().padStart(2, '0');
    return `${Math.min(99, parseInt(mm,10))}:${ss}`;
  }
  function escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c])); }

  document.addEventListener('DOMContentLoaded', renderSessionPill);

  // ── Redirect already-logged-in users away from /login ─────────────────
  document.addEventListener('DOMContentLoaded', () => {
    if (window.location.pathname === '/login' && isLoggedIn()) {
      const params = new URLSearchParams(location.search);
      const reason = params.get('reason');
      if (reason === 'idle') showBanner('You were signed out due to inactivity.', 'info');
      if (reason === 'expired') showBanner('Your session expired. Please sign in again.', 'info');
      // Don't redirect automatically; let user see the reason. They can hit "Sign in" again.
    }
  });

})();