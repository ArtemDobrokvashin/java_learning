import { dayKey, escapeHtml, percentage, plural } from '../utils/helpers.js';
import { moduleProgress, nextStep } from '../utils/course.js';
import { isDue } from '../review.js';
import { icon } from './icons.js';

const STATUS_LABEL = { available: 'Доступен', completed: 'Пройден', locked: 'Закрыт', planned: 'Скоро' };

function renderCard(module, state, index) {
  const progress = moduleProgress(module, state);
  const clickable = ['available', 'completed'].includes(module.status);
  const badgeIcon = module.status === 'locked' ? 'lock' : module.status === 'completed' ? 'check' : module.status === 'planned' ? 'clock' : 'arrow';
  return `<button type="button" class="module-card status-${module.status} reveal" style="--reveal-delay:${Math.min(index, 8) * 55}ms" data-module="${module.id}" ${clickable ? '' : 'disabled'} aria-label="Модуль ${module.id}: ${escapeHtml(module.title)}. ${STATUS_LABEL[module.status]}">
    <span class="module-topline"><span class="module-number">МОДУЛЬ ${String(module.id).padStart(2, '0')}</span><span class="module-status">${icon(badgeIcon, 15)} ${STATUS_LABEL[module.status]}</span></span>
    <span class="module-symbol" aria-hidden="true">${icon(module.id <= 3 ? ['code','target','refresh'][module.id - 1] : module.id >= 17 ? 'spark' : 'layers', 26)}</span>
    <span class="module-title">${escapeHtml(module.title)}</span>
    <span class="module-subtitle">${escapeHtml(module.subtitle)}</span>
    <span class="module-progress-line"><span>${progress.done} из ${progress.total} шагов</span><span>${percentage(progress.done, progress.total)}%</span></span>
    <span class="meter"><span style="width:${percentage(progress.done, progress.total)}%"></span></span>
    <span class="module-footer"><span>${module.lessons.length} ${plural(module.lessons.length, 'урок', 'урока', 'уроков')} · практика${module.boss ? ' · экзамен' : ''}</span><span class="module-footer-arrow">${icon(clickable ? 'arrow' : badgeIcon, 17)}</span></span>
  </button>`;
}

