// Progress lives in the browser. These pure selectors keep all views in sync.
export function bossPassed(module, state) {
  return !module.boss || state.bossScores[module.id]?.passed === true;
}

export function isModuleComplete(module, state) {
  return state.completedPractices.includes(module.id) && bossPassed(module, state) && (!module.exam || state.examScores?.[module.id]?.passed === true);
}

export function courseStatuses(modules, state) {
  let unlocked = true;
  return modules.map((module) => {
    const status = !unlocked ? 'locked'
      : module.contentStatus !== 'ready' ? 'planned'
        : isModuleComplete(module, state) ? 'completed' : 'available';
    unlocked = unlocked && isModuleComplete(module, state);
    return { ...module, status };
  });
}

export function moduleProgress(module, state) {
  const lessonsDone = module.lessons.filter((lesson) => state.completedLessons.includes(lesson.id)).length;
  const practiceDone = state.completedPractices.includes(module.id) ? 1 : 0;
  const bossDone = module.boss && bossPassed(module, state) ? 1 : 0;
  const examDone = module.exam && state.examScores?.[module.id]?.passed ? 1 : 0;
  const total = module.lessons.length + 1 + (module.boss ? 1 : 0) + (module.exam ? 1 : 0);
  return { done: lessonsDone + practiceDone + bossDone + examDone, total, lessonsDone };
}

export function canOpenStep(module, step, state) {
  if (step === 'exam') return !!module.exam && state.completedPractices.includes(module.id);
  if (step === 'practice') {
    return module.lessons.every((lesson) => state.completedLessons.includes(lesson.id)) && bossPassed(module, state);
  }
  if (step === 'boss') {
    return !!module.boss && module.lessons.every((lesson) => state.completedLessons.includes(lesson.id));
  }
  const index = module.lessons.findIndex((lesson) => lesson.id === step);
  return index >= 0 && (index === 0 || state.completedLessons.includes(module.lessons[index - 1].id));
}

export function nextStep(module, state) {
  const lesson = module.lessons.find((entry) => !state.completedLessons.includes(entry.id));
  if (lesson) return { type: 'lesson', id: lesson.id };
  if (module.boss && !bossPassed(module, state)) return { type: 'boss', id: module.boss.id };
  if (!state.completedPractices.includes(module.id)) return { type: 'practice', id: module.practiceId };
  if (module.exam && !state.examScores?.[module.id]?.passed) return { type: 'exam', id: module.exam.id };
  return null;
}

export function stepUrl(module, step) {
  if (!step) return '/';
  if (step.type === 'lesson') return `/lesson/${module.id}/${step.id}`;
  if (step.type === 'exam') return `/exam/${module.id}`;
  if (step.type === 'boss') return `/boss/${module.id}`;
  return `/practice/${module.id}`;
}
