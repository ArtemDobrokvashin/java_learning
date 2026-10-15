import { submitCode } from '../api.js';
import { saveDraft } from '../storage.js';
import { escapeHtml } from '../utils/helpers.js';
import { validateInput } from '../validator.js';
import { renderCodeEditor } from './code-editor.js';
import { showFeedback } from './feedback.js';
import { icon } from './icons.js';

// The checklist is self-reported evidence, NOT an automated assessment.
export const EVIDENCE = Object.freeze([
  { id: 'cli-verify', text: 'CLI: python3 verify.py проходит, включая повторный запуск с тем же файлом' },
  { id: 'cli-own-tests', text: 'CLI: добавил(а) минимум два своих крайних случая' },
  { id: 'cli-docs', text: 'CLI: в моём README есть запуск, пример работы и решения по файлам/ошибкам' },
  { id: 'cli-ci', text: 'CLI: GitHub Actions зелёный в моём репозитории' },
  { id: 'api-tests', text: 'API: PATCH/DELETE реализованы, mvn test зелёный' },
  { id: 'api-docs', text: 'API: задокументированы HTTP-статусы, curl и структура слоёв' },
  { id: 'api-ci', text: 'API: GitHub Actions зелёный в моём репозитории' },
]);

const stages = [
  { number: '01', title: 'Java Core и задачи', modules: '1–9', description: 'Циклы, ООП, коллекции, граничные случаи. Не угадывать ответ, а читать вход и считать результат.', labIds: ['lab-array-report', 'lab-user-lookup'] },
  { number: '02', title: 'Данные и надёжность', modules: '10–16', description: 'Ошибочный ввод, хранение, агрегации. Переносить идеи SQL в работающий код и разбирать сбои.', labIds: ['lab-import-report', 'lab-order-totals'] },
  { number: '03', title: 'Backend в отдельном проекте', modules: '17–22', description: 'HTTP, Spring, БД, тесты, CI. Лаборатория моделирует маршруты; реальный API потребует Maven и зависимостей.', labIds: ['lab-rest-router'] },
];

