// Hash routes work with a plain Flask static server (and on page refresh).
export function parseRoute(hash = '#/') {
  const segments = hash.replace(/^#\/?/, '').split('/').filter(Boolean);
  const [name, number, id] = segments;
  if (!name || name === 'course') return { name: 'dashboard' };
  if (name === 'settings' || name === 'review' || name === 'result' || name === 'career') return { name };
  if (name === 'lab') return /^lab-[a-z0-9-]+$/.test(number || '') && !id
    ? { name, labId: number } : { name: 'not-found' };
  if (['module', 'lesson', 'mastery', 'practice', 'boss'].includes(name) && /^[1-9]\d*$/.test(number || '')) {
    if (['lesson', 'mastery'].includes(name) && !/^m\d{2}-l\d{2}$/.test(id || '')) return { name: 'not-found' };
    return { name, moduleId: Number(number), lessonId: ['lesson', 'mastery'].includes(name) ? id : null };
  }
  return { name: 'not-found' };
}

export function navigate(path) {
  const hash = `#${path.startsWith('/') ? path : `/${path}`}`;
  if (window.location.hash === hash) {
    window.dispatchEvent(new Event('hashchange'));
  } else {
    window.location.hash = hash;
  }
}

export function subscribe(handler) {
  const listener = () => handler(parseRoute(window.location.hash));
  window.addEventListener('hashchange', listener);
  listener();
  return () => window.removeEventListener('hashchange', listener);
}
