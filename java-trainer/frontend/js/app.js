import { getHealth, getLabs, getModuleContent, getModules, mergeProgress } from './api.js';
import { levelInfo, recordAttempt, syncState } from './gamification.js';
import { addSpacedReview, dueQuestions } from './review.js';
import { sendDueReminder } from './reminders.js';
import { navigate, subscribe } from './router.js';
import { createSharedProgress } from './shared-progress.js';
import { loadState, resetState, saveState, setPersistenceListener } from './storage.js';
import { canOpenStep, courseStatuses, nextStep, stepUrl } from './utils/course.js';
import { APP_VERSION } from './utils/constants.js';
import { escapeHtml } from './utils/helpers.js';
import { renderDashboard } from './ui/dashboard.js';
import { renderCareer, renderLab } from './ui/career.js';
import { icon } from './ui/icons.js';
import { renderSession } from './ui/lesson.js';
import { renderModule } from './ui/module.js';
import { renderExam } from './ui/exam.js';
import { renderSettings } from './ui/settings.js';
import { renderResult } from './ui/success.js';

let state = loadState();
syncState(state);
saveState(state);
let modules = [];
let health = null;
let cleanup = null;
let resultContext = null;
let navigationId = 0;
let activeRoute = { name: 'dashboard' };
const app = document.querySelector('#app');
document.documentElement.dataset.theme = state.theme;
const shared = createSharedProgress({
  getState: () => state,
  applyState: (remote) => {
    Object.assign(state, remote); // keep live lesson references intact
    saveState(state, { notify: false });
    updateHeader();
  },
});

function shell() {
  app.innerHTML = `<div class="app-shell">
    <aside class="sidebar" aria-label="Основная навигация">
      <a class="brand" href="#/" aria-label="Javada — на главную"><span class="brand-mark"><span>{</span><span class="brand-dot">●</span><span>}</span></span><span class="brand-words"><strong>javada<span>.</span></strong><small>ТВОЙ JAVA-ТРЕНАЖЁР</small></span></a>
      <div class="sidebar-section-label">МЕНЮ</div>
      <nav class="sidebar-nav"><a href="#/" data-nav="dashboard">${icon('map', 21)} <span>Карта курса</span></a><a href="#/review" data-nav="review">${icon('refresh', 21)} <span>Повторение</span></a><a href="#/career" data-nav="career">${icon('target', 21)} <span>К junior</span></a><a href="#/settings" data-nav="settings">${icon('settings', 21)} <span>Настройки</span></a></nav>
      <div class="sidebar-spacer"></div>
      <div id="sidebar-level"></div>
      <div class="sidebar-footer"><span class="online-dot"></span> Локальный тренажёр <small>Данные остаются у вас</small></div>
    </aside>
    <div class="main-column"><header class="topbar" id="topbar"></header><main id="view" class="view" tabindex="-1"><div class="loading-state"><span class="button-spinner"></span> Загружаем ваш маршрут…</div></main><footer class="app-footer"><span>Сделано для маленьких побед каждый день ✦</span><span>Java · в своём темпе</span></footer></div>
  </div>`;
}

function updateHeader() {
  const { level, current, required } = levelInfo(state.xp);
  document.querySelector('#topbar').innerHTML = `<div class="breadcrumbs"><span>МОЙ ПУТЬ</span>${icon('chevron', 13)}<strong>${activeRoute.name === 'settings' ? 'Настройки' : activeRoute.name === 'review' ? 'Повторение' : activeRoute.name === 'career' || activeRoute.name === 'lab' ? 'Практика для junior' : activeRoute.name === 'result' ? 'Результат' : 'Курс Java'}</strong></div>
    <div class="topbar-actions"><div class="top-stat stat-xp" title="Всего опыта: ${state.xp}"><span class="top-stat-icon">${icon('bolt', 19)}</span><strong>${state.xp}</strong><small>XP · ур. ${level}</small></div><div class="top-stat stat-streak" title="Серия дней подряд"><span class="top-stat-icon">${icon('flame', 19)}</span><strong>${state.streak}</strong><small>дней</small></div><a href="#/settings" class="top-settings" aria-label="Настройки">${icon('settings', 21)}</a></div>`;
  document.querySelector('#sidebar-level').innerHTML = `<div class="level-card"><div class="level-card-top"><span>${icon('spark', 18)} ВАШ УРОВЕНЬ</span><strong>${String(level).padStart(2, '0')}</strong></div><div class="level-card-bar"><span style="width:${Math.round(current / required * 100)}%"></span></div><small>${current} / ${required} XP до нового уровня</small></div>`;
  document.querySelectorAll('[data-nav]').forEach((entry) => {
    const section = activeRoute.name === 'lab' ? 'career'
      : ['module', 'lesson', 'mastery', 'practice', 'boss', 'exam', 'result'].includes(activeRoute.name) ? 'dashboard' : activeRoute.name;
    const selected = entry.dataset.nav === section;
    entry.classList.toggle('is-active', selected);
    if (selected) entry.setAttribute('aria-current', 'page');
    else entry.removeAttribute('aria-current');
  });
}