export function renderCareer(root, { labs, state, health, onOpenLab, onChecklistChange }) {
  const passed = new Set(state.completedLabs);
  const selected = new Set(state.careerChecklist);
  const passedCount = labs.filter((lab) => passed.has(lab.id)).length;
  root.innerHTML = `<div class="career-page page-enter">
    <div class="page-heading"><div><div class="eyebrow"><span class="eyebrow-line"></span> ПРАКТИЧЕСКИЙ МАРШРУТ</div>
      <h1>К позиции <em>junior Java backend</em></h1>
      <p>Изучить тему → решить задачу на разных входных данных → собрать проект → объяснить свои решения.</p></div>
      <span class="course-pill">${icon('target', 17)} ${passedCount} / ${labs.length} лабораторий</span></div>
    <div class="career-disclaimer" role="note">${icon('info', 21)} <span>Это маршрут практики, а не сертификат трудоустройства. Модули дают базу; пять лабораторий проверяются локальным JDK на нескольких входах. Реальные Spring, SQL и CI нужно отработать в отдельных проектах.</span></div>
    ${health && !health.jdkAvailable ? `<div class="health-notice" role="status">${icon('warning', 20)} <span>${health.message?.includes('отключён') ? 'В сетевом предпросмотре выполнение Java отключено. Откройте тренажёр на своём ноутбуке (127.0.0.1), чтобы проверить лаборатории.' : 'Для лабораторий установите JDK 17+ и перезапустите сервер. План и проекты доступны без JDK.'}</span></div>` : ''}
    <section aria-label="Этапы пути" class="career-stages">${stages.map((stage) => {
      const solved = stage.labIds.filter((id) => passed.has(id)).length;
      return `<article class="career-stage"><span class="career-stage-number">${stage.number}</span><div><span class="section-overline">МОДУЛИ ${stage.modules}</span><h2>${stage.title}</h2><p>${stage.description}</p><small>${solved} / ${stage.labIds.length} лабораторий проверено локально</small></div></article>`;
    }).join('')}</section>
    <section class="career-section" aria-labelledby="career-labs-title"><div class="section-heading"><div><span class="section-overline">КОД ВМЕСТО УГАДЫВАНИЯ</span><h2 id="career-labs-title">Лаборатории <span class="heading-count">${labs.length}</span></h2><p>Компиляция один раз, запуск на четырёх серверных наборах ввода. Проход — только если все ответы верны; общие 5 секунд на запуски.</p></div></div>
      <div class="career-lab-grid">${labs.map((lab, index) => `<article class="career-lab-card"><div class="career-lab-meta"><span>ЛАБОРАТОРИЯ ${String(index + 1).padStart(2, '0')}</span><span class="career-lab-status ${passed.has(lab.id) ? 'is-passed' : ''}">${passed.has(lab.id) ? 'Пройдена локально' : 'Не проверена'}</span></div>
        <h3>${escapeHtml(lab.title)}</h3><p>${escapeHtml(lab.goal)}</p><div class="career-skills">${lab.skills.map((skill) => `<span>${escapeHtml(skill)}</span>`).join('')}</div>
        <div class="career-lab-bottom"><small>Рекомендуем после модулей ${lab.prerequisites.map((id) => `<a href="#/module/${Number(id)}">${Number(id)}</a>`).join(', ')} · начать можно раньше</small><button type="button" class="button button-outline" data-lab="${escapeHtml(lab.id)}">${passed.has(lab.id) ? 'Повторить' : 'Решить'} ${icon('arrow', 17)}</button></div></article>`).join('')}</div>
    </section>
    <section class="career-section" aria-labelledby="career-projects-title"><div class="section-heading"><div><span class="section-overline">ПОКАЖИТЕ РЕЗУЛЬТАТ В КОДЕ</span><h2 id="career-projects-title">Проекты для портфолио</h2><p>Работают отдельно от тренажёра. Заготовки содержат контракт, код, проверки и CI; основные TODO решаете вы.</p></div></div>
      <div class="career-project-grid"><article class="career-project"><span class="career-project-tag">НАЧАТЬ ПОСЛЕ JAVA CORE</span><h3>Task Tracker CLI</h3><p>Задачи, ID, сохранение в UTF-8, ошибки, автоматическая проверка повторного запуска. Только Java 17 и Python для проверок, без Maven.</p><p><strong>Критерий:</strong> зелёный <code>python3 verify.py</code> плюс свои тесты и понятный README.</p><a class="button button-primary" href="/api/projects/task-tracker/starter" download>Скачать заготовку ZIP ${icon('arrow', 17)}</a></article>
      <article class="career-project"><span class="career-project-tag">ПОСЛЕ SPRING / REST / ТЕСТОВ</span><h3>Task API на Spring Boot</h3><p>Настоящий REST: контроллер, JPA, H2, валидация и интеграционные тесты. Maven скачает зависимости при первом запуске; в тренажёре Spring не запускается.</p><p><strong>Критерий:</strong> реализованы PATCH/DELETE, <code>mvn test</code> зелёный, HTTP-контракт и CI объяснены.</p><a class="button button-primary" href="/api/projects/task-api/starter" download>Скачать заготовку ZIP ${icon('arrow', 17)}</a></article></div>
    </section>
    <section class="career-section career-evidence" aria-labelledby="career-evidence-title"><span class="section-overline">ЧЕК-ЛИСТ ДОКАЗАТЕЛЬСТВ</span><h2 id="career-evidence-title">Что показать на собеседовании</h2><p>Отмечайте сами — тренажёр <strong>не проверяет</strong> файлы или GitHub. Лучше приложить ссылку на репозиторий и объяснить выбор решений, чем опираться на XP.</p>
      <div class="career-checklist">${EVIDENCE.map(({ id, text }) => `<label><input type="checkbox" data-evidence="${id}" ${selected.has(id) ? 'checked' : ''}><span>${escapeHtml(text)}</span></label>`).join('')}</div>
      <small data-evidence-count>Самооценка: ${EVIDENCE.filter(({ id }) => selected.has(id)).length} / ${EVIDENCE.length} пунктов</small></section>
  </div>`;
  root.querySelectorAll('[data-lab]').forEach((button) => button.addEventListener('click', () => onOpenLab(button.dataset.lab)));
  root.querySelectorAll('[data-evidence]').forEach((input) => input.addEventListener('change', () => {
    onChecklistChange(input.dataset.evidence, input.checked);
    root.querySelector('[data-evidence-count]').textContent = `Самооценка: ${root.querySelectorAll('[data-evidence]:checked').length} / ${EVIDENCE.length} пунктов`;
  }));
}

