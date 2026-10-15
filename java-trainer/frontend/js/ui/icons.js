// Inline SVGs keep the entire interface readable without an icon CDN.
const paths = {
  map: '<path d="m3 6 6-2 6 2 6-2v14l-6 2-6-2-6 2z"/><path d="M9 4v14M15 6v14"/>',
  book: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>',
  code: '<path d="m8 17-5-5 5-5m8 0 5 5-5 5M14 4l-4 16"/>',
  spark: '<path d="m12 3 1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM19 17l.7 1.3L21 19l-1.3.7L19 21l-.7-1.3L17 19l1.3-.7z"/>',
  bolt: '<path d="m13 2-9 11h7l-1 9 10-12h-7z"/>',
  flame: '<path d="M12 22c4.4 0 7-3.1 7-7 0-3-2-5-3-6-.1 2-1.1 3-2 3 .5-5-3-8-5-10 0 4-4 6-4 12 0 4.5 2.7 8 7 8Z"/><path d="M12 22c1.8 0 3-1.5 3-3.2 0-1.2-.7-2.4-2.3-3.8-.1 1.2-.7 2-1.6 2.2-.2-.8-.6-1.3-1.1-1.8-.8 1.2-1.1 2.2-1.1 3.4 0 1.7 1.1 3.2 3.1 3.2Z"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  arrow: '<path d="M5 12h14m-6-6 6 6-6 6"/>',
  back: '<path d="M19 12H5m6 6-6-6 6-6"/>',
  chevron: '<path d="m9 18 6-6-6-6"/>',
  settings: '<path d="M12 15.3a3.3 3.3 0 1 0 0-6.6 3.3 3.3 0 0 0 0 6.6Z"/><path d="M19.4 15a1.8 1.8 0 0 0 .4 2l-1.8 1.8a1.8 1.8 0 0 0-2-.4 1.8 1.8 0 0 0-1 1.6V21h-2.5v-1a1.8 1.8 0 0 0-1.2-1.6 1.8 1.8 0 0 0-2 .4L7.5 17a1.8 1.8 0 0 0 .4-2 1.8 1.8 0 0 0-1.6-1H5v-2.5h1.3a1.8 1.8 0 0 0 1.6-1.2 1.8 1.8 0 0 0-.4-2L9.3 6.5a1.8 1.8 0 0 0 2 .4 1.8 1.8 0 0 0 1.2-1.6V4h2.5v1.3a1.8 1.8 0 0 0 1 1.6 1.8 1.8 0 0 0 2-.4l1.8 1.8a1.8 1.8 0 0 0-.4 2 1.8 1.8 0 0 0 1.6 1.2H22V14h-1a1.8 1.8 0 0 0-1.6 1Z" transform="translate(-1 -1)"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4m10-4v4M3 10h18m-13 4h2m4 0h2m-8 3h2"/>',
  trophy: '<path d="M8 20h8m-4-4v4M7 3h10v6a5 5 0 0 1-10 0V3ZM7 5H4v3a4 4 0 0 0 4 4m9-7h3v3a4 4 0 0 1-4 4"/>',
  refresh: '<path d="M20 11a8 8 0 1 0-2.2 6M20 4v7h-7"/>',
  play: '<path d="m8 5 11 7-11 7z"/>',
  moon: '<path d="M20 15.1A8 8 0 0 1 8.9 4 8 8 0 1 0 20 15.1Z"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  info: '<circle cx="12" cy="12" r="10"/><path d="M12 11v5m0-8h.01"/>',
  target: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>',
  layers: '<path d="m12 3 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5M3 16l9 5 9-5"/>',
  grip: '<circle cx="9" cy="7" r="1"/><circle cx="15" cy="7" r="1"/><circle cx="9" cy="12" r="1"/><circle cx="15" cy="12" r="1"/><circle cx="9" cy="17" r="1"/><circle cx="15" cy="17" r="1"/>',
  warning: '<path d="m12 3 10 18H2L12 3Z"/><path d="M12 9v5m0 3h.01"/>',
};

export function icon(name, size = 20, extraClass = '') {
  const safeSize = Number.isFinite(size) ? Math.max(12, Math.min(size, 80)) : 20;
  return `<svg class="icon ${extraClass}" width="${safeSize}" height="${safeSize}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.85" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${paths[name] || paths.spark}</svg>`;
}
