import { APP_VERSION, COURSE_VERSION } from './utils/constants.js';
import { isValidReminderTime } from './reminders.js';

const STATE_KEY = `javada.state.v${APP_VERSION}`;
const CACHE_PREFIX = `javada.cache.v${COURSE_VERSION}.`;
const DRAFT_PREFIX = `javada.draft.v${APP_VERSION}.`;
const DRAFT_TIME_PREFIX = `javada.draft-time.v${APP_VERSION}.`;
const CACHE_MAX_AGE_MS = 60 * 60 * 1000;
const memoryFallback = new Map();
let persistenceListener = null;

export function setPersistenceListener(listener) { persistenceListener = listener; }

function read(key) {
  try { return localStorage.getItem(key); }
  catch (error) { console.warn('localStorage недоступен; данные останутся только в этой вкладке.', error); return memoryFallback.get(key) ?? null; }
}

function write(key, value) {
  try { localStorage.setItem(key, value); }
  catch (error) { console.warn('Не удалось сохранить прогресс.', error); memoryFallback.set(key, value); }
}

export function freshState() {
  return {
    version: APP_VERSION,
    xp: 0,
    legacyXp: 0,
    xpLedger: {},
    legacyAwardedTasks: [],
    achievements: [],
    streak: 0,
    lastActiveDate: null,
    milestones: [],
    completedLessons: [],
    completedPractices: [],
    completedLabs: [],
    careerChecklist: [],
    bossScores: {},
    attempts: {},
    awardedTasks: [],
    reviewRewards: {},
    theme: 'light',
    remindersEnabled: false,
    reminderTime: '19:00',
    lastReminderDay: null,
  };
}

function cleanState(saved) {
  const defaults = freshState();
  // A pre-plugin v1 save has no ledger: its XP becomes an immutable baseline.
  // New rewards have unique event IDs, so IDE and browser awards merge once.
  const xpLedger = Object.fromEntries(Object.entries(saved.xpLedger && typeof saved.xpLedger === 'object'
    ? saved.xpLedger : {}).filter(([id, value]) => /^[\w:.-]{1,100}$/.test(id)
      && Number.isSafeInteger(value) && value >= 0 && value <= 1000));
  const legacyAwardedTasks = Array.isArray(saved.legacyAwardedTasks) ? saved.legacyAwardedTasks
    : Array.isArray(saved.awardedTasks) ? saved.awardedTasks : [];
  const tracked = Object.entries(xpLedger).reduce((sum, [key, amount]) =>
    sum + (key.startsWith('task:') && legacyAwardedTasks.includes(key.slice(5)) ? 0 : amount), 0);
  let legacyXp = Number.isSafeInteger(saved.legacyXp) && saved.legacyXp >= 0
    ? saved.legacyXp : Math.max(0, Number(saved.xp) || 0);
  if (Number(saved.xp) > legacyXp + tracked) legacyXp = Number(saved.xp) - tracked;
  return {
    ...defaults,
    xp: legacyXp + tracked,
    legacyXp,
    xpLedger,
    legacyAwardedTasks: [...new Set(legacyAwardedTasks)],
    achievements: Array.isArray(saved.achievements) ? [...new Set(saved.achievements.filter(
      (id) => typeof id === 'string' && /^mastery:m\d{2}-l\d{2}$/.test(id)))] : [],
    streak: Math.max(0, Number(saved.streak) || 0),
    lastActiveDate: typeof saved.lastActiveDate === 'string' ? saved.lastActiveDate : null,
    milestones: Array.isArray(saved.milestones) ? saved.milestones : [],
    completedLessons: Array.isArray(saved.completedLessons) ? saved.completedLessons : [],
    completedPractices: Array.isArray(saved.completedPractices) ? saved.completedPractices : [],
    completedLabs: Array.isArray(saved.completedLabs) ? saved.completedLabs : [],
    careerChecklist: Array.isArray(saved.careerChecklist) ? saved.careerChecklist : [],
    bossScores: saved.bossScores && typeof saved.bossScores === 'object' ? saved.bossScores : {},
    attempts: saved.attempts && typeof saved.attempts === 'object' ? saved.attempts : {},
    awardedTasks: Array.isArray(saved.awardedTasks) ? saved.awardedTasks : [],
    reviewRewards: saved.reviewRewards && typeof saved.reviewRewards === 'object' ? saved.reviewRewards : {},
    theme: saved.theme === 'dark' ? 'dark' : 'light',
    remindersEnabled: saved.remindersEnabled === true,
    reminderTime: isValidReminderTime(saved.reminderTime) ? saved.reminderTime : defaults.reminderTime,
    lastReminderDay: typeof saved.lastReminderDay === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(saved.lastReminderDay)
      ? saved.lastReminderDay : null,
  };
}

