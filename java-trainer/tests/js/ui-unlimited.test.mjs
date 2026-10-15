import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { recordAttempt } from '../../frontend/js/gamification.js';
import { freshState } from '../../frontend/js/storage.js';
import { courseStatuses } from '../../frontend/js/utils/course.js';
import { renderDashboard } from '../../frontend/js/ui/dashboard.js';
import { renderModule } from '../../frontend/js/ui/module.js';
import { renderResult } from '../../frontend/js/ui/success.js';
import { renderSession } from '../../frontend/js/ui/lesson.js';

let JSDOM;
try { ({ JSDOM } = await import('jsdom')); } catch { /* Optional browser UI tests. */ }
const index = JSON.parse(readFileSync(new URL('../../frontend/data/modules.json', import.meta.url), 'utf8'));
const moduleOne = JSON.parse(readFileSync(new URL('../../frontend/data/module-01.json', import.meta.url), 'utf8'));

test('course and retry buttons stay available despite a legacy zero-life value', { skip: !JSDOM }, () => {
  const dom = new JSDOM('<main id="view"></main>', { url: 'http://127.0.0.1:8000' });
  const prior = { window: globalThis.window, document: globalThis.document };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  try {
    const root = document.querySelector('#view');
    const state = { ...freshState(), hearts: 0 };
    const modules = courseStatuses(index.modules, state);
    let continued = false;
    renderDashboard(root, { modules, state, health: null, onContinue: () => { continued = true; },
      onOpenModule: () => {}, onReview: () => {} });
    assert.equal(root.querySelector('[data-action="continue"]').disabled, false);
    root.querySelector('[data-action="continue"]').click();
    assert.equal(continued, true);
    let opened;
    renderModule(root, { module: modules[0], state, onOpenStep: (...args) => { opened = args; }, onBack: () => {} });
    assert.equal(root.querySelector('[data-step="lesson"]').disabled, false);
    root.querySelector('[data-step="lesson"]').click();
    assert.deepEqual(opened, ['lesson', 'm01-l01']);
    let retried = false;
    renderResult(root, { result: { passed: false, earnedXp: 0, accuracy: 0, seconds: 10,
      correctFirstTry: 0, total: 5 }, onRetry: () => { retried = true; },
    onNext: () => {}, onMap: () => {} });
    assert.ok(root.querySelector('[data-retry]'));
    root.querySelector('[data-retry]').click();
    assert.equal(retried, true);
  } finally {
    for (const [key, value] of Object.entries(prior)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
    dom.window.close();
  }
});

test('a wrong lesson answer can be retried indefinitely, then earns XP once', { skip: !JSDOM }, async () => {
  const dom = new JSDOM('<main id="view"></main>', { url: 'http://127.0.0.1:8000' });
  const prior = { window: globalThis.window, document: globalThis.document };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  try {
    const root = document.querySelector('#view');
    const state = freshState();
    const activity = moduleOne.lessons[0];
    const task = activity.tasks.find(({ id }) => id === 'm01-l01-q1');
    let completed;
    const cleanup = renderSession(root, { module: moduleOne, activity, mode: 'lesson',
      entries: [{ task, review: false }], health: null,
      onAttempt: (entry, correct, review) => recordAttempt(state, entry, correct,
        { review, now: new Date(2026, 9, 14, 12) }),
      onComplete: (result) => { completed = result; }, onExit: () => {},
    });
    const button = root.querySelector('[data-primary]');
    root.querySelector('input[value="a"]').checked = true;
    for (let attempt = 1; attempt <= 8; attempt++) {
      button.click();
      await new Promise(resolve => setTimeout(resolve, 0));
      assert.ok(root.querySelector('.feedback-error'), `attempt ${attempt}`);
      assert.equal(state.attempts[task.id].mistakes, attempt);
      assert.match(button.textContent, /Попробовать снова/);
      button.click(); // leave feedback and try the same exercise again
      assert.equal(button.disabled, false);
    }
    root.querySelector('input[value="b"]').checked = true;
    button.click();
    await new Promise(resolve => setTimeout(resolve, 0));
    assert.ok(root.querySelector('.feedback-success'));
    assert.equal(state.xp, 10);
    button.click();
    assert.equal(completed.passed, true);
    assert.equal(completed.accuracy, 0);
    cleanup();
  } finally {
    for (const [key, value] of Object.entries(prior)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
    dom.window.close();
  }
});