function showMessage(title, message, path = '/') {
  document.querySelector('#view').innerHTML = `<div class="message-page page-enter"><span class="message-icon">${icon('info', 30)}</span><h1>${escapeHtml(title)}</h1><p>${escapeHtml(message)}</p><a class="button button-primary" href="#${path}">Вернуться к карте ${icon('arrow', 18)}</a></div>`;
}

function checkReminders() {
  if (sendDueReminder(state)) saveState(state);
}

function freshModules() {
  return courseStatuses(modules, state);
}

function getModule(id) {
  return freshModules().find((module) => module.id === id);
}

function goToNext() {
  const current = freshModules().find((module) => module.status === 'available');
  if (!current) return navigate('/');
  const step = nextStep(current, state);
  navigate(step ? stepUrl(current, step) : `/module/${current.id}`);
}

function record(task, correct, review) {
  const reward = recordAttempt(state, task, correct, { review });
  saveState(state);
  updateHeader();
  return reward;
}

function completeSession(result) {
  const { mode, module, activity } = result;
  if (mode === 'lesson' && !state.completedLessons.includes(activity.id)) {
    state.completedLessons.push(activity.id);
  } else if (mode === 'practice' && !state.completedPractices.includes(module.id)) {
    state.completedPractices.push(module.id);
  } else if (mode === 'boss') {
    const previous = state.bossScores[module.id] || { best: 0, passed: false };
    state.bossScores[module.id] = { best: Math.max(previous.best, result.accuracy), passed: previous.passed || result.passed };
  }
  saveState(state);
  const next = mode === 'review' ? null : nextStep(module, state);
  const nextModule = mode === 'review' ? null : freshModules().find((entry) => entry.id === module?.id + 1 && entry.status === 'available');
  const nextPath = next ? stepUrl(module, next) : nextModule ? `/module/${nextModule.id}` : '/';
  const nextLabel = next?.type === 'lesson' ? 'Следующий урок' : next?.type === 'boss' ? 'К экзамену'
    : next?.type === 'exam' ? 'К мини-экзамену' : next?.type === 'practice' ? 'К практике' : nextModule ? 'Следующий модуль' : 'На карту курса';
  resultContext = { result, nextPath, nextLabel, retryPath: mode === 'boss' ? `/boss/${module.id}` : null };
  updateHeader();
  navigate('/result');
}

