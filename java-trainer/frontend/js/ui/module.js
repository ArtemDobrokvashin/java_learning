import { canOpenStep, moduleProgress } from '../utils/course.js';
import { escapeHtml, percentage, plural } from '../utils/helpers.js';
import { icon } from './icons.js';

export function renderModule(root, { module, state, onOpenStep, onBack }) {
  const progress = moduleProgress(module, state);
  const steps = module.lessons.map((lesson, index) => {
    const done = state.completedLessons.includes(lesson.id);
    const open = canOpenStep(module, lesson.id, state);
    const mastered = state.achievements?.includes(`mastery:${lesson.id}`);
    return `<div class="lesson-path-group"><button type="button" class="path-step ${done ? 'is-done' : open ? 'is-open' : 'is-locked'}" data-step="lesson" data-id="${escapeHtml(lesson.id)}" ${!open ? 'disabled' : ''}>
      <span class="path-node">${done ? icon('check', 23) : open ? String(index + 1).padStart(2, '0') : icon('lock', 20)}</span>
      <span class="path-step-copy"><small>УРОК ${index + 1} ${index === 0 ? '· НАЧНИТЕ ЗДЕСЬ' : ''}</small><strong>${escapeHtml(lesson.title)}</strong><span>${done ? 'Изучено · можно повторить' : open ? 'Перепишите пример · лёгкий · средний · сложный · хард по желанию' : 'Откроется после предыдущего урока'}</span></span>
      <span class="path-step-action">${done ? 'Повторить' : open ? 'Начать' : ''} ${icon(open ? 'arrow' : 'lock', 18)}</span>
    </button>${done ? `<button type="button" class="mastery-shortcut ${mastered ? 'is-mastered' : ''}" data-step="mastery" data-id="${escapeHtml(lesson.id)}">${icon('trophy', 16)} ${mastered ? 'Хард пройден · повторить' : 'Необязательный хард · 90 XP и достижение'} ${icon('arrow', 15)}</button>` : ''}</div>`;
  }).join('');
  const bossDone = !!module.boss && state.bossScores[module.id]?.passed;
  const bossOpen = canOpenStep(module, 'boss', state);
  const practiceDone = state.completedPractices.includes(module.id);
  const practiceOpen = canOpenStep(module, 'practice', state);

  root.innerHTML = `<div class="module-page page-enter">
    <button class="back-link" type="button" data-back>${icon('back', 18)} К карте курса</button>
    <div class="module-banner"><div class="module-banner-copy"><span class="banner-eyebrow">МОДУЛЬ ${String(module.id).padStart(2, '0')} / 22 <span>•</span> ${module.lessons.length} ${plural(module.lessons.length, 'УРОК', 'УРОКА', 'УРОКОВ')}</span><h1>${escapeHtml(module.title)}</h1><p>${escapeHtml(module.subtitle)}. Разберите подробную теорию, ответьте на вопросы и закрепите материал на практике.</p><div class="banner-tags"><span>${icon('book', 16)} Подробная теория</span><span>${icon('code', 16)} Настоящая Java</span><span>${icon('bolt', 16)} XP за каждый шаг</span></div></div><div class="module-progress-orb" style="--progress:${percentage(progress.done, progress.total)}%"><div><strong>${percentage(progress.done, progress.total)}%</strong><small>ПРОЙДЕНО</small></div></div></div>
    <div class="module-layout"><section class="module-journey" aria-labelledby="journey-title"><div class="section-heading compact"><div><span class="section-overline">УЧИТЕСЬ В СВОЁМ ТЕМПЕ</span><h2 id="journey-title">Ваши шаги</h2><p>${progress.done} из ${progress.total} завершено</p></div></div><div class="path-list">${steps}
      ${module.boss ? `<button type="button" class="path-step boss-step ${bossDone ? 'is-done' : bossOpen ? 'is-open' : 'is-locked'}" data-step="boss" ${!bossOpen ? 'disabled' : ''}><span class="path-node">${icon(bossDone ? 'check' : bossOpen ? 'trophy' : 'lock', 23)}</span><span class="path-step-copy"><small>МИНИ-ЭКЗАМЕН</small><strong>Стоп-урок</strong><span>5 вопросов · для зачёта нужно 4 правильных</span></span><span class="path-step-action">${bossDone ? 'Сдано' : bossOpen ? 'Сдать' : ''} ${icon(bossOpen ? 'arrow' : 'lock', 18)}</span></button>` : ''}
      <button type="button" class="path-step practice-step ${practiceDone ? 'is-done' : practiceOpen ? 'is-open' : 'is-locked'}" data-step="practice" ${!practiceOpen ? 'disabled' : ''}><span class="path-node">${icon(practiceDone ? 'check' : practiceOpen ? 'code' : 'lock', 23)}</span><span class="path-step-copy"><small>ЗАКРЕПЛЕНИЕ МАТЕРИАЛА</small><strong>Финальная практика</strong><span>7 разнообразных заданий · код проверяется javac</span></span><span class="path-step-action">${practiceDone ? 'Повторить' : practiceOpen ? 'Начать' : ''} ${icon(practiceOpen ? 'arrow' : 'lock', 18)}</span></button>
    </div></section><aside class="module-aside"><div class="aside-card"><span class="aside-icon">${icon('spark', 23)}</span><h3>Знания растут с практикой</h3><p>Ошибки — часть пути. За каждое верное решение вы получите XP, а сложные вопросы вернутся на повторение позже.</p><div class="aside-rule"></div><div class="aside-stat"><span>Уроков пройдено</span><strong>${progress.lessonsDone} / ${module.lessons.length}</strong></div><div class="aside-stat"><span>Достижений за хард</span><strong>${module.lessons.filter((lesson) => state.achievements?.includes(`mastery:${lesson.id}`)).length} / ${module.lessons.length}</strong></div><div class="aside-stat"><span>Практика</span><strong>${practiceDone ? 'Готово ✓' : 'Впереди'}</strong></div></div><div class="aside-note">${icon('info', 18)} Прогресс сохраняется в браузере и на вашем локальном Flask-сервере. Хард не блокирует следующий урок.</div></aside></div>
  </div>`;
  root.querySelector('[data-back]')?.addEventListener('click', onBack);
  root.querySelectorAll('[data-step]').forEach((button) => button.addEventListener('click', () =>
    onOpenStep(button.dataset.step, button.dataset.id)));
}
