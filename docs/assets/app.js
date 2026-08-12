/*
  Everything on this page that changes between releases comes out of
  downloads.json: the version, the date, the build buttons and the changelog.
  Shipping a new version is one file edit and no HTML surgery.
*/

const REPO = 'https://github.com/Omai4x/neocalc';

document.querySelectorAll('#source-link, #source-link-foot').forEach((a) => { a.href = REPO; });
const issues = document.getElementById('issues-link');
if (issues) issues.href = REPO + '/issues';
const ds = document.getElementById('ds-link');
if (ds) ds.href = REPO + '/blob/main/design-system/neocalc/MASTER.md';

/** Anything from the manifest is escaped before it reaches the DOM. */
function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

/** A build still carrying the placeholder URL points at releases instead. */
const pending = (url) => !url || url.includes('YOUR_CLOUD_NAME');

fetch('downloads.json', { cache: 'no-cache' })
  .then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); })
  .then((data) => {
    document.querySelectorAll('[data-version]').forEach((n) => { n.textContent = data.version; });
    document.querySelectorAll('[data-released]').forEach((n) => { n.textContent = data.released; });

    document.getElementById('builds').innerHTML = (data.builds || []).map((b) => {
      const wait = pending(b.url);
      const href = wait ? REPO + '/releases' : b.url;
      return `
        <a class="build${b.recommended ? ' primary' : ''}" href="${esc(href)}"${wait ? '' : ' download'}>
          <span class="label">${esc(b.label)}
            <span class="sub num">${esc(b.arch)}${wait ? ' &middot; link pending' : ''}</span>
          </span>
          <span class="size num">${esc(b.size)}</span>
        </a>`;
    }).join('');

    // The changelog is the same data the app shows in its own What's new screen.
    const releases = document.getElementById('releases');
    if (releases && Array.isArray(data.history)) {
      releases.innerHTML = data.history.map((rel) => `
        <div class="release">
          <div class="release-head">
            <h3 class="num">${esc(rel.version)}</h3>
            <span class="date num">${esc(rel.date)}</span>
          </div>
          <p style="margin:6px 0 0;color:var(--ink-2)">${esc(rel.headline)}</p>
          <ul>${(rel.changes || []).map((c) => `<li>${esc(c)}</li>`).join('')}</ul>
        </div>`).join('');
    }
  })
  .catch(() => {
    // A failed fetch must still leave a way to get the app.
    document.getElementById('builds').innerHTML =
      `<a class="build primary" href="${REPO}/releases"><span class="label">Downloads on GitHub</span></a>`;
  });

/*
  The app hides an arcade behind a code typed on its keypad. Typing it here nods
  to that without spelling it out in the copy.
*/
(() => {
  const CODE = '4199';
  let typed = '';
  addEventListener('keydown', (e) => {
    if (!/^[0-9]$/.test(e.key)) return;
    typed = (typed + e.key).slice(-4);
    if (typed !== CODE) return;
    typed = '';
    const hero = document.querySelector('.hero .caption');
    if (!hero || hero.dataset.found) return;
    hero.dataset.found = '1';
    hero.style.color = '#f97316';
    hero.textContent = 'Same code works in the app. Type it, then hold the display.';
  });
})();
