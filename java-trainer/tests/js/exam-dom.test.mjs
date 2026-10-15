import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';
import { renderExam, cooldownText } from '../../frontend/js/ui/exam.js';
import { renderQuiz } from '../../frontend/js/ui/quiz.js';
import { renderModule } from '../../frontend/js/ui/module.js';
import { freshState, loadState, saveState, readExamDraft, resetState } from '../../frontend/js/storage.js';
import { canOpenStep, courseStatuses, moduleProgress, nextStep, stepUrl } from '../../frontend/js/utils/course.js';
import { parseRoute } from '../../frontend/js/router.js';

const load = (name) => JSON.parse(readFileSync(new URL(`../../frontend/data/${name}.json`, import.meta.url), 'utf8'));
const content = load('module-01');
const modules = load('modules').modules;
const exam = content.exam;
const module = modules[0];
const flush = () => new Promise((resolve) => setTimeout(resolve, 5));

function setup(t) {
  const dom = new JSDOM('<main id="root"></main>', { url: 'http://localhost/' });
  const names = ['window', 'document', 'localStorage', 'requestAnimationFrame', 'fetch'];
  const old = Object.fromEntries(names.map((key) => [key, globalThis[key]]));
  Object.assign(globalThis, { window: dom.window, document: dom.window.document,
    localStorage: dom.window.localStorage, requestAnimationFrame: (callback) => setTimeout(callback, 0) });
  let cleanup;
  const root = document.querySelector('#root');
  let record = {};
  let submitted;
  let responseError = null;
  globalThis.fetch = async (_url, options) => {
    const payload = options?.body ? JSON.parse(options.body) : null;
    if (payload?.action === 'start') record = { active: true, attemptId: 'attempt-1' };
    if (payload?.action === 'submit') {
      submitted = payload;
      if (responseError) return { ok: false, status: 503, json: async () => ({ error: responseError }) };
      record = { attemptId: 'attempt-1', active: false,
        result: { percent: 80, correct: 4, total: 5, passed: true, xp: 310,
          details: exam.tasks.map((task, i) => ({ id: task.id, correct: i !== 4, error: 'Пропущено', explanation: 'Разбор' })) },
        progress: { state: { examScores: { 1: { best: 80, passed: true } }, xp: 310 } } };
    }
    return { ok: true, status: 200, json: async () => ({ ...record, retryAt: record.retryAt || 0, serverNow: Math.floor(Date.now() / 1000) }) };
  };
  const options = { module, exam, health: { jdkAvailable: true }, onProfile: () => {},
    onComplete: () => {}, onTheory: () => {}, onExit: () => {} };
  const open = (overrides = {}) => { cleanup?.(); cleanup = renderExam(root, { ...options, ...overrides }); };
  t.after(() => { cleanup?.(); dom.window.close(); for (const name of names) {
    if (old[name] === undefined) delete globalThis[name]; else globalThis[name] = old[name];
  } });
  return { root, open, setRecord: (next) => { record = next; }, getSubmitted: () => submitted,
    setError: (next) => { responseError = next; } };
}

function click(root, selector) { const element = root.querySelector(selector); assert.ok(element, selector); element.click(); }
async function begin(ui) { ui.open(); await flush(); click(ui.root, '[data-start]'); await flush(); }

test('exams gate completion, route after practice, and appear after practice in every module', (t) => {
  const ui = setup(t);
  const state = freshState();
  state.completedLessons = module.lessons.map((lesson) => lesson.id);
  assert.equal(canOpenStep(module, 'exam', state), false);
  state.completedPractices.push(1);
  assert.equal(canOpenStep(module, 'exam', state), true);
  assert.equal(courseStatuses(modules, state)[1].status, 'locked');
  assert.equal(nextStep(module, state).type, 'exam');
  assert.equal(stepUrl(module, nextStep(module, state)), '/exam/1');
  assert.equal(moduleProgress(module, state).total, module.lessons.length + 2);
  state.examScores[1] = { best: 80, passed: true };
  assert.equal(courseStatuses(modules, state)[1].status, 'available');
  assert.equal(moduleProgress(module, state).done, moduleProgress(module, state).total);
  assert.equal(parseRoute('#/exam/22').name, 'exam');
  for (const entry of modules) {
    renderModule(ui.root, { module: entry, state, onOpenStep() {}, onBack() {} });
    const steps = [...ui.root.querySelectorAll('[data-step]')].map((button) => button.dataset.step);
    assert.equal(steps.indexOf('exam'), steps.indexOf('practice') + 1);
    assert.match(ui.root.querySelector('[data-step="exam"]').textContent, /Мини-экзамен/);
  }
});