export function renderLab(root, { lab, state, health, onPassed, onExit }) {
  const passed = state.completedLabs.includes(lab.id);
  root.innerHTML = `<div class="career-page career-lab-page page-enter">
    <button class="back-link" type="button" data-exit>${icon('back', 17)} К практическому маршруту</button>
    <div class="page-heading"><div><div class="eyebrow"><span class="eyebrow-line"></span> ЛАБОРАТОРИЯ · JAVA 17</div><h1>${escapeHtml(lab.title)}</h1><p>${escapeHtml(lab.goal)}</p></div>
      <span class="career-lab-status ${passed ? 'is-passed' : ''}" data-lab-status>${passed ? 'Пройдена локально' : 'Не проверена'}</span></div>
    <div class="career-disclaimer" role="note">${icon('info', 20)}<span>Практика без XP: можно пробовать сколько угодно. Сервер сам подставляет четыре разных входа и проверяет вывод каждого; образец ниже — только первый случай. Выполнение Java доступно лишь при локальном запуске тренажёра.</span></div>
    <div class="career-lab-layout"><section class="career-lab-instructions" aria-label="Условия задания"><span class="section-overline">ЗАДАЧА</span><h2>Что нужно сделать</h2><p>${escapeHtml(lab.description)}</p>
      <h3>Ввод</h3><p>${escapeHtml(lab.inputFormat)}</p><h3>Вывод</h3><p>${escapeHtml(lab.outputFormat)}</p><h3>Пример ввода</h3><pre>${escapeHtml(lab.sampleInput)}</pre><h3>Ожидаемый вывод</h3><pre>${escapeHtml(lab.sampleOutput)}</pre>
      <details class="career-hint"><summary>Нужна подсказка?</summary><p>${escapeHtml(lab.hint)}</p></details></section>
      <section class="career-lab-work" aria-label="Решение"><span class="section-overline">ВАШ КОД</span><h2>Решение в Main.java</h2><p>Читайте данные из <code>System.in</code>. Черновик сохраняется только в этом браузере; файлы проекта скачиваются отдельно.</p>
        ${health && !health.jdkAvailable ? `<div class="editor-warning" role="status">${icon('warning', 18)} ${health.message?.includes('отключён') ? 'В сетевом предпросмотре запуск отключён. Для проверки используйте локальный сервер.' : 'JDK не найден. Установите JDK 17+ и перезапустите сервер.'}</div>` : ''}
        <div data-editor></div><div class="career-lab-actions"><button type="button" class="button button-primary" data-run ${health && !health.jdkAvailable ? 'disabled' : ''}>Проверить на 4 входах ${icon('arrow', 17)}</button><small>Компиляция ≤ 10 с · все запуски ≤ 5 с</small></div>
        <div data-feedback class="feedback-slot"></div></section></div>
  </div>`;
  const editor = renderCodeEditor(root.querySelector('[data-editor]'), lab);
  const button = root.querySelector('[data-run]');
  const feedback = root.querySelector('[data-feedback]');
  let alive = true;
  let pending = false;
  root.querySelector('[data-exit]').addEventListener('click', onExit);
  button.addEventListener('click', async () => {
    if (!alive || pending) return;
    const code = editor.getValue();
    const problem = validateInput(lab, code);
    if (problem) {
      showFeedback(feedback, { status: 'notice', title: 'Проверьте решение', message: problem });
      return;
    }
    saveDraft(lab.id, code); // Don't lose edits if the learner leaves immediately.
    pending = true;
    button.disabled = true;
    button.textContent = 'Проверяем…';
    try {
      const result = await submitCode(code, lab.id, 'run');
      if (!alive) return;
      if (result.success) {
        onPassed(lab.id);
        root.querySelector('[data-lab-status]').textContent = 'Пройдена локально';
        root.querySelector('[data-lab-status]').classList.add('is-passed');
        showFeedback(feedback, { status: 'success', title: 'Все входы пройдены', message: result.output });
      } else {
        showFeedback(feedback, { status: 'error', title: result.kind === 'compile_error' ? 'Ошибка компиляции' : 'Есть непройденный вход',
          message: 'Исправьте решение и попробуйте снова. Попытки не ограничены.', detail: result.error, output: result.output });
      }
    } catch (error) {
      if (!alive) return;
      showFeedback(feedback, { status: 'notice', title: 'Проверка недоступна', message: error.message });
    } finally {
      if (alive) {
        pending = false;
        button.disabled = false;
        button.innerHTML = `Проверить на 4 входах ${icon('arrow', 17)}`;
      }
    }
  });
  return () => { alive = false; saveDraft(lab.id, editor.getValue()); editor.destroy(); };
}
