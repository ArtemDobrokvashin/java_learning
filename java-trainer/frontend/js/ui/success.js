import { formatTime, percentage } from '../utils/helpers.js';
import { icon } from './icons.js';

export function renderResult(root, { result, nextLabel, onNext, onRetry, onMap }) {
  const passed = result.passed;
  root.innerHTML = `<div class="result-page page-enter">
    <div class="result-confetti" aria-hidden="true">${passed ? Array.from({ length: 22 }, (_, index) => `<i style="--i:${index};--x:${(index * 43) % 100}%;--delay:${(index % 7) * 0.1}s"></i>`).join('') : ''}</div>
    <div class="result-medal ${passed ? '' : 'result-medal-fail'}">${icon(passed ? 'trophy' : 'refresh', 48)}</div>
    <div class="eyebrow centered">${passed ? 'ЕЩЁ ОДИН ШАГ ПРОЙДЕН' : 'ПОПРОБУЙТЕ ЕЩЁ РАЗ'}</div>
    <h1>${passed ? 'Это было здорово!' : 'Почти у цели!'}</h1>
    <p class="result-subtitle">${passed ? 'Вы разобрались в теме и стали немного сильнее в Java.' : 'Для зачёта нужно 4 из 5 правильных ответов. Повторите материал и возвращайтесь.'}</p>
    <div class="result-stats"><div class="result-stat"><span class="stat-icon xp-stat">${icon('bolt', 24)}</span><strong>+${result.earnedXp}</strong><small>XP ЗАРАБОТАНО</small></div><div class="result-stat"><span class="stat-icon accuracy-stat">${icon('target', 24)}</span><strong>${result.accuracy}%</strong><small>С ПЕРВОЙ ПОПЫТКИ</small></div><div class="result-stat"><span class="stat-icon time-stat">${icon('clock', 24)}</span><strong>${formatTime(result.seconds)}</strong><small>ВРЕМЯ УРОКА</small></div></div>
    <div class="result-progress"><span>Верно с первого раза</span><strong>${result.correctFirstTry} из ${result.total}</strong><div class="meter"><span style="width:${percentage(result.correctFirstTry, result.total)}%"></span></div></div>
    <div class="result-actions">${passed ? `<button type="button" class="button button-primary" data-next>${nextLabel || 'Следующий шаг'} ${icon('arrow', 20)}</button>` : `<button type="button" class="button button-primary" data-retry>Повторить экзамен ${icon('refresh', 20)}</button>`}<button type="button" class="button button-outline" data-map>Вернуться к карте</button></div>
  </div>`;
  root.querySelector('[data-next]')?.addEventListener('click', onNext);
  root.querySelector('[data-retry]')?.addEventListener('click', onRetry);
  root.querySelector('[data-map]')?.addEventListener('click', onMap);
}
