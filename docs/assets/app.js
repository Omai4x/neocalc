/*
  The page reads its download links and version from downloads.json, so shipping
  a new build is one file edit and no HTML surgery. Paste the Cloudinary URLs in
  there and everything on this page updates: version, badge, date, buttons,
  sizes and the change list.
*/

const REPO = 'https://github.com/Omai4x/neocalc';

document.querySelectorAll('#source-link, #source-link-foot').forEach((link) => {
  link.href = REPO;
});

/** Escapes anything that came from the manifest before it reaches the DOM. */
function text(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

/** A build whose URL is still the placeholder is offered as a source link. */
function isPlaceholder(url) {
  return !url || url.includes('YOUR_CLOUD_NAME');
}

fetch('downloads.json', { cache: 'no-cache' })
  .then((response) => {
    if (!response.ok) throw new Error(response.status);
    return response.json();
  })
  .then((data) => {
    document.querySelectorAll('[data-version]').forEach((node) => {
      node.textContent = data.version;
    });
    document.querySelectorAll('[data-version-badge]').forEach((node) => {
      node.textContent = 'v' + data.version;
    });
    document.querySelectorAll('[data-released]').forEach((node) => {
      node.textContent = data.released;
    });
    document.querySelectorAll('[data-min-android]').forEach((node) => {
      node.textContent = data.minAndroid;
    });

    const builds = document.getElementById('builds');
    builds.innerHTML = (data.builds || []).map((build) => {
      const pending = isPlaceholder(build.url);
      const href = pending ? REPO + '/releases' : build.url;
      const style = build.recommended ? 'btn-primary' : 'btn-ghost';
      const note = pending ? ' (link pending)' : '';
      return `
        <a class="btn ${style}" href="${text(href)}"${pending ? '' : ' download'}>
          <span>${text(build.label)}</span>
          <span class="num" style="opacity:.75;font-weight:500">
            ${text(build.arch)} &middot; ${text(build.size)}${note}
          </span>
        </a>`;
    }).join('');

    const changes = document.getElementById('changes');
    changes.innerHTML = (data.changes || [])
      .map((change) => `<li>${text(change)}</li>`)
      .join('');
  })
  .catch(() => {
    // A failed fetch must still leave a way to get the app, so fall back to the
    // releases page rather than showing an empty card.
    document.getElementById('builds').innerHTML =
      `<a class="btn btn-primary" href="${REPO}/releases">Downloads on GitHub</a>`;
  });

/*
  The app hides an arcade behind a code typed on its keypad. Typing the same
  code here nods to it without giving the game away in the copy.
*/
(() => {
  const CODE = '4199';
  let typed = '';
  window.addEventListener('keydown', (event) => {
    if (!/^[0-9]$/.test(event.key)) return;
    typed = (typed + event.key).slice(-CODE.length);
    if (typed !== CODE) return;
    const note = document.createElement('p');
    note.className = 'hero-note';
    note.style.color = '#f97316';
    note.textContent = 'There is one of these in the app too. Hold the display.';
    document.querySelector('.hero .hero-note').after(note);
    typed = '';
  });
})();