async function showRoute(route) {
  const callId = ++navigationId;
  cleanup?.();
  cleanup = null;
  activeRoute = route;
  syncState(state);
  saveState(state);
  updateHeader();
  const view = document.querySelector('#view');
  const module = route.moduleId ? getModule(route.moduleId) : null;

  if (route.name === 'dashboard') {
    renderDashboard(view, { modules: freshModules(), state, health,
      onContinue: goToNext,
      onOpenModule: (id) => navigate(`/module/${id}`),
      onReview: () => navigate('/review') });
  } else if (route.name === 'career' || route.name === 'lab') {
    view.innerHTML = '<div class="loading-state"><span class="button-spinner"></span> Загружаем практический маршрут…</div>';
    try {
      const labs = await getLabs();
      if (callId !== navigationId) return;
      if (route.name === 'career') {
        renderCareer(view, { labs, state, health, onOpenLab: (id) => navigate(`/lab/${id}`),
          onChecklistChange: (id, checked) => {
            state.careerChecklist = checked
              ? [...new Set([...state.careerChecklist, id])]
              : state.careerChecklist.filter((entry) => entry !== id);
            saveState(state);
          } });
      } else {
        const lab = labs.find((entry) => entry.id === route.labId);
        if (!lab) { showMessage('Такой лаборатории нет', 'Выберите задачу на практическом маршруте.', '/career'); return; }
        cleanup = renderLab(view, { lab, state, health, onExit: () => navigate('/career'),
          onPassed: (id) => {
            if (!state.completedLabs.includes(id)) state.completedLabs.push(id);
            saveState(state);
          } });
      }
    } catch (error) {
      if (callId !== navigationId) return;
      console.error('Не удалось открыть лаборатории:', error);
      showMessage('Не удалось загрузить практику', error.message, '/career');
    }
  } else if (route.name === 'settings') {
    renderSettings(view, { state, health, onBack: () => navigate('/'),
      onRefreshHealth: async () => {
        try {
          health = await getHealth(true);
        } catch (error) {
          console.warn('Не удалось обновить статус JDK', error);
          health = null;
        }
        if (activeRoute.name === 'settings') navigate('/settings');
      },
      onThemeChange: (theme) => {
        state.theme = theme; saveState(state); document.documentElement.dataset.theme = theme;
        navigate('/settings');
      },
      onReminderChange: (changes) => {
        Object.assign(state, changes); saveState(state); checkReminders();
      },
      onReset: async () => {
        try { await shared.reset(); }
        catch (error) { console.warn('Не удалось сбросить общее хранилище; попробуйте снова.', error); return; }
        state = resetState(); document.documentElement.dataset.theme = state.theme;
        resultContext = null; saveState(state, { notify: false }); updateHeader(); navigate('/');
      } });
  } else if (route.name === 'result') {
    if (!resultContext) { navigate('/'); return; }
    renderResult(view, { ...resultContext,
      onNext: () => navigate(resultContext.nextPath),
      onRetry: () => navigate(resultContext.retryPath),
      onMap: () => navigate('/') });
  } else if (route.name === 'module') {
    if (!module || module.status === 'locked') {
      showMessage('Пока закрыто', 'Завершите практику предыдущего модуля, чтобы продолжить.'); return;
    }
    if (module.status === 'planned') {
      showMessage('Материалы не найдены', 'Проверьте файлы курса и обновите страницу. Все 22 модуля должны быть доступны по порядку.'); return;
    }
    renderModule(view, { module, state, onBack: () => navigate('/'),
      onOpenStep: (type, id) => navigate(['lesson', 'mastery'].includes(type)
        ? `/${type}/${module.id}/${id}` : `/${type}/${module.id}`) });
  } else if (route.name === 'exam') {
    if (!module || module.status === 'locked' || !canOpenStep(module, 'exam', state)) {
      showMessage('Сначала завершите практику', 'Мини-экзамен открывается после практики модуля.'); return;
    }
    view.innerHTML = '<div class="loading-state">Готовим мини-экзамен…</div>';
    try {
      // Flush practice completion even if the debounced profile sync is still in flight.
      await mergeProgress(state);
      const content = await getModuleContent(module.id);
      if (callId !== navigationId) return;
      cleanup = renderExam(view, { module, exam: content.exam, health,
        onProfile: (profile) => { Object.assign(state, profile.state); saveState(state); updateHeader(); },
        onComplete: () => navigate(`/module/${module.id}`),
        onTheory: () => navigate(`/lesson/${module.id}/${module.lessons[0].id}`),
        onExit: () => navigate(`/module/${module.id}`) });
    } catch (error) {
      if (callId !== navigationId) return;
      showMessage('Не удалось открыть экзамен', error.message, `/module/${module.id}`);
    }
  } else if (['lesson', 'mastery', 'practice', 'boss', 'review'].includes(route.name)) {
    if (route.name !== 'review' && (!module || module.status === 'locked' || module.status === 'planned')) {
      showMessage('Урок пока недоступен', 'Начните с доступного модуля на карте курса.'); return;
    }
    if (route.name === 'mastery' && !state.completedLessons.includes(route.lessonId)) {
      showMessage('Сначала завершите урок', 'Хард открыт после обязательных уровней.'); return;
    }
    if (route.name !== 'review' && !canOpenStep(module, ['lesson', 'mastery'].includes(route.name)
      ? route.lessonId : route.name, state)) {
      showMessage('Этот шаг ещё впереди', 'Сначала завершите предыдущие уроки.'); return;
    }
    view.innerHTML = '<div class="loading-state"><span class="button-spinner"></span> Подбираем задания…</div>';
    try {
      let activity, entries, sessionModule = module;
      if (route.name === 'review') {
        const due = await dueQuestions(state, Infinity, getModuleContent);
        if (callId !== navigationId) return;
        if (!due.length) {
          showMessage('Пока нечего повторять', 'Ошибки и вопросы старше семи дней появятся здесь после завершения модуля.'); return;
        }
        activity = { id: 'review', title: 'Повторение знаний', description: 'Вопросы, которые полезно вспомнить прямо сейчас.' };
        entries = due.slice(0, 5).map((task) => ({ task, review: true }));
        sessionModule = { title: 'Повторение', id: 0 };
      } else {
        const content = await getModuleContent(module.id);
        if (callId !== navigationId) return;
        activity = ['lesson', 'mastery'].includes(route.name)
          ? content.lessons.find((lesson) => lesson.id === route.lessonId)
          : route.name === 'practice' ? content.practice : content.boss;
        if (!activity?.tasks?.length) {
          showMessage('Задания ещё не готовы', 'Для этого шага пока нет заданий.'); return;
        }
        if (route.name === 'boss') activity = { ...activity, passingScore: module.boss?.passingScore || 0.8 };
        entries = route.name === 'lesson'
          ? await addSpacedReview(activity.tasks, state, module.id, getModuleContent)
          : route.name === 'mastery'
            ? activity.tasks.filter((task) => task.optional).map((task) => ({ task, review: false }))
            : activity.tasks.map((task) => ({ task, review: false }));
      }
      if (callId !== navigationId) return;
      cleanup = renderSession(view, { module: sessionModule, activity,
        mode: route.name === 'mastery' ? 'lesson' : route.name, entries, health,
        optionalOnly: route.name === 'mastery', onAttempt: record,
        onComplete: completeSession, onExit: () => navigate(route.name === 'review' ? '/' : `/module/${module.id}`) });
    } catch (error) {
      if (callId !== navigationId) return;
      console.error('Не удалось открыть урок:', error);
      showMessage('Не удалось загрузить урок', error.message);
    }
  } else {
    showMessage('Такой страницы нет', 'Вернитесь на карту и выберите доступный модуль.');
  }
  window.scrollTo({ top: 0, behavior: 'auto' });
}

