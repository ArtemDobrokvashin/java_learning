import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { freshState, loadState, readCache, saveState, writeCache } from '../../frontend/js/storage.js';
import { APP_VERSION, COURSE_VERSION } from '../../frontend/js/utils/constants.js';
import { courseStatuses, canOpenStep, nextStep, moduleProgress } from '../../frontend/js/utils/course.js';
import { recordAttempt, syncState, levelInfo } from '../../frontend/js/gamification.js';
import { isCorrect, parsonsIndentIssues, validateInput } from '../../frontend/js/validator.js';
import { addSpacedReview, isDue } from '../../frontend/js/review.js';
import { parseRoute } from '../../frontend/js/router.js';
import { EVIDENCE } from '../../frontend/js/ui/career.js';

const dataDir = new URL('../../frontend/data/', import.meta.url);
const load = (filename) => JSON.parse(readFileSync(new URL(filename, dataDir), 'utf8'));
const index = load('modules.json');
const moduleOne = load('module-01.json');
const moduleTwo = load('module-02.json');
const moduleThree = load('module-03.json');
const labs = load('labs.json').labs;
const at = (day, hour = 12) => new Date(`2026-10-${String(day).padStart(2, '0')}T${String(hour).padStart(2, '0')}:00:00Z`);

test('all 22 modules have real lessons, five practice types and answerable tasks', () => {
  assert.equal(index.modules.length, 22);
  const allIds = new Set();
  let lessons = 0;
  let codeTasks = 0;
  const explanations = [];
  const guidedExamples = new Set();
  for (const metadata of index.modules) {
    const filename = `module-${String(metadata.id).padStart(2, '0')}.json`;
    assert.equal(metadata.contentStatus, 'ready', filename);
    assert.equal(metadata.contentFile, filename);
    const content = load(filename);
    assert.equal(content.id, metadata.id);
    assert.equal(content.title, metadata.title);
    assert.equal(content.lessons.length, metadata.lessons.length);
    if (metadata.id <= 3) assert.equal(content.lessons.length, 4);
    lessons += content.lessons.length;
    assert.equal(content.practice.id, metadata.practiceId);
    assert.equal(content.practice.tasks.length, 7);
    assert.deepEqual([...new Set(content.practice.tasks.map((task) => task.type))].sort(),
      ['choice', 'code', 'fill', 'matching', 'parsons']);
    const codePractice = content.practice.tasks.filter((task) => task.type === 'code');
    assert.equal(codePractice.length, 2);
    assert.deepEqual(content.practice.primer.hints.map((hint) => hint.taskId),
      codePractice.map((task) => task.id), filename);
    assert.ok(content.practice.primer.steps.length >= 2 && content.practice.primer.why.length >= 70);
    assert.ok(content.practice.primer.example.runnable && content.practice.primer.example.stdout);
    assert.match(content.practice.primer.example.code, /public class Main \{/);
    content.lessons.forEach((lesson, number) => {
      assert.equal(lesson.id, metadata.lessons[number].id);
      assert.equal(lesson.title, metadata.lessons[number].title);
      assert.ok(lesson.theory.paragraphs.length >= 2);
      assert.ok(lesson.theory.paragraphs.every((paragraph) => paragraph.length >= 75));
      assert.ok(lesson.theory.code);
      assert.ok(lesson.theory.objective?.length >= 35, lesson.id);
      assert.ok(lesson.theory.walkthrough?.length >= 65, lesson.id);
      assert.ok(lesson.theory.pitfall?.length >= 55, lesson.id);
      assert.ok(lesson.theory.deepDive?.length >= 90, lesson.id);
      assert.ok(lesson.theory.useCase?.length >= 90, lesson.id);
      const guide = lesson.theory.guide;
      assert.ok(guide.why.length >= 65 && guide.steps.length >= 2, lesson.id);
      assert.ok(guide.result && guide.bridge && guide.example.code && guide.example.label, lesson.id);
      assert.notEqual(guide.example.code, lesson.theory.code, lesson.id);
      assert.ok(!guidedExamples.has(guide.example.code), `a distinct second example for ${lesson.id}`);
      guidedExamples.add(guide.example.code);
      if (guide.example.runnable) assert.ok(guide.example.stdout, lesson.id);
      else assert.equal(guide.example.stdout, undefined, lesson.id);
      explanations.push(lesson.theory.deepDive, lesson.theory.useCase);
      assert.equal(lesson.tasks.length, 5);
      assert.equal(lesson.tasks[0].type, 'copy');
      assert.equal(lesson.tasks[1].id, `${lesson.id}-q1`);
      assert.equal(lesson.tasks[2].id, `${lesson.id}-q2`);
      assert.deepEqual(lesson.tasks.slice(3).map((task) => task.type), ['choice', 'code']);
    });
    if (metadata.boss) {
      assert.equal(content.boss.id, metadata.boss.id);
      assert.equal(content.boss.passingScore, 0.8);
      assert.equal(content.boss.tasks.length, 5);
      assert.ok(content.boss.tasks.every((task) => task.type === 'choice'));
    } else assert.equal(content.boss, undefined);
    const activities = [...content.lessons, content.practice, ...(content.boss ? [content.boss] : [])];
    for (const task of activities.flatMap((entry) => entry.tasks)) {
      assert.ok(!allIds.has(task.id), `${task.id} is unique`);
      allIds.add(task.id);
      assert.ok(task.prompt && task.hint && task.explanation, task.id);
      if (task.type === 'choice') {
        const choices = new Set(task.options.map((option) => option.id));
        assert.ok(task.correct.length >= 1 && task.correct.every((id) => choices.has(id)), task.id);
        assert.ok(isCorrect(task, task.correct), task.id);
      } else if (task.type === 'fill') {
        assert.equal(task.template.length, task.blanks.length + 1, task.id);
        for (const blank of task.blanks) {
          if (blank.kind === 'select') assert.ok(blank.options.includes(blank.answer), task.id);
        }
        assert.ok(isCorrect(task, Object.fromEntries(task.blanks.map((blank) => [blank.id, blank.answer]))), task.id);
      } else if (task.type === 'matching') {
        assert.ok(isCorrect(task, Object.fromEntries(task.pairs.map((pair) => [pair.id, pair.id]))), task.id);
      } else if (task.type === 'parsons') {
        assert.equal(task.lines.length, task.solution.length, task.id);
        assert.ok(isCorrect(task, task.solution), task.id);
      } else if (task.type === 'code') {
        codeTasks++;
        assert.equal(task.mode, 'run', task.id);
        assert.ok(task.starterCode.includes('class Main') && task.expectedOutput, task.id);
      } else if (task.type === 'copy') {
        assert.ok(isCorrect(task, task.sourceCode), task.id);
      } else assert.fail(`Unknown type: ${task.type}`);
    }
  }
  assert.equal(lessons, 99);
  assert.equal(new Set(explanations).size, 198, 'every lesson has its own explanations');
  assert.equal(guidedExamples.size, 99, 'each lesson has another concrete worked example');
  assert.equal(allIds.size, 679);
  assert.equal(codeTasks, 143);
});

test('five junior-path labs accept input and keep judging cases off the client', () => {
  assert.equal(labs.length, 5);
  assert.equal(new Set(labs.map((lab) => lab.id)).size, labs.length);
  assert.equal(EVIDENCE.length, 7);
  for (const lab of labs) {
    assert.equal(lab.type, 'code');
    assert.equal(lab.mode, 'run');
    assert.ok(lab.goal && lab.sampleInput && lab.sampleOutput && lab.starterCode);
    assert.ok(lab.prerequisites.every((id) => id >= 1 && id <= 22));
    assert.equal(validateInput(lab, lab.starterCode), null);
    assert.ok(!Object.hasOwn(lab, 'testCases') && !Object.hasOwn(lab, 'expectedOutput'));
  }
  assert.deepEqual(freshState().completedLabs, []);
  assert.deepEqual(freshState().careerChecklist, []);
});

test('all ready modules unlock in sequence; 17–22 require a boss pass', () => {
  const state = freshState();
  let modules = courseStatuses(index.modules, state);
  assert.deepEqual(modules.slice(0, 3).map((module) => module.status), ['available', 'locked', 'locked']);
  assert.equal(nextStep(modules[0], state).id, 'm01-l01');
  assert.ok(!canOpenStep(modules[0], 'practice', state));
  state.completedLessons = modules[0].lessons.map((lesson) => lesson.id);
  assert.ok(canOpenStep(modules[0], 'practice', state));
  assert.equal(nextStep(modules[0], state).type, 'practice');
  assert.equal(moduleProgress(modules[0], state).done, 4);
  state.completedPractices = [1, 2, 3];
  modules = courseStatuses(index.modules, state);
  assert.equal(modules[3].status, 'available');
  assert.equal(modules[4].status, 'locked');
  state.completedPractices = Array.from({ length: 17 }, (_, i) => i + 1);
  modules = courseStatuses(index.modules, state);
  assert.equal(modules[16].status, 'available');
  assert.equal(modules[17].status, 'locked');
  state.completedLessons.push(...modules[16].lessons.map((lesson) => lesson.id));
  assert.ok(canOpenStep(modules[16], 'boss', state));
  assert.ok(!canOpenStep(modules[16], 'practice', state));
  assert.equal(nextStep(modules[16], state).type, 'boss');
  state.bossScores[17] = { best: 80, passed: true };
  modules = courseStatuses(index.modules, state);
  assert.equal(modules[16].status, 'completed');
  assert.equal(modules[17].status, 'available');
  assert.ok(canOpenStep(modules[16], 'practice', state));
  state.completedPractices = Array.from({ length: 22 }, (_, i) => i + 1);
  for (let id = 18; id <= 22; id++) state.bossScores[id] = { best: 80, passed: true };
  assert.ok(courseStatuses(index.modules, state).every((module) => module.status === 'completed'));
});

test('boss requires 80% pass as well as practice to unlock next module', () => {
  const fakeModules = [
    { id: 1, contentStatus: 'ready', lessons: [], boss: { passingScore: 0.8 } },
    { id: 2, contentStatus: 'ready', lessons: [] },
  ];
  const state = freshState();
  state.completedPractices = [1];
  assert.equal(courseStatuses(fakeModules, state)[1].status, 'locked');
  state.bossScores[1] = { best: 60, passed: false };
  assert.equal(courseStatuses(fakeModules, state)[1].status, 'locked');
  state.bossScores[1] = { best: 80, passed: true };
  assert.equal(courseStatuses(fakeModules, state)[1].status, 'available');
});

test('mistakes can be repeated without a life counter or blocked progress', () => {
  const state = freshState();
  const task = { id: 'm01-l02-q1', type: 'choice' };
  for (let attempt = 1; attempt <= 20; attempt++) {
    assert.equal(recordAttempt(state, task, false, { now: at(1) }).earnedXp, 0);
    assert.equal(state.attempts[task.id].mistakes, attempt);
  }
  assert.equal(state.xp, 0);
  assert.equal(Object.hasOwn(state, 'hearts'), false);
  assert.equal(recordAttempt(state, task, true, { now: at(1) }).earnedXp, 10);
  assert.equal(state.attempts[task.id].correct, true);
  assert.equal(state.xp, 10);
});

test('XP is awarded once, streak gives 10%, milestone and review reward are bounded', () => {
  const state = freshState();
  const task = (id, type = 'choice') => ({ id, type });
  assert.equal(recordAttempt(state, task('one'), true, { now: at(1) }).earnedXp, 10);
  assert.equal(recordAttempt(state, task('one'), true, { now: at(1) }).earnedXp, 0);
  assert.equal(recordAttempt(state, task('two', 'fill'), true, { now: at(2) }).earnedXp, 17);
  assert.equal(state.streak, 2);
  for (let day = 3; day <= 6; day++) recordAttempt(state, task(`day${day}`), true, { now: at(day) });
  const seventh = recordAttempt(state, task('day7'), true, { now: at(7) });
  assert.equal(seventh.milestoneXp, 50);
  assert.equal(seventh.earnedXp, 61);
  assert.equal(recordAttempt(state, task('one'), true, { now: at(7), review: true }).earnedXp, 6);
  assert.equal(recordAttempt(state, task('one'), true, { now: at(7), review: true }).earnedXp, 0);
  syncState(state, at(9));
  assert.equal(state.streak, 0);
  assert.equal(recordAttempt(state, task('after-break'), true, { now: at(9) }).earnedXp, 10);
  assert.equal(state.streak, 1);
  assert.equal(levelInfo(260).level, 2);
});

test('mistakes are remembered for review even after correcting the answer', () => {
  const state = freshState();
  const task = { id: 'm01-l02-q1', type: 'choice' };
  assert.equal(recordAttempt(state, task, false, { now: at(1) }).earnedXp, 0);
  assert.equal(state.xp, 0);
  recordAttempt(state, task, true, { now: at(1) });
  assert.equal(state.attempts[task.id].correct, true);
  assert.equal(state.attempts[task.id].mistakes, 1);
  assert.equal(state.attempts[task.id].needsReview, true);
  recordAttempt(state, task, true, { now: at(2), review: true });
  assert.equal(state.attempts[task.id].needsReview, false);
});

test('validators check all question types; empty answers get guidance', () => {
  const practice = moduleOne.practice.tasks;
  const byType = (type) => practice.find((task) => task.type === type);
  const choice = byType('choice');
  assert.equal(validateInput(choice, []), 'Сначала выберите вариант ответа.');
  assert.ok(isCorrect(choice, choice.correct));
  assert.ok(!isCorrect(choice, choice.correct.slice(1)));
  const fill = byType('fill');
  const answer = Object.fromEntries(fill.blanks.map((blank) => [blank.id, blank.answer]));
  assert.equal(validateInput(fill, answer), null);
  assert.ok(isCorrect(fill, answer));
  const matching = byType('matching');
  const pairs = Object.fromEntries(matching.pairs.map((pair) => [pair.id, pair.id]));
  assert.ok(isCorrect(matching, pairs));
  const parsons = byType('parsons');
  assert.ok(isCorrect(parsons, parsons.solution));
  assert.ok(!isCorrect(parsons, [...parsons.solution].reverse()));
  const code = byType('code');
  assert.ok(validateInput(code, ''));
  assert.equal(validateInput(code, code.starterCode), null);
});

test('an age declaration accepts 3 and other valid values, not just 25 from the example', () => {
  const task = moduleOne.lessons[0].tasks.find(({ id }) => id === 'm01-l01-q2');
  assert.equal(task.id, 'm01-l01-q2');
  assert.deepEqual(task.blanks[1].validation, { kind: 'integerRange', min: 0, max: 120 });
  for (const value of ['0', '3', '25', '120', ' 3 ']) {
    assert.equal(validateInput(task, { type: 'int', value }), null);
    assert.ok(isCorrect(task, { type: 'int', value }), value);
  }
  for (const value of ['-1', '121', '3.0', '"3"', "'3'", '3L', '03', '3; System.exit(0)', '2147483648']) {
    assert.ok(!isCorrect(task, { type: 'int', value }), value);
  }
  assert.ok(!isCorrect(task, { type: 'String', value: '3' }), 'type choice is still required');
  assert.ok(!isCorrect(task, { type: 'int', value: '' }), 'empty answer is still invalid');
  assert.equal(validateInput(task, { type: 'int', value: '' }), 'Заполните все пропуски.');
  const exactFill = moduleTwo.lessons.find(({ tasks }) => tasks.some(({ type }) => type === 'fill'))
    .tasks.find(({ type }) => type === 'fill');
  const answer = Object.fromEntries(exactFill.blanks.map((blank) => [blank.id, blank.answer]));
  assert.ok(isCorrect(exactFill, answer));
  assert.ok(!isCorrect(exactFill, Object.fromEntries(Object.keys(answer).map((id) => [id, '3']))),
    'other fill questions must still use exact answers');
});

test('Parsons checker distinguishes correct ordering from missing indentation', () => {
  const task = moduleOne.practice.tasks.find((entry) => entry.type === 'parsons');
  const screenshot = task.solution.map(({ id }) => ({ id, indent: 0 }));
  assert.equal(isCorrect(task, screenshot), false);
  assert.deepEqual(parsonsIndentIssues(task, screenshot).map(({ row, current, expected }) =>
    [row, current, expected]), [[2, 0, 1], [3, 0, 2], [4, 0, 2], [5, 0, 1]]);
  assert.equal(parsonsIndentIssues(task, [...screenshot].reverse()), null);
  assert.deepEqual(parsonsIndentIssues(task, task.solution), []);
  assert.equal(isCorrect(task, task.solution), true);
});

test('identical closing braces in Parsons tasks can exchange places', () => {
  const task = moduleOne.practice.tasks.find((entry) => entry.type === 'parsons');
  const braces = task.solution.filter((line) => task.lines.find((item) => item.id === line.id).text === '}');
  assert.equal(braces.length, 2);
  const swapped = task.solution.map((line) => {
    if (line.id === braces[0].id) return { ...line, id: braces[1].id };
    if (line.id === braces[1].id) return { ...line, id: braces[0].id };
    return line;
  });
  assert.ok(isCorrect(task, swapped), 'visually identical result is accepted');
  assert.ok(!isCorrect(task, swapped.map((line) => ({ ...line, indent: 0 }))));
});

test('new course cache never replaces existing learner progress or drafts', () => {
  const original = globalThis.localStorage;
  const store = new Map();
  globalThis.localStorage = {
    getItem: (key) => store.get(key) ?? null,
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key),
    key: (index) => [...store.keys()][index] ?? null,
    get length() { return store.size; },
  };
  try {
    assert.equal(APP_VERSION, 1);
    assert.equal(COURSE_VERSION, 8);
    const state = { ...freshState(), xp: 250, completedPractices: [1, 2, 3] };
    localStorage.setItem('javada.state.v1', JSON.stringify(state));
    localStorage.setItem('javada.draft.v1.m01-p06', 'my code');
    localStorage.setItem('javada.cache.v5.index', JSON.stringify({ version: 5, savedAt: Date.now(), data: ['old answer keys'] }));
    localStorage.setItem('javada.cache.v5.module-1', JSON.stringify({ version: 5, savedAt: Date.now(), data: moduleOne }));
    assert.deepEqual(loadState().completedPractices, [1, 2, 3]);
    assert.equal(loadState().xp, 250);
    assert.deepEqual(loadState().completedLabs, []); // old v1 saves migrate safely
    assert.deepEqual(loadState().careerChecklist, []);
    assert.equal(readCache('index'), null);
    assert.equal(readCache('module-1'), null); // outdated answer keys cannot stay cached
    writeCache('index', index.modules);
    assert.equal(readCache('index').length, 22);
    assert.equal(localStorage.getItem('javada.draft.v1.m01-p06'), 'my code');
  } finally {
    if (original === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = original;
  }
});

test('old saves drop unused life bookkeeping without losing XP, attempts or completed steps', () => {
  const original = globalThis.localStorage;
  const store = new Map();
  globalThis.localStorage = {
    getItem: (key) => store.get(key) ?? null,
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: (key) => store.delete(key),
  };
  try {
    const previous = { ...freshState(), xp: 150, streak: 3, lastActiveDate: '2026-10-14',
      hearts: 0, nextHeartAt: Date.now() + 1_800_000, creditedFixes: ['age-range-v4'],
      attempts: { 'm01-l01-q2': { mistakes: 2, needsReview: true } },
      completedLessons: ['m01-l01'], completedPractices: [1], completedLabs: ['lab-array-report'],
      careerChecklist: ['readme'], bossScores: { 17: { best: 80, passed: true } },
      awardedTasks: ['m01-l01-q1'], reviewRewards: { 'm01-l01-q2': '2026-10-14' }, theme: 'dark' };
    localStorage.setItem('javada.state.v1', JSON.stringify(previous));
    const migrated = loadState();
    for (const key of ['hearts', 'nextHeartAt', 'creditedFixes']) assert.ok(!Object.hasOwn(migrated, key));
    assert.equal(migrated.xp, 150);
    assert.equal(migrated.streak, 3);
    assert.equal(migrated.lastActiveDate, '2026-10-14');
    assert.deepEqual(migrated.attempts, previous.attempts);
    for (const key of ['completedLessons', 'completedPractices', 'completedLabs', 'careerChecklist',
      'bossScores', 'awardedTasks', 'reviewRewards', 'theme']) assert.deepEqual(migrated[key], previous[key], key);
    assert.equal(migrated.remindersEnabled, false, 'old users are not opted in');
    assert.equal(migrated.reminderTime, '19:00');
    saveState({ ...migrated, hearts: 5, nextHeartAt: 1, creditedFixes: ['old'] });
    const persisted = JSON.parse(localStorage.getItem('javada.state.v1'));
    for (const key of ['hearts', 'nextHeartAt', 'creditedFixes']) assert.ok(!Object.hasOwn(persisted, key));
    assert.deepEqual(loadState(), migrated, 'migration is stable across reloads');
  } finally {
    if (original === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = original;
  }
});

test('review picks an erroneous older-module question with 30% chance', async () => {
  const state = freshState();
  state.completedPractices = [1];
  state.attempts['m01-l01-q1'] = { correct: true, needsReview: true, lastAttempt: at(1).toISOString() };
  assert.ok(isDue(state.attempts['m01-l01-q1'], at(2)));
  assert.ok(!isDue({ correct: true, lastAttempt: at(2).toISOString() }, at(2)));
  assert.ok(isDue({ correct: true, lastAttempt: at(1).toISOString() }, at(8)));
  const questions = await addSpacedReview(moduleTwo.lessons[0].tasks, state, 2,
    async () => moduleOne, () => 0.1);
  assert.equal(questions.length, moduleTwo.lessons[0].tasks.length + 1);
  assert.equal(questions.filter((entry) => entry.review).length, 1);
  assert.equal(questions.find((entry) => entry.review).task.id, 'm01-l01-q1');
});

test('hash routes parse predictable, refresh-safe paths', () => {
  assert.equal(parseRoute('#/').name, 'dashboard');
  assert.deepEqual(parseRoute('#/lesson/2/m02-l03'), { name: 'lesson', moduleId: 2, lessonId: 'm02-l03' });
  assert.equal(parseRoute('#/lesson/2/nope').name, 'not-found');
  assert.equal(parseRoute('#/practice/3').moduleId, 3);
  assert.equal(parseRoute('#/career').name, 'career');
  assert.deepEqual(parseRoute('#/lab/lab-array-report'), { name: 'lab', labId: 'lab-array-report' });
  assert.equal(parseRoute('#/lab/../../private').name, 'not-found');
});
