// One place for game rules and UI labels. Changing these also changes the
// values shown in the lesson; existing earned XP is never recalculated.
// Course content can change without invalidating XP or saved drafts.
export const APP_VERSION = 1;
export const COURSE_VERSION = 9;
export const REVIEW_AFTER_MS = 7 * 24 * 60 * 60 * 1000;
export const REVIEW_CHANCE = 0.3;
export const XP_PER_LEVEL = 250;
export const XP_BY_TYPE = Object.freeze({
  theory: 5,
  choice: 10,
  fill: 15,
  matching: 20,
  parsons: 25,
  code: 50,
  copy: 8,
});
export const XP_BY_DIFFICULTY = Object.freeze({ easy: 10, medium: 20, hard: 35, hardcore: 90 });
export const REVIEW_XP = 5;
export const STREAK_MULTIPLIER = 1.1;
export const STREAK_MILESTONES = Object.freeze({ 7: 50, 30: 200 });

export const TASK_LABELS = Object.freeze({
  theory: 'Проверка теории',
  choice: 'Выберите ответ',
  fill: 'Заполните пропуски',
  matching: 'Найдите пары',
  parsons: 'Соберите код',
  code: 'Напишите Java-код',
  copy: 'Перепишите код вручную',
});
