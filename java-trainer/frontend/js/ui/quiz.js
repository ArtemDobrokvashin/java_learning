import { escapeHtml } from '../utils/helpers.js';
import { renderCodeEditor } from './code-editor.js';
import { renderMatching, renderParsons } from './drag-drop.js';

function renderChoice(host, task) {
  host.innerHTML = `<div class="answer-list" role="group" aria-label="${task.multiple ? 'Выберите все верные ответы' : 'Выберите один ответ'}">
    ${task.options.map((option, index) => `<label class="answer-option">
      <input type="${task.multiple ? 'checkbox' : 'radio'}" name="answer-${escapeHtml(task.id)}" value="${escapeHtml(option.id)}" />
      <span class="answer-letter">${String.fromCharCode(65 + index)}</span>
      <span class="answer-text">${escapeHtml(option.label)}</span>
      <span class="answer-check" aria-hidden="true">✓</span>
    </label>`).join('')}
  </div>${task.multiple ? '<p class="interaction-help">Можно выбрать несколько вариантов.</p>' : ''}`;
  return {
    getValue: () => [...host.querySelectorAll('input:checked')].map((input) => input.value),
    focus: () => host.querySelector('input')?.focus(),
    destroy: () => {},
  };
}

function renderFill(host, task) {
  const parts = task.template.map((fragment, index) => {
    const blank = task.blanks[index];
    let input = '';
    if (blank) {
      const label = `Пропуск ${index + 1}`;
      input = blank.kind === 'select'
        ? `<select class="blank-input" data-blank="${escapeHtml(blank.id)}" aria-label="${label}"><option value="" selected disabled>выбрать…</option>${blank.options.map((option) => `<option value="${escapeHtml(option)}">${escapeHtml(option)}</option>`).join('')}</select>`
        : `<input class="blank-input blank-text" type="text" data-blank="${escapeHtml(blank.id)}" aria-label="${label}" autocomplete="off" spellcheck="false" placeholder="ответ" />`;
    }
    return `<span class="fill-fragment">${escapeHtml(fragment)}</span>${input}`;
  }).join('');
  host.innerHTML = `<div class="fill-editor"><div class="fill-editor-top"><span class="editor-filedot"></span> Фрагмент задания</div><div class="fill-line">${parts}</div></div>`;
  return {
    getValue: () => Object.fromEntries([...host.querySelectorAll('[data-blank]')]
      .map((element) => [element.dataset.blank, element.value.trim()])),
    focus: () => host.querySelector('[data-blank]')?.focus(),
    destroy: () => {},
  };
}

export function renderQuiz(host, task) {
  switch (task.type) {
    case 'choice': return renderChoice(host, task);
    case 'fill': return renderFill(host, task);
    case 'matching': return renderMatching(host, task);
    case 'parsons': return renderParsons(host, task);
    case 'code':
    case 'copy': return renderCodeEditor(host, task);
    default: throw new Error(`Неизвестный тип задания: ${task.type}`);
  }
}
