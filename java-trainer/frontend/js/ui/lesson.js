import { TASK_LABELS, XP_BY_TYPE, XP_BY_DIFFICULTY, REVIEW_XP } from '../utils/constants.js';
import { escapeHtml, percentage } from '../utils/helpers.js';
import { codeHint, isCorrect, parsonsIndentIssues, validateInput } from '../validator.js';
import { submitCode } from '../api.js';
import { clearFeedback, showFeedback } from './feedback.js';
import { icon } from './icons.js';
import { renderQuiz } from './quiz.js';

const MODE_LABEL = { lesson: 'УРОК', practice: 'ПРАКТИКА', boss: 'СТОП-УРОК', review: 'ПОВТОРЕНИЕ' };
const CODE_LANGUAGE = { sql: 'sql', xml: 'markup', yaml: 'yaml', dockerfile: 'docker', text: 'text' };
const LEVEL_LABEL = { easy: 'ЛЁГКИЙ', medium: 'СРЕДНИЙ', hard: 'СЛОЖНЫЙ', hardcore: 'ХАРД · НЕОБЯЗАТЕЛЬНО' };

function codeExampleHtml(example, title) {
  const language = CODE_LANGUAGE[example.language] || 'java';
  return `<div class="theory-code-title"><span class="editor-filedot"></span> ${escapeHtml(title)}</div>
    <pre class="language-${language} theory-code"><code class="language-${language}">${escapeHtml(example.code)}</code></pre>`;
}

function workedExampleHtml(guide, practice = false) {
  return `<section class="theory-worked" aria-label="${practice ? 'Подготовка к практике' : 'Пошаговый пример'}">
    <div class="theory-worked-kicker">${practice ? 'РАЗБОР ПЕРЕД ПРАКТИКОЙ' : 'ВТОРОЙ ПРИМЕР · ШАГ ЗА ШАГОМ'}</div>
    <h3>Зачем это нужно?</h3><p>${escapeHtml(guide.why)}</p>
    <h3>Как сделать</h3><ol class="theory-steps">${guide.steps.map((step) => `<li>${escapeHtml(step)}</li>`).join('')}</ol>
    <div class="theory-example-label">${guide.example.runnable ? '✓ Готовая программа Main.java' : 'Фрагмент / схема — контекст указан ниже'}</div>
    ${codeExampleHtml(guide.example, guide.example.label)}
    ${guide.example.stdout !== undefined ? `<div class="theory-stdout-label">ВЫВОД ПРОГРАММЫ</div><pre class="theory-stdout">${escapeHtml(guide.example.stdout)}</pre>` : ''}
    <div class="theory-result"><strong>${guide.example.stdout !== undefined ? 'ПОЧЕМУ ТАК' : 'ЧТО ПРОИЗОЙДЁТ'}</strong><p>${escapeHtml(guide.result)}</p></div>
    ${practice
      ? `<div class="theory-transfer"><strong>КАК ПОДОЙТИ К ЗАДАЧАМ С КОДОМ</strong>
          <ul>${guide.hints.map((hint) => `<li data-practice-task="${escapeHtml(hint.taskId)}"><span>ЗАДАНИЕ ${escapeHtml(hint.taskId.slice(-2))}</span> ${escapeHtml(hint.text)}</li>`).join('')}</ul></div>`
      : `<div class="theory-transfer"><strong>ТЕПЕРЬ ПОПРОБУЙТЕ САМИ</strong><p>${escapeHtml(guide.bridge)}</p></div>`}
  </section>`;
}

