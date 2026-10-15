import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { freshState, loadState, readDraft, saveState } from '../../frontend/js/storage.js';
import { renderCareer, renderLab } from '../../frontend/js/ui/career.js';

// Optional browser-like smoke test: npm test still works without npm install.
let JSDOM;
try { ({ JSDOM } = await import('jsdom')); } catch { /* No optional DOM dependency. */ }

const labs = JSON.parse(readFileSync(new URL('../../frontend/data/labs.json', import.meta.url), 'utf8')).labs;

test('junior route, cases, saved evidence and disabled-preview UX', { skip: !JSDOM }, async () => {
  const dom = new JSDOM('<main id="view"></main>', { url: 'http://127.0.0.1:8000' });
  const prior = { window: globalThis.window, document: globalThis.document,
    localStorage: globalThis.localStorage, fetch: globalThis.fetch };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  globalThis.localStorage = dom.window.localStorage;
  try {
    const root = document.querySelector('#view');
    const state = freshState();
    let opened = null;
    renderCareer(root, { labs, state, health: { jdkAvailable: true },
      onOpenLab: id => { opened = id; },
      onChecklistChange: (id, checked) => { state.careerChecklist = checked ? [id] : []; saveState(state); } });
    assert.equal(root.querySelectorAll('.career-lab-card').length, 5);
    assert.equal(root.querySelectorAll('.career-project').length, 2);
    root.querySelector('[data-lab]').click();
    assert.equal(opened, 'lab-array-report');
    const check = root.querySelector('[data-evidence]');
    check.checked = true;
    check.dispatchEvent(new dom.window.Event('change', { bubbles: true }));
    assert.deepEqual(loadState().careerChecklist, ['cli-verify']);

    let answer = { success: false, kind: 'wrong_output', error: 'Ожидалось: <script>bad</script>', output: 'no' };
    globalThis.fetch = async () => ({ ok: true, json: async () => answer });
    const cleanup = renderLab(root, { lab: labs[0], state, health: { jdkAvailable: true }, onExit: () => {},
      onPassed: id => { state.completedLabs.push(id); saveState(state); } });
    assert.ok(root.querySelector('textarea').value.includes('class Main'));
    root.querySelector('[data-run]').click();
    await new Promise(resolve => setTimeout(resolve, 0));
    assert.equal(root.querySelector('.feedback-error script'), null); // Java stdout is always escaped.
    assert.match(root.querySelector('.feedback-detail').textContent, /<script>/);
    assert.deepEqual(loadState().completedLabs, []);
    answer = { success: true, kind: 'ok', error: '', output: 'Пройдено 4 из 4 проверок.' };
    root.querySelector('[data-run]').click();
    await new Promise(resolve => setTimeout(resolve, 0));
    assert.deepEqual(loadState().completedLabs, [labs[0].id]);
    assert.match(root.querySelector('.feedback-success').textContent, /Все входы пройдены/);
    cleanup();
    assert.ok(readDraft(labs[0].id).includes('class Main'));
    const disabled = renderLab(root, { lab: labs[1], state, health: { jdkAvailable: false,
      message: 'Запуск Java отключён в сетевом предпросмотре.' }, onPassed: () => {}, onExit: () => {} });
    assert.equal(root.querySelector('[data-run]').disabled, true);
    disabled();
  } finally {
    for (const [key, value] of Object.entries(prior)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
    dom.window.close();
  }
});
