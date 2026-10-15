import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { isCorrect, parsonsIndentIssues, validateInput } from '../../frontend/js/validator.js';

const dataDir = new URL('../../frontend/data/', import.meta.url);
const load = (filename) => JSON.parse(readFileSync(new URL(filename, dataDir), 'utf8'));
const tasks = Array.from({ length: 22 }, (_, index) => load(`module-${String(index + 1).padStart(2, '0')}.json`))
  .flatMap((module) => [...module.lessons, module.practice, ...(module.boss ? [module.boss] : [])]
    .flatMap((activity) => activity.tasks));
const byId = (id) => tasks.find((task) => task.id === id);
const sameSet = (left, right) => left.length === right.length && left.every((id) => right.includes(id));

// Check not just that the stored answer passes, but that the UI rejects each
// wrong choice, cloze option, mismatched pair and incorrect line arrangement.
test('all 679 course tasks have coherent keys, usable options and constrained validators', () => {
  assert.equal(tasks.length, 679);
  assert.equal(new Set(tasks.map((task) => task.id)).size, tasks.length);
  const counts = { choice: 0, fill: 0, matching: 0, parsons: 0, code: 0, copy: 0 };
  for (const task of tasks) {
    counts[task.type]++;
    assert.ok(task.prompt && task.hint && task.explanation, task.id);
    if (task.type === 'choice') {
      assert.equal(task.options.length, 4, task.id);
      const ids = task.options.map((option) => option.id);
      assert.equal(new Set(ids).size, ids.length, task.id);
      assert.equal(new Set(task.options.map((option) => option.label)).size, ids.length, task.id);
      assert.ok(task.correct.every((id) => ids.includes(id)), task.id);
      assert.equal(task.multiple, task.correct.length > 1, task.id);
      for (let mask = 1; mask < 2 ** ids.length; mask++) {
        const selected = ids.filter((_, index) => mask & (1 << index));
        assert.equal(isCorrect(task, selected), sameSet(selected, task.correct), `${task.id}: ${selected}`);
      }
      assert.ok(validateInput(task, []), task.id);
    } else if (task.type === 'fill') {
      assert.equal(task.template.length, task.blanks.length + 1, task.id);
      assert.equal(new Set(task.blanks.map((blank) => blank.id)).size, task.blanks.length, task.id);
      const answer = Object.fromEntries(task.blanks.map((blank) => [blank.id, blank.answer]));
      assert.ok(isCorrect(task, answer), task.id);
      assert.ok(validateInput(task, {}), task.id);
      for (const blank of task.blanks) {
        if (blank.kind === 'select') {
          assert.ok(blank.options.includes(blank.answer), task.id);
          assert.equal(new Set(blank.options).size, blank.options.length, task.id);
          for (const option of blank.options) {
            if (option !== blank.answer && !blank.accepted?.includes(option)) {
              assert.equal(isCorrect(task, { ...answer, [blank.id]: option }), false, `${task.id}: ${option}`);
            }
          }
        } else assert.equal(blank.kind, 'text', task.id);
      }
    } else if (task.type === 'matching') {
      const ids = task.pairs.map((pair) => pair.id);
      assert.equal(new Set(ids).size, ids.length, task.id);
      assert.equal(new Set(task.pairs.map((pair) => pair.left)).size, ids.length, task.id);
      assert.equal(new Set(task.pairs.map((pair) => pair.right)).size, ids.length, task.id);
      const answer = Object.fromEntries(ids.map((id) => [id, id]));
      assert.ok(isCorrect(task, answer), task.id);
      for (let i = 0; i < ids.length; i++) {
        assert.equal(isCorrect(task, { ...answer, [ids[i]]: ids[(i + 1) % ids.length] }), false, task.id);
      }
    } else if (task.type === 'parsons') {
      const ids = task.lines.map((line) => line.id);
      assert.ok(ids.length >= 3 && ids.length <= 12, task.id);
      assert.equal(new Set(ids).size, ids.length, task.id);
      assert.ok(task.lines.every((line) => line.text), task.id);
      assert.equal(task.solution.length, ids.length, task.id);
      assert.ok(sameSet(task.solution.map((line) => line.id), ids), task.id);
      assert.ok(task.solution.every(({ indent }) => Number.isInteger(indent) && indent >= 0 && indent <= 3), task.id);
      const indentById = new Map(task.solution.map(({ id, indent }) => [id, indent]));
      const canonical = task.solution.map(({ id }) => id);
      const orders = [canonical, ...(task.alternativeOrders || [])];
      assert.equal(new Set(orders.map((order) => order.join(','))).size, orders.length, task.id);
      for (const order of orders) {
        assert.ok(sameSet(order, ids) && new Set(order).size === ids.length, task.id);
        const answer = order.map((id) => ({ id, indent: indentById.get(id) }));
        assert.deepEqual(parsonsIndentIssues(task, answer), [], `${task.id}: ${order}`);
        assert.ok(isCorrect(task, answer), `${task.id}: ${order}`);
      }
      assert.equal(isCorrect(task, task.solution.map(({ id }) => ({ id, indent: 4 }))), false, task.id);
      assert.equal(isCorrect(task, [{ ...task.solution[0] }, ...task.solution.slice(0, -1)]), false, task.id);
      assert.equal(parsonsIndentIssues(task, [...task.solution].reverse()), null, task.id);
    } else if (task.type === 'code') {
      assert.equal(task.mode, 'run', task.id);
      assert.ok(task.starterCode.includes('class Main'), task.id);
      assert.ok(typeof task.expectedOutput === 'string' && task.expectedOutput.length > 0, task.id);
      assert.equal(validateInput(task, task.starterCode), null, task.id);
    } else if (task.type === 'copy') {
      assert.ok(task.sourceCode && task.language, task.id);
      assert.equal(validateInput(task, task.sourceCode), null, task.id);
      assert.ok(isCorrect(task, task.sourceCode), task.id);
      assert.ok(!isCorrect(task, task.sourceCode + '\nошибка'), task.id);
    } else assert.fail(`Unknown task type: ${task.id} (${task.type})`);
  }
  assert.deepEqual(counts, { choice: 272, fill: 116, matching: 25, parsons: 24, code: 143, copy: 99 });
});

