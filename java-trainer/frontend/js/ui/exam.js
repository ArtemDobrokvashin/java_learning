import { getExam, startExam, submitExam } from '../api.js';
import { readExamDraft, saveExamDraft } from '../storage.js';
import { escapeHtml } from '../utils/helpers.js';
import { renderQuiz } from './quiz.js';
import { icon } from './icons.js';

const DIFFICULTY = { easy: 'Лёгкая', medium: 'Средняя', hard: 'Сложная', complex: 'Мини-проект' };

export function cooldownText(retryAt, now) {
  const seconds = Math.max(0, Math.ceil(retryAt - now));
  return `${String(Math.floor(seconds / 3600)).padStart(2, '0')}:${String(Math.floor(seconds / 60) % 60).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}

// Separate from lessons: no recordAttempt, life counters or per-question XP.
export function renderExam(root, { module, exam, health, onProfile, onComplete, onTheory, onExit }) {
  let record = null;
  let draft = readExamDraft(module.id);
  let control = null;
  let index = 0;
  let disposed = false;
  let busy = false;
  let error = '';
  let offset = 0;
  let screen = 'loading';
  let saveQueued = false;

  const now = () => Date.now() / 1000 + offset;
  const setRecord = (value) => {
    record = value;
    offset = value.serverNow - Date.now() / 1000;
  };

  function persist() {
    if (!control || !draft || screen !== 'tasks') return;
    draft.answers[exam.tasks[index].id] = control.getValue();
    draft.index = index;
    saveExamDraft(module.id, draft);
  }
  function queuePersist() {
    if (screen === 'tasks' && draft && !draft.touched.includes(exam.tasks[index].id)) {
      draft.touched.push(exam.tasks[index].id);
    }
    if (saveQueued) return;
    saveQueued = true;
    queueMicrotask(() => { saveQueued = false; if (!disposed) persist(); });
  }
  function clearControl() {
    persist();
    control?.destroy();
    control = null;
  }
  function frame(body) {
    root.innerHTML = `<section class="exam-page page-enter" aria-label="Мини-экзамен">
      <button class="back-link" type="button" data-exit>${icon('back', 18)} К модулю</button>
      <div class="section-overline">МОДУЛЬ ${module.id} · ФИНАЛЬНОЕ ИСПЫТАНИЕ</div>
      ${body}<p class="exam-error" role="alert">${escapeHtml(error)}</p>
    </section>`;
    root.querySelector('[data-exit]').addEventListener('click', onExit);
  }
  function welcome() {
    clearControl();
    screen = 'welcome';
    const cooling = record?.retryAt > now();
    frame(`<div class="exam-intro aside-card"><span class="exam-emblem">${icon('trophy', 42)}</span>
      <h1>Мини-экзамен</h1><h2>${escapeHtml(module.title)}</h2>
      <p>${escapeHtml(exam.description)}</p>
      <div class="exam-facts"><div><strong>${exam.tasks.length}</strong><span>заданий по возрастанию сложности</span></div>
      <div><strong>80%</strong><span>минимум для зачёта</span></div><div><strong>+${exam.xpBonus} XP</strong><span>и достижение за первую сдачу</span></div></div>
      <p>Можно пропускать задания и возвращаться к ним до отправки. Все ответы проверяются вместе.
      Жизни не списываются. После неудачной попытки — 1 час на повторение, затем новая попытка без ограничений.</p>
      <p>Ответы сохраняются в этом браузере. Проверка Java доступна только на вашем локальном сервере с JDK 17+.</p>
      ${cooling ? '<p>До пересдачи: <strong data-cooldown></strong></p>' : ''}
      ${health?.jdkAvailable === false ? '<p class="exam-error">JDK недоступен. Проверьте настройки локального сервера перед экзаменом.</p>' : ''}
      <button class="button button-primary" type="button" data-start ${busy || cooling || health?.jdkAvailable === false ? 'disabled' : ''}>${busy ? 'Загрузка…' : record?.active ? 'Продолжить экзамен' : 'Начать'}</button>
      <button class="button button-outline" type="button" data-theory>Повторить теорию</button></div>`);
    root.querySelector('[data-start]').addEventListener('click', begin);
    root.querySelector('[data-theory]').addEventListener('click', onTheory);
    tick();
  }
  async function begin() {
    if (busy) return;
    busy = true; error = ''; welcome();
    try {
      const next = await startExam(module.id);
      if (disposed) return;
      setRecord(next);
      if (record.result) { results(); return; }
      if (!draft || draft.attemptId !== record.attemptId || typeof draft.answers !== 'object' || !draft.answers || Array.isArray(draft.answers)) {
        draft = { attemptId: record.attemptId, answers: {}, index: 0, visited: [], touched: [] };
      }
      draft.visited = Array.isArray(draft.visited) ? draft.visited : [];
      draft.touched = Array.isArray(draft.touched) ? draft.touched : [];
      index = Number.isInteger(draft.index) ? Math.min(Math.max(draft.index, 0), exam.tasks.length - 1) : 0;
      tasks();
    } catch (failure) {
      if (!disposed) { error = failure.message; welcome(); }
    } finally {
      busy = false;
      if (!disposed && screen === 'welcome') welcome();
    }
  }
  function tasks() {
    clearControl();
    screen = 'tasks';
    const task = exam.tasks[index];
    if (!draft.visited.includes(task.id)) draft.visited.push(task.id);
    frame(`<div class="exam-task-head"><span>Задача ${index + 1} из ${exam.tasks.length}</span>
      <span class="exam-difficulty difficulty-${task.difficulty}">${DIFFICULTY[task.difficulty]}</span></div>
      <progress class="exam-progress" max="${exam.tasks.length}" value="${index + 1}" aria-label="Задача ${index + 1} из ${exam.tasks.length}"></progress>
      <nav class="exam-numbers" aria-label="Перейти к заданию">${exam.tasks.map((entry, i) => `<button class="button ${i === index ? 'button-primary' : 'button-outline'}" type="button" data-task="${i}" ${i === index ? 'aria-current="step"' : ''} aria-label="Задание ${i + 1}${draft.visited.includes(entry.id) ? ', открыто' : ', ещё не открыто'}">${i + 1}</button>`).join('')}</nav>
      <h2 class="exam-prompt">${escapeHtml(task.prompt)}</h2>
      ${task.type === 'code' ? `<details class="exam-expected"><summary>Формат ожидаемого вывода</summary><pre>${escapeHtml(task.expectedOutput)}</pre></details>` : ''}
      <div data-exam-task></div>
      <div class="exam-actions"><button class="button button-outline" type="button" data-prev ${index === 0 ? 'disabled' : ''}>Назад</button>
      <button class="button button-outline" type="button" data-skip>Пропустить и вернуться</button>
      <button class="button button-primary" type="button" data-next>${index < exam.tasks.length - 1 ? 'Далее' : 'К отправке'}</button></div>
      <p class="interaction-help">До отправки можно изменить любой ответ. Пропущенные задания считаются неверными.</p>
      <button class="button button-outline" type="button" data-review>Проверить готовность и отправить</button>`);
    const taskHost = root.querySelector('[data-exam-task]');
    control = renderQuiz(taskHost, task, { exam: true, value: draft.answers[task.id], onChange: queuePersist });
    if (task.type !== 'code') control.setValue?.(draft.answers[task.id]);
    // Task widgets use delegated clicks/drag events; defer until they have updated their values.
    for (const event of ['input', 'change', 'click', 'drop']) taskHost.addEventListener(event, queuePersist);
    persist();
    const move = (next) => { persist(); clearControl(); index = next; tasks(); };
    root.querySelectorAll('[data-task]').forEach((button) => button.addEventListener('click', () => move(Number(button.dataset.task))));
    root.querySelector('[data-prev]').addEventListener('click', () => move(index - 1));
    root.querySelector('[data-next]').addEventListener('click', () => index + 1 < exam.tasks.length ? move(index + 1) : review());
    root.querySelector('[data-skip]').addEventListener('click', () => move((index + 1) % exam.tasks.length));
    root.querySelector('[data-review]').addEventListener('click', review);
  }
  function hasAnswer(task) {
    const value = draft.answers[task.id];
    if (typeof value === 'string') return value.trim() && value !== task.starterCode;
    if (Array.isArray(value)) return task.type === 'parsons' ? draft.touched.includes(task.id) : value.length > 0;
    return value && Object.values(value).some(Boolean);
  }
  function review() {
    clearControl(); screen = 'review';
    const missing = exam.tasks.filter((task) => !hasAnswer(task));
    frame(`<div class="aside-card"><h1>Готовы отправить?</h1><p>Ответов: ${exam.tasks.length - missing.length} из ${exam.tasks.length}.
      После отправки изменить ответы этой попытки нельзя.</p>
      ${missing.length ? `<p>Без ответа: ${missing.map((task) => exam.tasks.indexOf(task) + 1).join(', ')}. Они будут засчитаны как неверные.</p>` : ''}
      <div class="exam-actions"><button class="button button-outline" type="button" data-return ${busy ? 'disabled' : ''}>Вернуться к заданиям</button>
      <button class="button button-primary" type="button" data-submit ${busy ? 'disabled' : ''}>${busy ? 'Проверяем Java-код…' : 'Отправить экзамен'}</button></div>
      <p>Проверка нескольких программ может занять до двух минут. При сбое сервера ответы останутся сохранёнными, кулдаун не начнётся.</p></div>`);
    root.querySelector('[data-return]').addEventListener('click', tasks);
    root.querySelector('[data-submit]').addEventListener('click', send);
  }
  async function send() {
    if (busy) return;
    busy = true; error = ''; review();
    try {
      const answers = Object.fromEntries(exam.tasks.filter(hasAnswer).map((task) => [task.id, draft.answers[task.id]]));
      const next = await submitExam(module.id, record.attemptId, answers);
      if (disposed) return;
      setRecord(next);
      onProfile(next.progress);
      results();
    } catch (failure) {
      if (!disposed) { error = failure.message; busy = false; review(); }
    } finally { busy = false; }
  }
  function results() {
    clearControl(); screen = 'results';
    const result = record.result;
    frame(`<div class="aside-card exam-result ${result.passed ? 'exam-passed' : ''}">
      <span class="exam-emblem">${icon(result.passed ? 'trophy' : 'book', 42)}</span>
      <h1>${result.passed ? 'Поздравляем! Экзамен сдан' : 'Есть над чем поработать'}</h1>
      <p class="exam-score">${result.percent}% · ${result.correct} из ${result.total}</p>
      <p>${result.passed ? `+${result.xp} XP · Достижение «Модуль ${module.id}: экзамен сдан». Награда начисляется один раз. Следующий модуль разблокирован.` : 'Для зачёта нужно не менее 80%. Жизни не потрачены. Повторите сложные темы и попробуйте снова.'}</p>
      <ol class="exam-breakdown">${result.details.map((row, i) => `<li><strong>${row.correct ? '✓' : '✕'} Задача ${i + 1} · ${row.correct ? 'Верно' : 'Неверно'}</strong>${!row.correct ? `<p>${escapeHtml(row.error)}</p>${row.output ? `<p>Вывод программы:</p><pre>${escapeHtml(row.output)}</pre>` : ''}<pre>${escapeHtml(row.explanation)}</pre>` : ''}</li>`).join('')}</ol>
      ${result.passed ? '<button class="button button-primary" type="button" data-complete>Завершить модуль</button>' : '<p>До пересдачи: <strong data-cooldown></strong></p><div class="exam-actions"><button class="button button-outline" type="button" data-theory>Вернуться к теории модуля</button><button class="button button-primary" type="button" data-retry disabled>Пересдать</button></div>'}
      </div>`);
    root.querySelector('[data-complete]')?.addEventListener('click', onComplete);
    root.querySelector('[data-theory]')?.addEventListener('click', onTheory);
    root.querySelector('[data-retry]')?.addEventListener('click', begin);
    tick();
  }
  function tick() {
    const timer = root.querySelector('[data-cooldown]');
    if (timer) timer.textContent = cooldownText(record.retryAt, now());
    const retry = root.querySelector('[data-retry]');
    if (retry) retry.disabled = busy || record.retryAt > now();
    if (screen === 'welcome' && record?.retryAt && record.retryAt <= now()) {
      const button = root.querySelector('[data-start]');
      if (button) button.disabled = busy || health?.jdkAvailable === false;
    }
  }
  frame('<h1>Мини-экзамен</h1><p>Проверяем доступность и сохранённую попытку…</p>');
  getExam(module.id).then((next) => {
    if (disposed) return;
    setRecord(next);
    if (record.result) results(); else welcome();
  }).catch((failure) => {
    if (disposed) return;
    error = failure.message;
    frame('<h1>Экзамен пока недоступен</h1><p>Вернитесь к модулю и проверьте соединение с локальным сервером.</p>');
  });
  const timer = setInterval(tick, 1000);
  const onHidden = () => { if (document.hidden) persist(); };
  window.addEventListener('pagehide', persist);
  document.addEventListener('visibilitychange', onHidden);
  return () => {
    clearControl(); disposed = true;
    clearInterval(timer);
    window.removeEventListener('pagehide', persist);
    document.removeEventListener('visibilitychange', onHidden);
  };
}
