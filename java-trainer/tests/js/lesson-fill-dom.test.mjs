import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { renderSession } from '../../frontend/js/ui/lesson.js';
import { freshState } from '../../frontend/js/storage.js';
import { recordAttempt } from '../../frontend/js/gamification.js';

let JSDOM;
try { ({ JSDOM } = await import('jsdom')); } catch { /* Optional for the local UI test. */ }

const moduleOne = JSON.parse(readFileSync(new URL('../../frontend/data/module-01.json', import.meta.url), 'utf8'));
const moduleFourteen = JSON.parse(readFileSync(new URL('../../frontend/data/module-14.json', import.meta.url), 'utf8'));

test('first lesson teaches a complete Main, variable output and the literal console result before its quiz', { skip: !JSDOM }, () => {
  const dom = new JSDOM('<main id="view"></main>', { url: 'http://127.0.0.1:8000' });
  const previous = { window: globalThis.window, document: globalThis.document, localStorage: globalThis.localStorage };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  globalThis.localStorage = dom.window.localStorage;
  try {
    const root = document.querySelector('#view');
    const lesson = moduleOne.lessons[0];
    const cleanup = renderSession(root, {
      module: moduleOne, activity: lesson, mode: 'lesson',
      entries: [{ task: lesson.tasks[0], review: false }], health: null,
      onAttempt: () => ({ earnedXp: 0 }), onComplete: () => {}, onExit: () => {},
    });
    const guide = root.querySelector('.theory-worked');
    assert.ok(guide, 'the worked example is visible before the interactive task');
    assert.equal(guide.querySelectorAll('.theory-steps li').length, 4);
    const code = guide.querySelector('.theory-code code').textContent;
    for (const token of ['public class Main {', 'public static void main(String[] args)',
      'int age =', 'String name =', 'System.out.println(age);', 'System.out.println(name);']) {
      assert.ok(code.includes(token), token);
    }
    assert.equal(guide.querySelector('.theory-stdout').textContent, '16\nМира\ntrue');
    assert.match(guide.textContent, /println\("age"\)/);
    assert.equal(root.querySelectorAll('[data-jump]').length, 2,
      'navigation is possible before or after the long guide');
    cleanup();
  } finally {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
    dom.window.close();
  }
});

test('practice shows task-specific preparation and skips rereading it on the next question', { skip: !JSDOM }, async () => {
  const dom = new JSDOM('<main id="view"></main>', { url: 'http://127.0.0.1:8000' });
  const previous = { window: globalThis.window, document: globalThis.document };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  const scrolls = [];
  dom.window.HTMLElement.prototype.scrollIntoView = function () { scrolls.push(this.className); };
  try {
    const root = document.querySelector('#view');
    const practice = moduleOne.practice;
    const cleanup = renderSession(root, {
      module: moduleOne, activity: practice, mode: 'practice',
      entries: practice.tasks.slice(0, 2).map(task => ({ task, review: false })),
      health: null, onAttempt: () => ({ earnedXp: 0 }), onComplete: () => {}, onExit: () => {},
    });
    assert.equal(root.querySelector('.theory-worked .theory-stdout').textContent, 'Тетради\n21');
    assert.deepEqual([...root.querySelectorAll('.theory-transfer li')].map(el => el.dataset.practiceTask),
      ['m01-p06', 'm01-p07']);
    assert.deepEqual([...root.querySelectorAll('.theory-transfer li span')].map(el => el.textContent),
      ['ЗАДАНИЕ 06', 'ЗАДАНИЕ 07']);
    assert.match(root.querySelector('.theory-worked .theory-code').textContent, /System\.out\.println\(PRICE \* pieces\)/);
    root.querySelector('.theory-jump-last').click();
    assert.deepEqual(scrolls, ['challenge-card']);
    for (const id of practice.tasks[0].correct) root.querySelector(`input[value="${id}"]`).checked = true;
    root.querySelector('[data-primary]').click();
    await new Promise(resolve => setTimeout(resolve, 0));
    assert.ok(root.querySelector('.feedback-success'));
    root.querySelector('[data-primary]').click();
    assert.match(root.querySelector('.challenge-count').textContent, /ЗАДАНИЕ 02/);
    assert.deepEqual(scrolls, ['challenge-card', 'challenge-card'],
      'the next question starts at the task rather than at the top of the repeated primer');
    assert.ok(root.querySelector('.theory-worked'), 'the guidance remains available for reference');
    cleanup();
  } finally {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
    dom.window.close();
  }
});

test('int age = 3 passes the expanded lesson UI', { skip: !JSDOM }, async () => {
  const dom = new JSDOM('<main id="view"></main>', { url: 'http://127.0.0.1:8000' });
  const previous = { window: globalThis.window, document: globalThis.document };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  try {
    const root = document.querySelector('#view');
    const activity = moduleOne.lessons[0];
    const task = activity.tasks.find(({ id }) => id === 'm01-l01-q2');
    const state = freshState();
    let finished;
    const cleanup = renderSession(root, {
      module: moduleOne, activity, mode: 'lesson', entries: [{ task, review: false }], health: null,
      onAttempt: (entry, correct, review) => {
        return recordAttempt(state, entry, correct, { review, now: new Date('2026-10-14T12:00:00Z') });
      },
      onComplete: result => { finished = result; }, onExit: () => {},
    });
    root.querySelector('select[data-blank="type"]').value = 'int';
    root.querySelector('input[data-blank="value"]').value = '3';
    root.querySelector('[data-primary]').click();
    await new Promise(resolve => setTimeout(resolve, 0));
    assert.ok(root.querySelector('.feedback-success'), 'answer 3 should be accepted');
    assert.equal(root.querySelector('.feedback-error'), null);
    assert.ok(root.querySelector('.theory-expanded'), 'deeper explanation appears before the exercise');
    assert.match(root.querySelector('.theory-expanded').textContent, /РАЗОБРАТЬ ГЛУБЖЕ/);
    assert.match(root.querySelector('.theory-expanded').textContent, /ГДЕ ПРИМЕНИТЬ/);
    assert.equal(root.querySelector('.lesson-top-heart'), null);
    assert.equal(state.attempts[task.id].correct, true);
    root.querySelector('[data-primary]').click();
    assert.equal(finished.accuracy, 100);
    cleanup();
  } finally {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
    dom.window.close();
  }
});