async function start() {
  shell();
  updateHeader();
  setPersistenceListener(shared.onPersist);
  await shared.sync(); // import existing localStorage before deciding what's unlocked
  try {
    [modules, health] = await Promise.all([
      getModules(state),
      getHealth().catch((error) => { console.warn('Не удалось проверить JDK', error); return null; }),
    ]);
    if (health?.revision) {
      import('./api.js').then(m => m.setCourseRevision(health.revision));
    }
    subscribe(showRoute);
    checkReminders();
    setInterval(checkReminders, 60_000);
    setInterval(() => { void shared.sync(); }, 15_000);
    window.addEventListener('focus', () => { checkReminders(); void shared.sync(); });
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) checkReminders();
    });
    // Another tab may have already delivered today's notification. Never
    // overwrite its learning progress just to refresh our reminder settings.
    window.addEventListener('storage', (event) => {
      if (event.key !== `javada.state.v${APP_VERSION}`) return;
      const latest = loadState();
      state.remindersEnabled = latest.remindersEnabled;
      state.reminderTime = latest.reminderTime;
      state.lastReminderDay = latest.lastReminderDay;
    });
  } catch (error) {
    console.error('Не удалось загрузить курс:', error);
    showMessage('Не удалось загрузить курс', `${error.message} Обновите страницу после запуска сервера.`);
  }
}

start();