export function renderDashboard(root, { modules, state, health, onContinue, onOpenModule, onReview }) {
  const completed = modules.filter((module) => module.status === 'completed').length;
  const current = modules.find((module) => module.status === 'available');
  const step = current ? nextStep(current, state) : null;
  const dueCount = Object.entries(state.attempts).filter(([id, history]) => {
    const number = Number(id.match(/^m(\d{2})-/)?.[1]);
    return state.completedPractices.includes(number) && !['code', 'copy'].includes(history.type) && isDue(history);
  }).length;
  const activeToday = state.lastActiveDate === dayKey();

  root.innerHTML = `
    <div class="dashboard page-enter">
      <div class="page-heading"><div><div class="eyebrow"><span class="eyebrow-line"></span> ЛИЧНЫЙ МАРШРУТ</div><h1>Учиться писать код — <em>это путь.</em></h1><p>Одна задача за другой. Ваш темп, ваш прогресс, ваш Java.</p></div><span class="course-pill">${icon('layers', 17)} 22 модуля</span></div>
      <section class="hero" aria-label="Продолжить обучение">
        <div class="hero-copy">
          <span class="hero-kicker">${icon('spark', 16)} ПРОДОЛЖАЙТЕ В ТОМ ЖЕ ДУХЕ</span>
          <h2>${current ? 'Сегодня — отличный день<br>для нового открытия.' : 'Вы прошли<br>все 22 модуля!'}</h2>
          <p>${current ? `Следующий шаг: ${escapeHtml(current.title)}${step ? ` · ${step.type === 'lesson' ? 'новый урок' : step.type === 'boss' ? 'стоп-урок' : 'практика'}` : ''}.` : 'От первой переменной до CI/CD — готово! Возвращайтесь к повторению, чтобы поддерживать навыки.'}</p>
          <button class="button button-white hero-button" type="button" data-action="continue" ${!current ? 'disabled' : ''}>${icon('play', 17)} Продолжить обучение ${icon('arrow', 19)}</button>
          <div class="hero-caption">${icon('check', 16)} ${completed} из 22 модулей завершено</div>
        </div>
        <div class="hero-art" aria-hidden="true"><div class="hero-orbit orbit-one"></div><div class="hero-orbit orbit-two"></div><div class="hero-star star-one">✦</div><div class="hero-star star-two">✧</div><div class="hero-editor"><div class="hero-editor-top"><span></span><span></span><span></span><small>Main.java</small></div><div class="hero-editor-code"><div><b>01</b> <span class="syntax-purple">public class</span> Main {</div><div><b>02</b> &nbsp; <span class="syntax-purple">public static void</span> main(...) {</div><div><b>03</b> &nbsp;&nbsp; System.out.println(<span class="syntax-yellow">"Hello, Java!"</span>);</div><div><b>04</b> &nbsp; }</div><div><b>05</b> }</div></div><div class="hero-success">${icon('check', 17)} Скомпилировано!</div></div></div>
      </section>
      ${health && !health.jdkAvailable ? `<div class="health-notice" role="status">${icon('info', 20)}<span><strong>${health.message?.includes('отключён') ? 'Компиляция отключена в сетевом предпросмотре.' : 'Для заданий с кодом нужен JDK.'}</strong> ${health.message?.includes('отключён') ? 'Для проверки Java запустите проект на своём ноутбуке.' : 'Установите JDK 17+ и перезапустите сервер. Теорию и тесты можно проходить уже сейчас.'}</span><a href="#/settings">Как настроить ${icon('arrow', 16)}</a></div>` : ''}
      <div class="dashboard-widgets">
        <div class="widget review-widget"><span class="widget-icon review-icon">${icon('refresh', 23)}</span><div><span class="widget-label">ИНТЕРВАЛЬНОЕ ПОВТОРЕНИЕ</span><h3>${dueCount ? `${dueCount} ${plural(dueCount, 'вопрос ждёт', 'вопроса ждут', 'вопросов ждут')} вас` : 'Знания под контролем'}</h3><p>${dueCount ? 'Повторите темы, в которых были ошибки.' : 'Ошибки и старые вопросы появятся здесь.'}</p></div><button type="button" class="widget-button" data-action="review" ${!dueCount ? 'disabled' : ''} aria-label="Начать повторение">${icon('arrow', 21)}</button></div>
        <div class="widget daily-widget"><span class="widget-icon daily-icon">${icon('target', 23)}</span><div><span class="widget-label">ЦЕЛЬ НА СЕГОДНЯ</span><h3>${activeToday ? 'Сегодня вы уже учились!' : 'Один шаг — и день ваш'}</h3><p>${activeToday ? 'Серия продолжается. Так держать!' : 'Решите любое задание, чтобы сохранить серию.'}</p></div><div class="daily-check ${activeToday ? 'is-done' : ''}">${icon(activeToday ? 'check' : 'clock', 20)}</div></div>
      </div>
      <section class="career-teaser" aria-label="Практический маршрут"><div><span class="section-overline">СЛЕДУЮЩИЙ ШАГ ПОСЛЕ ТЕОРИИ</span><h2>Хотите приблизиться к junior Java backend?</h2><p>Решайте задачи на нескольких входах и соберите два проекта, которые можно показать и объяснить. XP не заменяет практику.</p></div><a href="#/career" class="button button-outline">Практический маршрут ${icon('arrow', 17)}</a></section>
      <section class="course-section" aria-labelledby="course-title"><div class="section-heading"><div><span class="section-overline">ВАША ПРОГРАММА</span><h2 id="course-title">Карта курса <span class="heading-count">22</span></h2><p>От первой переменной до CI/CD — шаг за шагом.</p></div><div class="filter-group" role="group" aria-label="Фильтр модулей"><button type="button" class="filter-button is-active" data-filter="all" aria-pressed="true">Все модули</button><button type="button" class="filter-button" data-filter="ready" aria-pressed="false">Доступные</button></div></div>
        <div class="module-grid">${modules.map((module, index) => renderCard(module, state, index)).join('')}</div>
        <p class="empty-filter" hidden>Пока нет доступных модулей. Завершите практику предыдущего.</p>
      </section>
    </div>`;

  root.querySelector('[data-action="continue"]')?.addEventListener('click', onContinue);
  root.querySelector('[data-action="review"]')?.addEventListener('click', onReview);
  root.querySelectorAll('[data-module]').forEach((button) => {
    button.addEventListener('click', () => onOpenModule(Number(button.dataset.module)));
  });
  root.querySelectorAll('[data-filter]').forEach((button) => button.addEventListener('click', () => {
    root.querySelectorAll('[data-filter]').forEach((entry) => {
      entry.classList.toggle('is-active', entry === button);
      entry.setAttribute('aria-pressed', String(entry === button));
    });
    const readyOnly = button.dataset.filter === 'ready';
    let visible = 0;
    root.querySelectorAll('.module-card').forEach((card) => {
      card.hidden = readyOnly && !card.classList.contains('status-available') && !card.classList.contains('status-completed');
      if (!card.hidden) visible++;
    });
    root.querySelector('.empty-filter').hidden = visible > 0;
  }));
}