function theoryHtml(activity, mode) {
  if (mode === 'lesson' && activity.theory) {
    const language = CODE_LANGUAGE[activity.theory.language] || 'java';
    const exampleLabel = { sql: 'ПРИМЕР SQL', markup: 'ПРИМЕР XML', yaml: 'ПРИМЕР YAML', docker: 'ПРИМЕР DOCKERFILE', text: 'ПРИМЕР' }[language] || 'ПРИМЕР КОДА';
    return `<div class="theory-kicker">${icon('book', 18)} КОНСПЕКТ УРОКА</div>
      <h2>Сначала — разобраться.</h2>
      ${activity.theory.objective ? `<div class="theory-objective"><strong>ПОСЛЕ УРОКА ВЫ СМОЖЕТЕ</strong><span>${escapeHtml(activity.theory.objective)}</span></div>` : ''}
      <button type="button" class="theory-jump" data-jump>Сразу к заданию ↓</button>
      ${activity.theory.paragraphs.map((paragraph) => `<p>${escapeHtml(paragraph)}</p>`).join('')}
      ${activity.theory.deepDive && activity.theory.useCase ? `<section class="theory-expanded" aria-label="Дополнительные объяснения">
        <h3>РАЗОБРАТЬ ГЛУБЖЕ</h3><p>${escapeHtml(activity.theory.deepDive)}</p>
        <h3>ГДЕ ПРИМЕНИТЬ</h3><p>${escapeHtml(activity.theory.useCase)}</p></section>` : ''}
      ${codeExampleHtml(activity.theory, exampleLabel)}
      <small class="theory-caption">${escapeHtml(activity.theory.caption || '')}</small>
      ${activity.theory.walkthrough ? `<div class="theory-note"><strong>РАЗБОР ПРИМЕРА</strong><p>${escapeHtml(activity.theory.walkthrough)}</p></div>` : ''}
      ${activity.theory.guide ? workedExampleHtml(activity.theory.guide) : ''}
      ${activity.theory.pitfall ? `<div class="theory-note is-pitfall"><strong>ЧАСТАЯ ОШИБКА</strong><p>${escapeHtml(activity.theory.pitfall)}</p></div>` : ''}
      <button type="button" class="theory-jump theory-jump-last" data-jump>Перейти к заданию ↓</button>`;
  }
  if (mode === 'practice' && activity.primer) {
    return `<div class="theory-kicker">${icon('spark', 18)} ПРАКТИКА</div>
      <h2>Сначала — разобрать приём.</h2>
      <p>У вас неограниченное число попыток. Потренируйтесь на другом примере, затем решите задания со своими данными.</p>
      <button type="button" class="theory-jump" data-jump>Сразу к заданию ↓</button>
      ${workedExampleHtml(activity.primer, true)}
      <button type="button" class="theory-jump theory-jump-last" data-jump>Перейти к заданию ↓</button>`;
  }
  const guides = {
    practice: ['Время применить знания.', 'Здесь есть тесты, работа со строками кода и настоящая компиляция через javac. Не спешите: после ошибки можно попробовать ещё раз.', 'Каждая решённая задача приближает вас к следующему модулю.'],
    boss: ['Время показать результат.', 'Пять вопросов, одна попытка на каждый. Нужно правильно ответить хотя бы на четыре (80%). Если не получилось — экзамен можно пройти повторно.', 'Практика и успешно сданный стоп-урок вместе открывают следующий модуль.'],
    review: ['Вспомнить — значит закрепить.', 'Вопросы, в которых была ошибка или с которыми вы давно не встречались, возвращаются здесь. Никакой спешки — только полезное повторение.', 'За верное повторение можно получить 5 XP раз в день.'],
  };
  const [title, ...paragraphs] = guides[mode] || guides.practice;
  return `<div class="theory-kicker">${icon(mode === 'boss' ? 'trophy' : 'spark', 18)} ${MODE_LABEL[mode]}</div><h2>${title}</h2>${paragraphs.map((paragraph) => `<p>${paragraph}</p>`).join('')}<div class="theory-decoration" aria-hidden="true">{ <span>java</span> }</div>`;
}

