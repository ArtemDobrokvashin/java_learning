import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { getLanguage, setLanguage, SUPPORTED_LANGUAGES, tr } from '../../frontend/js/i18n.js';
import { isCorrect } from '../../frontend/js/validator.js';

const i18nRoot = new URL('../../frontend/data/i18n/', import.meta.url);
const loadLocalized = (lang, filename) =>
  JSON.parse(readFileSync(new URL(`${lang}/${filename}`, i18nRoot), 'utf8'));

test('i18n switches between all 5 languages (ru, en, es, de, zh) and translates UI strings', () => {
  try {
    assert.deepEqual(SUPPORTED_LANGUAGES.map((l) => l.code), ['ru', 'en', 'es', 'de', 'zh']);
    setLanguage('ru');
    assert.equal(getLanguage(), 'ru');
    assert.equal(tr('Карта курса'), 'Карта курса');

    setLanguage('en');
    assert.equal(getLanguage(), 'en');
    assert.equal(tr('Карта курса'), 'Course Map');

    setLanguage('es');
    assert.equal(tr('Карта курса'), 'Mapa del curso');

    setLanguage('de');
    assert.equal(tr('Карта курса'), 'Kurskarte');

    setLanguage('zh');
    assert.equal(tr('Карта курса'), '课程地图');
  } finally {
    setLanguage('ru');
  }
});

test('all 4 localized course bundles (en, es, de, zh) preserve 22 modules, 99 lessons, 5 labs and valid answer keys', () => {
  for (const lang of ['en', 'es', 'de', 'zh']) {
    const index = loadLocalized(lang, 'modules.json');
    const labs = loadLocalized(lang, 'labs.json').labs;
    assert.equal(index.modules.length, 22, `${lang} modules count`);
    assert.equal(labs.length, 5, `${lang} labs count`);

    let totalLessons = 0;
    let totalTasks = 0;
    for (const meta of index.modules) {
      const mod = loadLocalized(lang, meta.contentFile);
      assert.equal(mod.id, meta.id);
      assert.equal(mod.title, meta.title);
      totalLessons += mod.lessons.length;

      const activities = [...mod.lessons, mod.practice, ...(mod.boss ? [mod.boss] : [])];
      for (const task of activities.flatMap((a) => a.tasks)) {
        totalTasks++;
        if (task.type === 'choice') {
          assert.ok(isCorrect(task, task.correct), `${lang}:${task.id}`);
        } else if (task.type === 'fill') {
          const ans = Object.fromEntries(task.blanks.map((b) => [b.id, b.answer]));
          assert.ok(isCorrect(task, ans), `${lang}:${task.id}`);
        } else if (task.type === 'matching') {
          const ans = Object.fromEntries(task.pairs.map((p) => [p.id, p.id]));
          assert.ok(isCorrect(task, ans), `${lang}:${task.id}`);
        } else if (task.type === 'parsons') {
          assert.ok(isCorrect(task, task.solution), `${lang}:${task.id}`);
        } else if (task.type === 'copy') {
          assert.ok(isCorrect(task, task.sourceCode), `${lang}:${task.id}`);
        }
      }
    }
    assert.equal(totalLessons, 99, `${lang} lessons count`);
    assert.equal(totalTasks, 679, `${lang} tasks count`);
  }
});