test('legitimate Java member, Builder and all 24 GitLab YAML permutations are accepted', () => {
  for (const id of ['m05-p04', 'm14-p04', 'm17-p04', 'm18-p04']) {
    const task = byId(id);
    assert.equal(task.alternativeOrders.length, 1, id);
    const indentById = new Map(task.solution.map(({ id: lineId, indent }) => [lineId, indent]));
    const variant = task.alternativeOrders[0].map((lineId) => ({ id: lineId, indent: indentById.get(lineId) }));
    assert.ok(isCorrect(task, variant), id);
    assert.ok(parsonsIndentIssues(task, variant.map((line) => ({ ...line, indent: 0 })))?.length, id);
  }
  const builder = byId('m14-p04');
  assert.equal(isCorrect(builder, ['d', 'b', 'c', 'a'].map((id) => ({ id, indent: id === 'a' ? 0 : 1 }))), false);
  const pipeline = byId('m22-p04');
  assert.equal(pipeline.alternativeOrders.length, 23);
  assert.ok(pipeline.alternativeOrders.some((order) => order.join('') === 'egfbdca'),
    'jobs, fields and stages can be reordered without changing GitLab execution order');
  const indentById = new Map(pipeline.solution.map(({ id, indent }) => [id, indent]));
  const toRows = (order) => [...order].map((id) => ({ id, indent: indentById.get(id) }));
  assert.ok(isCorrect(pipeline, toRows('egfbdca')));
  assert.equal(isCorrect(pipeline, toRows('abefgcd')), false, 'a job body cannot be split by another job');
  assert.equal(parsonsIndentIssues(pipeline, toRows('egfbdca').map((line) => ({ ...line, indent: 0 })))?.length, 4,
    'the lesson should guide indentation even for a different valid YAML order');
});

test('explicitly authored text equivalents are accepted without weakening other blanks', () => {
  const task = { type: 'fill', blanks: [
    { id: 'type', kind: 'text', answer: 'String', accepted: ['String', 'java.lang.String'] },
    { id: 'suffix', kind: 'select', answer: ';', options: [';', ':'] },
  ] };
  assert.ok(isCorrect(task, { type: 'String', suffix: ';' }));
  assert.ok(isCorrect(task, { type: 'java.lang.String', suffix: ';' }));
  assert.equal(isCorrect(task, { type: 'String', suffix: ':' }), false);
  assert.equal(isCorrect(task, { type: 'Object', suffix: ';' }), false);
});

test('audited prompts and terminology do not promise another answer', () => {
  assert.match(byId('m02-p02').prompt, /score равен 100/);
  assert.match(byId('m05-l02-q1').options[0].label, /связь объектов/i);
  assert.match(byId('m06-l02-q1').prompt, /Java 17/);
  assert.match(byId('m04-p03').pairs[0].right, /Переменная в объявлении/);
  const collections = byId('m09-p03').pairs;
  assert.match(collections[2].right, /значения могут повторяться/);
  assert.match(collections[3].right, /Хеш-реализация/);
  assert.match(byId('m10-l04-q2').template[0], /StandardOpenOption.CREATE/);
  assert.match(byId('m16-l02-q2').prompt, /первой/);
  const age = byId('m01-l01-q2');
  for (const value of ['0', '3', '120']) assert.ok(isCorrect(age, { type: 'int', value }));
  assert.ok(!isCorrect(age, { type: 'int', value: '121' }));
});

test('old life UI and gating are absent from screens, styling and game rules', () => {
  for (const filename of ['app.js', 'gamification.js', 'utils/constants.js', 'ui/lesson.js',
    'ui/dashboard.js', 'ui/module.js', 'ui/success.js', 'ui/career.js']) {
    const source = readFileSync(new URL(`../../frontend/js/${filename}`, import.meta.url), 'utf8');
    assert.doesNotMatch(source, /hearts?|сердеч|nextHeartAt|loseHeart/i, filename);
  }
  for (const filename of ['main.css', 'components.css']) {
    const source = readFileSync(new URL(`../../frontend/css/${filename}`, import.meta.url), 'utf8');
    assert.doesNotMatch(source, /hearts?|сердеч/i, filename);
  }
  assert.equal(existsSync(new URL('../../frontend/js/ui/hearts.js', import.meta.url)), false);
});
