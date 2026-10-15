import { REVIEW_AFTER_MS, REVIEW_CHANCE } from './utils/constants.js';

export function isDue(history, now = new Date()) {
  if (!history?.lastAttempt) return false;
  const last = Date.parse(history.lastAttempt);
  return !history.correct || !!history.needsReview || !Number.isFinite(last)
    || now.getTime() - last >= REVIEW_AFTER_MS;
}

export async function dueQuestions(state, beforeModuleId, loadContent, now = new Date()) {
  const completedEarlier = state.completedPractices
    .filter((id) => Number.isInteger(id) && id < beforeModuleId)
    .sort((a, b) => a - b);
  const contents = await Promise.all(completedEarlier.map(async (id) => {
    try { return await loadContent(id); }
    catch (error) { console.warn(`Не удалось загрузить повторение модуля ${id}`, error); return null; }
  }));
  return contents.filter(Boolean).flatMap((content) =>
    [...content.lessons, content.practice].flatMap((activity) => activity.tasks))
    .filter((task) => task.type !== 'code' && task.type !== 'copy'
      && !task.optional && isDue(state.attempts[task.id], now));
}

export async function addSpacedReview(tasks, state, moduleId, loadContent, random = Math.random) {
  const entries = tasks.map((task) => ({ task, review: false }));
  if (random() >= REVIEW_CHANCE) return entries;
  const due = await dueQuestions(state, moduleId, loadContent);
  if (!due.length) return entries;
  const task = due[Math.floor(random() * due.length)];
  const at = Math.floor(random() * (entries.length + 1));
  entries.splice(at, 0, { task, review: true });
  return entries;
}
