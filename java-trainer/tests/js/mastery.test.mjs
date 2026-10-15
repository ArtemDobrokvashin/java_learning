import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { isCorrect, copyTokens, validateInput } from '../../frontend/js/validator.js';
import { XP_BY_DIFFICULTY, XP_BY_TYPE } from '../../frontend/js/utils/constants.js';
import { recordAttempt } from '../../frontend/js/gamification.js';
import { freshState, loadState, saveState, readDraft, restoreDrafts } from '../../frontend/js/storage.js';
import { renderSession } from '../../frontend/js/ui/lesson.js';
import { parseRoute } from '../../frontend/js/router.js';
import { createSharedProgress } from '../../frontend/js/shared-progress.js';
import { JSDOM } from 'jsdom';

const load = (number) => JSON.parse(readFileSync(new URL(
  `../../frontend/data/module-${String(number).padStart(2, '0')}.json`, import.meta.url), 'utf8'));
const moduleOne = load(1);
const clock = new Date('2026-10-15T12:00:00Z');

function withStorage() {
  const store = new Map();
  return {
    store,
    localStorage: {
      getItem: (key) => store.get(key) ?? null,
      setItem: (key, value) => store.set(key, String(value)),
      removeItem: (key) => store.delete(key),
      key: (index) => [...store.keys()][index] ?? null,
      get length() { return store.size; },
    },
  };
}

function withDom(callback) {
  const dom = new JSDOM('<main id="view"></main>', { url: 'http://127.0.0.1:8000' });
  const old = { window: globalThis.window, document: globalThis.document, localStorage: globalThis.localStorage };
  const { store, localStorage } = withStorage();
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  globalThis.localStorage = localStorage;
  dom.window.HTMLElement.prototype.scrollIntoView = function () {};
  return Promise.resolve().then(() => callback(document.querySelector('#view'), store)).finally(() => {
    for (const [key, value] of Object.entries(old)) {
      if (value === undefined) delete globalThis[key]; else globalThis[key] = value;
    }
    dom.window.close();
  });
}

test('each of 99 lessons has a unique copy and 4 graded levels; legacy IDs stay stable', () => {
  const copies = new Set();
  const hardPrompts = new Set();
  let count = 0;
  for (let number = 1; number <= 22; number++) {
    for (const lesson of load(number).lessons) {
      count++;
      const [copy, easy, medium, hard, hardcore] = lesson.tasks;
      assert.equal(copy.id, `${lesson.id}-copy`);
      assert.equal(copy.type, 'copy');
      assert.equal(copy.sourceCode, lesson.theory.guide.example.code);
      assert.equal(copy.language, lesson.theory.guide.example.language);
      assert.ok(!copies.has(copy.sourceCode)); copies.add(copy.sourceCode);
      assert.equal(easy.id, `${lesson.id}-q1`);
      assert.equal(medium.id, `${lesson.id}-q2`);
      assert.deepEqual([easy.difficulty, medium.difficulty, hard.difficulty, hardcore.difficulty],
        ['easy', 'medium', 'hard', 'hardcore']);
      assert.ok(hard.prompt && !hardPrompts.has(hard.prompt), lesson.id); hardPrompts.add(hard.prompt);
      assert.ok(isCorrect(hard, hard.correct));
      assert.equal(hardcore.id, `${lesson.id}-hardcore`);
      assert.equal(hardcore.lessonId, lesson.id);
      assert.equal(hardcore.type, 'code');
      assert.equal(hardcore.optional, true);
      assert.equal(hardcore.mode, 'run');
      assert.ok(hardcore.prompt.length > 65 && hardcore.expectedOutput && hardcore.hint);
    }
  }
  assert.equal(count, 99);
  assert.deepEqual(XP_BY_DIFFICULTY, { easy: 10, medium: 20, hard: 35, hardcore: 90 });
  assert.equal(XP_BY_TYPE.copy, 8);
});

test('copying ignores harmless Java formatting but not changed names, string spaces or YAML indentation', () => {
  const java = moduleOne.lessons[0].tasks[0];
  assert.ok(isCorrect(java, java.sourceCode.replaceAll('    ', '\t').replaceAll('\n', '\r\n')));
  assert.ok(!isCorrect(java, java.sourceCode.replace('int age', 'int aged')));
  assert.ok(!isCorrect(java, java.sourceCode.replace('"Мира"', '"Мир а"')));
  assert.ok(validateInput(java, ''));
  assert.deepEqual(copyTokens('int count = 3;', 'java'), copyTokens('int\tcount=3;', 'java'));
  const yaml = load(22).lessons.at(-1).tasks[0];
  assert.equal(yaml.language, 'yaml');
  assert.ok(isCorrect(yaml, yaml.sourceCode));
  const indented = yaml.sourceCode.replace(/^  /m, '   ');
  assert.notEqual(indented, yaml.sourceCode);
  assert.equal(isCorrect(yaml, indented), false);
});