test('correct Parsons order with zero indents gives actionable feedback, without recording a mistake', { skip: !JSDOM }, async () => {
  const dom = new JSDOM('<main id="view"></main>', { url: 'http://127.0.0.1:8000' });
  const previous = { window: globalThis.window, document: globalThis.document };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  try {
    const root = document.querySelector('#view');
    const activity = moduleOne.practice;
    const task = activity.tasks.find(({ type }) => type === 'parsons');
    const state = freshState();
    let attempts = 0;
    const cleanup = renderSession(root, {
      module: moduleOne, activity, mode: 'practice', entries: [{ task, review: false }], health: null,
      onAttempt: (entry, correct, review) => {
        attempts++;
        return recordAttempt(state, entry, correct, { review, now: new Date('2026-10-14T12:00:00Z') });
      },
      onComplete: () => {}, onExit: () => {},
    });
    const rows = () => [...root.querySelectorAll('.parsons-row')];
    for (let position = 0; position < task.solution.length; position++) {
      const id = task.solution[position].id;
      for (let current = rows().findIndex((row) => row.dataset.line === id); current > position; current--) {
        rows()[current].querySelector('[data-action="up"]').click();
      }
    }
    assert.deepEqual(rows().map((row) => row.dataset.line), task.solution.map((line) => line.id));
    assert.ok(rows().every((row) => row.querySelector('.indent-count').textContent === '0'));
    root.querySelector('[data-primary]').click();
    await new Promise((resolve) => setTimeout(resolve, 0));
    assert.match(root.querySelector('.feedback-notice').textContent, /Порядок строк верный/);
    assert.match(root.querySelector('.feedback-notice').textContent, /2 → 1, 3 → 2, 4 → 2, 5 → 1/);
    assert.equal(root.querySelectorAll('.parsons-row.indent-needs-work').length, 4);
    assert.equal(attempts, 0, 'indent guidance is not recorded as a mistake');
    for (const { id, indent } of task.solution) {
      for (let level = 0; level < indent; level++) {
        rows().find((row) => row.dataset.line === id).querySelector('[data-action="in"]').click();
        if (id === task.solution[1].id) {
          assert.equal(root.querySelectorAll('.parsons-row.indent-needs-work').length, 3,
            'fixed row is unmarked while the remaining rows stay highlighted');
        }
      }
    }
    assert.equal(root.querySelectorAll('.parsons-row.indent-needs-work').length, 0);
    root.querySelector('[data-primary]').click();
    await new Promise((resolve) => setTimeout(resolve, 0));
    assert.ok(root.querySelector('.feedback-success'));
    assert.equal(attempts, 1);
    cleanup();
  } finally {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
    dom.window.close();
  }
});

test('Builder setters can swap: alternate Parsons order and its indents stay accepted', { skip: !JSDOM }, async () => {
  const dom = new JSDOM('<main id="view"></main>', { url: 'http://127.0.0.1:8000' });
  const previous = { window: globalThis.window, document: globalThis.document };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  try {
    const root = document.querySelector('#view');
    const activity = moduleFourteen.practice;
    const task = activity.tasks.find((entry) => entry.id === 'm14-p04');
    const state = freshState();
    let attempts = 0;
    const cleanup = renderSession(root, {
      module: moduleFourteen, activity, mode: 'practice', entries: [{ task, review: false }],
      health: null, onAttempt: (entry, correct, review) => {
        attempts++;
        return recordAttempt(state, entry, correct, { review, now: new Date('2026-10-14T12:00:00Z') });
      }, onComplete: () => {}, onExit: () => {},
    });
    const rows = () => [...root.querySelectorAll('.parsons-row')];
    const order = task.alternativeOrders[0];
    for (let position = 0; position < order.length; position++) {
      const id = order[position];
      for (let current = rows().findIndex((row) => row.dataset.line === id); current > position; current--) {
        rows()[current].querySelector('[data-action="up"]').click();
      }
    }
    assert.deepEqual(rows().map((row) => row.dataset.line), order);
    root.querySelector('[data-primary]').click();
    await new Promise((resolve) => setTimeout(resolve, 0));
    assert.match(root.querySelector('.feedback-notice').textContent, /Порядок строк верный/);
    assert.equal(root.querySelectorAll('.parsons-row.indent-needs-work').length, 3);
    assert.equal(attempts, 0);
    for (const id of ['c', 'b', 'd']) {
      rows().find((row) => row.dataset.line === id).querySelector('[data-action="in"]').click();
    }
    assert.equal(root.querySelectorAll('.parsons-row.indent-needs-work').length, 0,
      'indent markers follow the alternative order, not canonical row positions');
    root.querySelector('[data-primary]').click();
    await new Promise((resolve) => setTimeout(resolve, 0));
    assert.ok(root.querySelector('.feedback-success'));
    assert.equal(attempts, 1);
    cleanup();
  } finally {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
    dom.window.close();
  }
});
