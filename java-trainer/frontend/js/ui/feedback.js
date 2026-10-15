import { escapeHtml } from '../utils/helpers.js';
import { icon } from './icons.js';

// Never inject compiler output as HTML: Java errors can contain user code.
export function showFeedback(host, { status, title, message, detail, output, xp } = {}) {
  const symbols = { success: icon('check', 22), error: icon('warning', 22), notice: icon('info', 22) };
  host.innerHTML = `<div class="feedback feedback-${status || 'notice'}" role="status" aria-live="polite">
    <span class="feedback-icon">${symbols[status] || symbols.notice}</span>
    <div class="feedback-body">
      <strong>${escapeHtml(title || '')}</strong>
      ${message ? `<p>${escapeHtml(message)}</p>` : ''}
      ${Number.isFinite(xp) && xp > 0 ? `<span class="feedback-xp">+${xp} XP</span>` : ''}
    </div>
  </div>`;
  const body = host.querySelector('.feedback-body');
  if (detail) {
    const label = document.createElement('span');
    label.className = 'feedback-detail-label';
    label.textContent = 'Детали проверки';
    const pre = document.createElement('pre');
    pre.className = 'feedback-detail';
    pre.textContent = detail;
    body.append(label, pre);
  }
  if (output) {
    const label = document.createElement('span');
    label.className = 'feedback-detail-label';
    label.textContent = 'Вывод программы';
    const pre = document.createElement('pre');
    pre.className = 'feedback-detail';
    pre.textContent = output;
    body.append(label, pre);
  }
}

export function clearFeedback(host) {
  host.replaceChildren();
}
