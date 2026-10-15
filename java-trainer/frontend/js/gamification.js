import {
  REVIEW_XP, STREAK_MILESTONES,
  STREAK_MULTIPLIER, XP_BY_TYPE, XP_BY_DIFFICULTY, XP_PER_LEVEL,
} from './utils/constants.js';
import { dayKey, daysBetween } from './utils/helpers.js';

// Mutations are saved by the app after each answer. Passing a clock makes the
// rules testable without changing the system date.
export function syncState(state, now = new Date()) {
  const gap = daysBetween(state.lastActiveDate, dayKey(now));
  if (gap > 1 && gap !== Infinity) {
    state.streak = 0;
    state.milestones = [];
  }
  return state;
}

export function levelInfo(xp) {
  const level = Math.floor(xp / XP_PER_LEVEL) + 1;
  return { level, current: xp % XP_PER_LEVEL, required: XP_PER_LEVEL };
}

export function recordAttempt(state, task, correct, { review = false, now = new Date() } = {}) {
  syncState(state, now);
  const previous = state.attempts[task.id] || {};
  state.attempts[task.id] = {
    correct,
    type: task.type,
    lastAttempt: now.toISOString(),
    mistakes: (previous.mistakes || 0) + (correct ? 0 : 1),
    needsReview: review ? !correct : (!correct || Boolean(previous.needsReview)),
  };
  if (!correct) return { earnedXp: 0, milestoneXp: 0, baseXp: 0 };

  const today = dayKey(now);
  if (state.lastActiveDate !== today) {
    state.streak = daysBetween(state.lastActiveDate, today) === 1 ? state.streak + 1 : 1;
    state.lastActiveDate = today;
  }

  let milestoneXp = 0;
  const milestone = STREAK_MILESTONES[state.streak];
  if (milestone && !state.milestones.includes(state.streak)) {
    milestoneXp = milestone;
    state.milestones.push(state.streak);
  }

  let baseXp = 0;
  if (review) {
    // Review earns a small reward once per question per local day, not an
    // unlimited stream of the original 50 XP code-task reward.
    if (state.reviewRewards[task.id] !== today) {
      baseXp = REVIEW_XP;
      state.reviewRewards[task.id] = today;
    }
  } else if (!state.awardedTasks.includes(task.id)) {
    const type = task.variant === 'theory' ? 'theory' : task.type;
    baseXp = task.difficulty ? XP_BY_DIFFICULTY[task.difficulty] : XP_BY_TYPE[type] || 0;
    state.awardedTasks.push(task.id);
  }
  const withStreak = state.streak >= 2 ? Math.round(baseXp * STREAK_MULTIPLIER) : baseXp;
  const earnedXp = withStreak + milestoneXp;
  state.xpLedger ||= {};
  if (withStreak > 0) {
    const key = review ? `review:${task.id}:${today}` : `task:${task.id}`;
    state.xpLedger[key] = Math.max(state.xpLedger[key] || 0, withStreak);
  }
  if (milestoneXp > 0) state.xpLedger[`milestone:${state.streak}:${today}`] = milestoneXp;
  if (task.difficulty === 'hardcore' && task.lessonId) {
    state.achievements ||= [];
    const badge = `mastery:${task.lessonId}`;
    if (!state.achievements.includes(badge)) state.achievements.push(badge);
  }
  state.xp += earnedXp;
  return { earnedXp, milestoneXp, baseXp };
}