export function loadState() {
  try {
    const saved = JSON.parse(read(STATE_KEY));
    if (!saved || saved.version !== APP_VERSION || typeof saved !== 'object') return freshState();
    return cleanState(saved);
  } catch (error) {
    console.warn('Не удалось прочитать сохранение. Начинаем заново.', error);
    return freshState();
  }
}

export function saveState(state, { notify = true } = {}) {
  write(STATE_KEY, JSON.stringify(cleanState(state)));
  if (notify) persistenceListener?.();
}

export function resetState() {
  try {
    localStorage.removeItem(STATE_KEY);
    // Course files remain cached, but submitted drafts are personal progress.
    const keys = Array.from({ length: localStorage.length }, (_, index) => localStorage.key(index));
    for (const key of keys) {
      if (key?.startsWith(DRAFT_PREFIX) || key?.startsWith(DRAFT_TIME_PREFIX)) localStorage.removeItem(key);
    }
  } catch {
    memoryFallback.delete(STATE_KEY);
    for (const key of memoryFallback.keys()) {
      if (key.startsWith(DRAFT_PREFIX) || key.startsWith(DRAFT_TIME_PREFIX)) memoryFallback.delete(key);
    }
  }
  return freshState();
}

export function readCache(key, { allowStale = false } = {}) {
  try {
    const cached = JSON.parse(read(CACHE_PREFIX + key));
    if (cached?.version !== COURSE_VERSION || !cached?.data) return null;
    if (!allowStale && Date.now() - cached.savedAt > CACHE_MAX_AGE_MS) return null;
    return cached.data;
  } catch { return null; }
}

export function writeCache(key, data) {
  write(CACHE_PREFIX + key, JSON.stringify({ version: COURSE_VERSION, savedAt: Date.now(), data }));
}

export function readDraft(taskId) {
  return read(DRAFT_PREFIX + taskId);
}

export function saveDraft(taskId, code) {
  write(DRAFT_PREFIX + taskId, code);
  write(DRAFT_TIME_PREFIX + taskId, String(Date.now()));
  persistenceListener?.();
}

export function clearDraft(taskId) {
  try {
    localStorage.removeItem(DRAFT_PREFIX + taskId);
    localStorage.removeItem(DRAFT_TIME_PREFIX + taskId);
  } catch {
    memoryFallback.delete(DRAFT_PREFIX + taskId);
    memoryFallback.delete(DRAFT_TIME_PREFIX + taskId);
  }
  // A tombstone in the shared profile prevents a stale browser tab from
  // resurrecting a successfully completed task's draft.
  persistenceListener?.(taskId);
}

export function listDrafts() {
  const result = {};
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (!key?.startsWith(DRAFT_PREFIX)) continue;
      const id = key.slice(DRAFT_PREFIX.length);
      if (!/^[a-zA-Z0-9][a-zA-Z0-9:_.-]{0,99}$/.test(id)) continue;
      const code = read(key);
      if (typeof code === 'string' && new TextEncoder().encode(code).length <= 16_384) {
        result[id] = { code, updatedAt: Number(read(DRAFT_TIME_PREFIX + id)) || 0 };
      }
    }
  } catch { /* localStorage blocked: memory fallback remains local */ }
  return result;
}

export function restoreDrafts(drafts) {
  for (const [id, draft] of Object.entries(drafts || {})) {
    if (!/^[a-zA-Z0-9][a-zA-Z0-9:_.-]{0,99}$/.test(id) || !draft || typeof draft.code !== 'string') continue;
    const localTime = Number(read(DRAFT_TIME_PREFIX + id)) || 0;
    if ((Number(draft.updatedAt) || 0) < localTime) continue;
    if (draft.code === '') {
      try { localStorage.removeItem(DRAFT_PREFIX + id); localStorage.removeItem(DRAFT_TIME_PREFIX + id); }
      catch { memoryFallback.delete(DRAFT_PREFIX + id); memoryFallback.delete(DRAFT_TIME_PREFIX + id); }
    } else {
      write(DRAFT_PREFIX + id, draft.code);
      write(DRAFT_TIME_PREFIX + id, String(draft.updatedAt || 0));
    }
  }
}