test('welcome, skip/back, all answer widgets and editor restore after closing the tab', async (t) => {
  const ui = setup(t);
  await begin(ui);
  assert.match(ui.root.textContent, /Задача 1 из 5/);
  const firstChoice = ui.root.querySelector('input');
  firstChoice.click();
  await flush();
  click(ui.root, '[data-skip]');
  assert.match(ui.root.textContent, /Задача 2 из 5/);
  const fill = ui.root.querySelector('[data-blank]');
  fill.value = fill.tagName === 'SELECT' ? fill.options[1].value : 'int';
  fill.dispatchEvent(new window.Event('input', { bubbles: true }));
  await flush();
  const fillValue = fill.value;
  click(ui.root, '[data-task="2"]');
  click(ui.root, '[data-left]');
  click(ui.root, '[data-right]');
  await flush();
  const matchingValue = readExamDraft(1).answers[exam.tasks[2].id];
  assert.equal(Object.keys(matchingValue).length, 1);
  click(ui.root, '[data-task="3"]');
  click(ui.root, '[data-action="in"]');
  await flush();
  const parsonsValue = readExamDraft(1).answers[exam.tasks[3].id];
  click(ui.root, '[data-task="4"]');
  const code = 'public class Main { // Мяч\n}';
  ui.root.querySelector('textarea').value = code;
  // A close immediately after a keystroke must not lose work (no debounce dependency).
  window.dispatchEvent(new window.Event('pagehide'));
  ui.open(); await flush(); click(ui.root, '[data-start]'); await flush();
  assert.match(ui.root.textContent, /Задача 5 из 5/);
  assert.equal(ui.root.querySelector('textarea').value, code);
  click(ui.root, '[data-task="0"]');
  assert.equal(ui.root.querySelector('input:checked').value, firstChoice.value);
  click(ui.root, '[data-task="1"]');
  assert.equal(ui.root.querySelector('[data-blank]').value, fillValue);
  click(ui.root, '[data-task="2"]');
  assert.equal(ui.root.querySelectorAll('.match-left.is-paired').length, 1);
  assert.deepEqual(readExamDraft(1).answers[exam.tasks[2].id], matchingValue);
  click(ui.root, '[data-task="3"]');
  assert.deepEqual(readExamDraft(1).answers[exam.tasks[3].id], parsonsValue);
});

test('submission confirms skipped tasks and shows server result without per-task reward', async (t) => {
  const ui = setup(t);
  let profile;
  ui.open({ onProfile: (value) => { profile = value; } });
  await flush(); click(ui.root, '[data-start]'); await flush();
  click(ui.root, '[data-review]');
  assert.match(ui.root.textContent, /Без ответа/);
  click(ui.root, '[data-submit]'); await flush();
  assert.match(ui.root.textContent, /Поздравляем! Экзамен сдан/);
  assert.match(ui.root.textContent, /310 XP/);
  assert.ok(ui.root.querySelector('[data-complete]'));
  assert.equal(profile.state.xp, 310);
  assert.equal(ui.getSubmitted().attemptId, 'attempt-1');
  assert.ok(ui.getSubmitted().answers);
  assert.doesNotMatch(ui.root.textContent, /жизнь потеряна/i);
});

test('technical failure preserves answers and enables retry without cooldown', async (t) => {
  const ui = setup(t);
  ui.setError('Нет JDK');
  await begin(ui);
  ui.root.querySelector('input').click();
  click(ui.root, '[data-review]');
  const before = readExamDraft(1);
  click(ui.root, '[data-submit]'); await flush();
  assert.match(ui.root.textContent, /Нет JDK/);
  assert.equal(ui.root.querySelector('[data-submit]').disabled, false);
  assert.deepEqual(readExamDraft(1), before);
});

test('failed result restores review and server-clock cooldown; theory stays accessible', async (t) => {
  const ui = setup(t);
  ui.setRecord({ active: false, retryAt: Math.floor(Date.now() / 1000) + 3600,
    result: { percent: 60, correct: 3, total: 5, passed: false,
      details: [{ id: exam.tasks[0].id, correct: false, error: '<ошибка>', explanation: 'Повторите типы' }] } });
  ui.open(); await flush();
  assert.match(ui.root.textContent, /60%/);
  assert.match(ui.root.textContent, /Повторите типы/);
  assert.match(ui.root.textContent, /<ошибка>/);
  assert.equal(ui.root.querySelector('ошибка'), null);
  assert.equal(ui.root.querySelector('[data-retry]').disabled, true);
  assert.ok(ui.root.querySelector('[data-theory]'));
  assert.match(ui.root.querySelector('[data-cooldown]').textContent, /01:00:00|00:59:59/);
  assert.equal(cooldownText(4600, 1000), '01:00:00');
  assert.equal(cooldownText(1000, 1001), '00:00:00');
});

test('quiz widgets can restore values independently; exam certificates survive local reload/reset', (t) => {
  const { root } = setup(t);
  for (const task of exam.tasks.slice(0, 4)) {
    const value = task.type === 'choice' ? task.correct : task.type === 'fill'
      ? Object.fromEntries(task.blanks.map((blank) => [blank.id, blank.answer]))
      : task.type === 'matching' ? Object.fromEntries(task.pairs.map((pair) => [pair.id, pair.id])) : task.solution;
    const control = renderQuiz(root, task);
    control.setValue(value);
    assert.deepEqual(control.getValue(), value);
    control.destroy();
  }
  const state = freshState();
  state.examScores[1] = { best: 80, passed: true };
  state.achievements.push('exam:m01');
  saveState(state);
  assert.deepEqual(loadState().examScores, state.examScores);
  assert.deepEqual(loadState().achievements, ['exam:m01']);
  localStorage.setItem('javada.exam.v1.1', JSON.stringify({ answers: {} }));
  resetState();
  assert.equal(readExamDraft(1), null);
  assert.deepEqual(loadState().examScores, {});
});