test('hardcore is optional, can be revisited alone, and skipping does not block the lesson', () => withDom(async (root) => {
  const activity = moduleOne.lessons[0];
  const state = freshState();
  let result;
  const entries = [activity.tasks[1], activity.tasks[4]].map(task => ({ task, review: false }));
  const props = { module: moduleOne, activity, mode: 'lesson', entries,
    health: { jdkAvailable: false, message: 'JDK не найден' },
    onAttempt: (task, correct, review) => recordAttempt(state, task, correct, { review, now: clock }),
    onComplete: (done) => { result = done; }, onExit: () => {} };
  let cleanup = renderSession(root, props);
  for (const id of entries[0].task.correct) root.querySelector(`input[value="${id}"]`).checked = true;
  root.querySelector('[data-primary]').click();
  root.querySelector('[data-primary]').click();
  assert.match(root.querySelector('.challenge-level').textContent, /ХАРД.*НЕОБЯЗАТЕЛЬНО/);
  assert.ok(root.querySelector('[data-skip]'));
  root.querySelector('[data-skip]').click();
  assert.equal(result.passed, true);
  assert.equal(result.total, 1);
  assert.equal(result.accuracy, 100);
  assert.equal(state.xp, 10);
  assert.equal(state.achievements.length, 0);
  cleanup();

  let exited = false;
  result = null;
  cleanup = renderSession(root, { ...props, entries: [entries[1]], optionalOnly: true,
    onExit: () => { exited = true; } });
  root.querySelector('[data-skip]').click();
  assert.equal(exited, true);
  assert.equal(result, null);
  cleanup();
  assert.deepEqual(parseRoute('#/mastery/1/m01-l01'),
    { name: 'mastery', moduleId: 1, lessonId: 'm01-l01' });
}));

test('copy task is presented beside the sample, preserves old drafts and rewards a successful rewrite', () => withDom(async (root, store) => {
  const activity = moduleOne.lessons[0];
  const task = activity.tasks[0];
  store.set('javada.draft.v1.m01-p06', 'my old solution');
  const state = freshState();
  let completed;
  const cleanup = renderSession(root, { module: moduleOne, activity, mode: 'lesson',
    entries: [{ task, review: false }], health: { jdkAvailable: false },
    onAttempt: (entry, correct, review) => recordAttempt(state, entry, correct, { review, now: clock }),
    onComplete: result => { completed = result; }, onExit: () => {} });
  assert.equal(root.querySelector('.copy-reference pre').textContent, task.sourceCode);
  assert.equal(root.querySelector('.code-textarea').value, '');
  root.querySelector('.code-textarea').value = task.sourceCode.replace('int age', 'int wrong');
  root.querySelector('[data-primary]').click();
  assert.ok(root.querySelector('.feedback-error'));
  root.querySelector('[data-primary]').click();
  root.querySelector('.code-textarea').value = task.sourceCode.replaceAll('    ', '\t');
  root.querySelector('[data-primary]').click();
  assert.ok(root.querySelector('.feedback-success'));
  assert.equal(state.xp, 8);
  root.querySelector('[data-primary]').click();
  assert.equal(completed.passed, true);
  assert.equal(readDraft('m01-p06'), 'my old solution');
  cleanup();
}));

test('legacy browser XP and drafts survive new rewards and reload without double credit', () => {
  const prior = globalThis.localStorage;
  const { localStorage } = withStorage();
  globalThis.localStorage = localStorage;
  try {
    localStorage.setItem('javada.state.v1', JSON.stringify({ version: 1, xp: 70,
      awardedTasks: ['m01-p06'], completedLessons: ['m01-l01'], theme: 'dark' }));
    localStorage.setItem('javada.draft.v1.m01-p07', 'old code');
    const state = loadState();
    assert.equal(state.legacyXp, 70);
    assert.equal(state.xp, 70);
    assert.deepEqual(state.legacyAwardedTasks, ['m01-p06']);
    recordAttempt(state, { id: 'm01-p06', type: 'code' }, true, { now: clock });
    assert.equal(state.xp, 70);
    recordAttempt(state, moduleOne.lessons[0].tasks[4], true, { now: clock });
    assert.equal(state.xp, 160);
    saveState(state);
    assert.equal(loadState().xp, 160);
    assert.equal(loadState().achievements[0], 'mastery:m01-l01');
    assert.equal(readDraft('m01-p07'), 'old code');
  } finally {
    if (prior === undefined) delete globalThis.localStorage; else globalThis.localStorage = prior;
  }
});

test('a timestamp tie never overwrites an older browser draft with different imported code', () => {
  const previous = globalThis.localStorage;
  const { localStorage } = withStorage();
  globalThis.localStorage = localStorage;
  try {
    localStorage.setItem('javada.draft.v1.m01-p06', 'my original browser code');
    restoreDrafts({ 'm01-p06': { code: 'another origin', updatedAt: 0 } });
    assert.equal(readDraft('m01-p06'), 'my original browser code');
    restoreDrafts({ 'm01-p06': { code: 'fresh IDE code', updatedAt: 42 } });
    assert.equal(readDraft('m01-p06'), 'fresh IDE code');
  } finally {
    if (previous === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = previous;
  }
});

test('an in-flight sync cannot overwrite a newer browser answer', async () => {
  const prev = { fetch: globalThis.fetch, localStorage: globalThis.localStorage };
  const { localStorage } = withStorage();
  globalThis.localStorage = localStorage;
  let resolveFirst;
  let calls = 0;
  globalThis.fetch = async () => {
    calls++;
    if (calls === 1) await new Promise(resolve => { resolveFirst = resolve; });
    return { ok: true, json: async () => ({ state: { ...freshState(), xp: 0 }, drafts: {} }) };
  };
  try {
    let state = freshState();
    const shared = createSharedProgress({ getState: () => state, applyState: next => { state = next; } });
    const request = shared.sync();
    // fetch is already waiting; the local answer wins until a fresh server
    // merge has incorporated it. No stale response may replace state.xp=10.
    state.xp = 10;
    shared.onPersist();
    resolveFirst();
    await request;
    assert.equal(state.xp, 10);
    // A later response can only be applied if no answer was made meanwhile.
    // Stop the retry timer by making the server unavailable in this test.
    globalThis.fetch = async () => ({ ok: false, status: 503,
      json: async () => ({ error: 'preview' }) });
    await new Promise(resolve => setTimeout(resolve, 500));
  } finally {
    for (const [key, value] of Object.entries(prev)) {
      if (value === undefined) delete globalThis[key]; else globalThis[key] = value;
    }
  }
});