export function renderSession(root, { module, activity, mode, entries, health, onAttempt, onComplete, onExit, optionalOnly = false }) {
  let index = 0;
  let phase = 'answer'; // answer | retry | done
  let pending = false;
  let alive = true;
  let adapter = null;
  let firstTryCorrect = 0;
  let earnedXp = 0;
  const attempted = new Set();
  const startedAt = Date.now();

  function finish() {
    if (!alive) return;
    const total = optionalOnly ? entries.length : entries.filter(({ task }) => !task.optional).length;
    const accuracy = percentage(firstTryCorrect, total);
    const passed = mode !== 'boss' || accuracy >= Math.round((activity.passingScore || 0.8) * 100);
    onComplete({ mode, module, activity, earnedXp, accuracy, passed,
      correctFirstTry: firstTryCorrect, total,
      seconds: Math.round((Date.now() - startedAt) / 1000) });
  }

  function paint() {
    adapter?.destroy();
    if (!alive) return;
    const { task, review } = entries[index];
    const type = task.variant === 'theory' ? 'theory' : task.type;
    const possibleXp = review ? REVIEW_XP : task.difficulty
      ? XP_BY_DIFFICULTY[task.difficulty] : XP_BY_TYPE[type];
    const progress = percentage(index, entries.length);
    root.innerHTML = `<div class="lesson-page page-enter">
      <div class="lesson-topline"><button class="lesson-exit" type="button" data-exit aria-label="Вернуться к модулю">${icon('back', 20)}</button>
        <div class="lesson-progress"><div class="lesson-progress-label"><span>${escapeHtml(module?.title || 'Повторение')}</span><strong>${index + 1} / ${entries.length}</strong></div><div class="progress-track" role="progressbar" aria-valuenow="${index}" aria-valuemin="0" aria-valuemax="${entries.length}" aria-label="Прогресс урока"><div class="progress-fill" style="width:${progress}%"></div></div></div>
      </div>
      <div class="session-heading"><span class="session-overline">${MODE_LABEL[mode]} ${mode === 'lesson' ? `· ${escapeHtml(activity.title)}` : ''}</span><h1>${mode === 'lesson' ? escapeHtml(activity.title) : escapeHtml(activity.title || 'Повторение')}</h1><p>${mode === 'lesson' ? 'Изучите идею слева, затем попробуйте применить её на практике.' : escapeHtml(activity.description || 'Практикуйтесь в удобном для вас темпе.')}</p></div>
      <div class="lesson-layout"><aside class="theory-card">${theoryHtml(activity, mode)}</aside>
        <section class="challenge-card" aria-label="Интерактивное задание"><div class="challenge-topline"><span class="challenge-count">ЗАДАНИЕ ${String(index + 1).padStart(2, '0')} / ${String(entries.length).padStart(2, '0')}</span><span class="xp-chip">${icon('bolt', 16)} ${possibleXp} XP</span></div>
          ${review ? `<div class="review-badge">${icon('refresh', 15)} Вопрос из прошлого модуля</div>` : ''}
          ${task.difficulty || type === 'copy' ? `<div class="challenge-level ${task.optional ? 'is-optional' : ''}">${icon(task.optional ? 'trophy' : 'bolt', 15)} ${escapeHtml(LEVEL_LABEL[task.difficulty] || 'ПЕРЕПИШИ КОД · РАЗМИНКА')}${task.optional ? ' · достижение за успех' : ''}</div>` : ''}
          <div class="challenge-type">${icon(type === 'code' || type === 'copy' ? 'code' : type === 'parsons' ? 'layers' : type === 'matching' ? 'target' : 'book', 19)} ${TASK_LABELS[type]}</div>
          <h2>${escapeHtml(task.prompt)}</h2>
          ${task.type === 'code' && health && !health.jdkAvailable ? `<div class="editor-warning">${icon('info', 17)} ${health.message?.includes('отключён') ? 'Компиляция отключена в сетевом предпросмотре. Запустите тренажёр локально.' : 'Для запуска кода установите JDK 17+ и перезапустите сервер.'}</div>` : ''}
          <div class="interaction interaction-${task.type}" id="interaction"></div>
          <div class="feedback-slot" id="feedback"></div>
          <div class="challenge-actions"><span class="challenge-help">${icon('info', 15)} ${task.optional ? 'Хард не нужен для завершения урока.' : 'Ошибаться можно. Вы справитесь.'}</span>${task.optional ? `<button class="button button-outline" type="button" data-skip>Пропустить хард</button>` : ''}<button class="button button-primary" type="button" data-primary>${task.type === 'code' ? 'Запустить проверку' : 'Проверить ответ'} ${icon('arrow', 19)}</button></div>
        </section></div>
    </div>`;
    adapter = renderQuiz(root.querySelector('#interaction'), task);
    root.querySelector('[data-exit]').addEventListener('click', onExit);
    root.querySelectorAll('[data-jump]').forEach((button) => button.addEventListener('click', () =>
      root.querySelector('.challenge-card').scrollIntoView({ behavior: 'smooth', block: 'start' })));
    root.querySelector('[data-primary]').addEventListener('click', handlePrimary);
    root.querySelector('[data-skip]')?.addEventListener('click', () => {
      if (pending) return;
      if (optionalOnly) onExit();
      else if (index === entries.length - 1) finish();
      else { index++; phase = 'answer'; paint(); }
    });
    if (window.Prism) {
      root.querySelectorAll('.theory-code code').forEach((element) => window.Prism.highlightElement(element));
    }
  }

  async function handlePrimary() {
    if (!alive || pending) return;
    const button = root.querySelector('[data-primary]');
    const feedback = root.querySelector('#feedback');
    const interaction = root.querySelector('#interaction');
    const { task, review } = entries[index];
    if (phase === 'done') {
      if (index === entries.length - 1) finish();
      else {
        index++; phase = 'answer'; paint();
        // Lesson and practice guides are long: read them once, then move to
        // the next task without forcing a scroll through the same text again.
        if (mode === 'lesson' || mode === 'practice') root.querySelector('.challenge-card').scrollIntoView({ behavior: 'smooth', block: 'start' });
        else window.scrollTo({ top: 0, behavior: 'smooth' });
      }
      return;
    }
    if (phase === 'retry') {
      phase = 'answer';
      interaction.classList.remove('is-wrong');
      clearFeedback(feedback);
      button.innerHTML = `${task.type === 'code' ? 'Запустить проверку' : 'Проверить ответ'} ${icon('arrow', 19)}`;
      adapter.focus?.();
      return;
    }

    const answer = adapter.getValue();
    const inputError = validateInput(task, answer);
    if (inputError) {
      showFeedback(feedback, { status: 'notice', title: 'Ещё один шаг', message: inputError });
      return; // Incomplete input is not a recorded attempt.
    }
    if (task.type === 'parsons') {
      const issues = parsonsIndentIssues(task, answer);
      if (issues?.length) {
        adapter.markIndentRows?.(issues.map(({ id }) => id));
        showFeedback(feedback, { status: 'notice', title: 'Порядок строк верный!',
          message: `Осталось выставить отступы кнопками «+» и «−». Нужные уровни строк: ${issues.map(({ row, expected }) => `${row} → ${expected}`).join(', ')}.` });
        return; // Partially correct: keep the layout and don't record a mistake.
      }
    }
    let correct, detail = '', output = '', kind = '';
    if (task.type === 'code') {
      pending = true;
      button.disabled = true;
      button.innerHTML = '<span class="button-spinner"></span> Компилируем…';
      try {
        const result = await submitCode(answer, task.id, task.mode || 'run');
        if (!alive) return;
        correct = result.success;
        detail = result.error;
        output = result.output;
        kind = result.kind;
      } catch (error) {
        if (!alive) return;
        showFeedback(feedback, { status: 'notice', title: 'Не получилось связаться с компилятором', message: error.message });
        button.disabled = false;
        button.innerHTML = `Попробовать снова ${icon('refresh', 18)}`;
        pending = false;
        return; // Infrastructure failures must not record a mistake or earn XP.
      }
      pending = false;
      button.disabled = false;
    } else {
      correct = isCorrect(task, answer);
    }

    const firstTry = !attempted.has(index);
    attempted.add(index);
    const reward = onAttempt(task, correct, review);
    if (correct) {
      if (firstTry && (!task.optional || optionalOnly)) firstTryCorrect++;
      earnedXp += reward.earnedXp;
      adapter.clearDraft?.();
      interaction.classList.remove('is-wrong');
      interaction.classList.add('is-correct');
      showFeedback(feedback, { status: 'success', title: 'Отлично, получилось!', message: task.explanation,
        output: task.type === 'code' ? output : '', xp: reward.earnedXp });
      phase = 'done';
      button.innerHTML = `${index === entries.length - 1 ? 'Завершить' : 'Далее'} ${icon('arrow', 19)}`;
      const bar = root.querySelector('.progress-fill');
      bar.style.width = `${percentage(index + 1, entries.length)}%`;
      bar.parentElement.setAttribute('aria-valuenow', String(index + 1));
    } else {
      interaction.classList.add('is-wrong');
      const specificHint = task.type === 'code' ? codeHint(detail, kind) : null;
      showFeedback(feedback, { status: 'error', title: kind === 'compile_error' ? 'Компилятор нашёл ошибку' : 'Почти получилось',
        message: specificHint || task.hint, detail: detail || '', output });
      // Boss questions remain single-attempt for scoring; regular questions
      // can be retried as often as needed without blocking access.
      phase = mode === 'boss' ? 'done' : 'retry';
      button.innerHTML = `${phase === 'done' ? 'Следующий вопрос' : 'Попробовать снова'} ${icon(phase === 'retry' ? 'refresh' : 'arrow', 19)}`;
      if (mode === 'boss') {
        const bar = root.querySelector('.progress-fill');
        bar.style.width = `${percentage(index + 1, entries.length)}%`;
        bar.parentElement.setAttribute('aria-valuenow', String(index + 1));
      }
    }
  }

  paint();
  return () => { alive = false; adapter?.destroy(); };
}
