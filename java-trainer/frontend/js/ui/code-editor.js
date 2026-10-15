import { clearDraft, readDraft, saveDraft } from '../storage.js';
import { debounce, escapeHtml } from '../utils/helpers.js';

// CodeMirror is bundled locally; a native textarea remains usable if a script
// fails to load (e.g. an older browser). Drafts are saved after a short pause.
export function renderCodeEditor(host, task) {
  const copy = task.type === 'copy';
  const language = copy ? task.language || 'java' : 'java';
  const name = { java: 'Main.java', xml: 'pom.xml', sql: 'query.sql', yaml: 'pipeline.yml',
    dockerfile: 'Dockerfile', text: 'example.txt' }[language] || 'Main.java';
  const textarea = document.createElement('textarea');
  textarea.className = 'code-textarea';
  textarea.id = `editor-${task.id}`;
  textarea.setAttribute('aria-label', copy ? 'Перепишите пример здесь' : 'Редактор Java-кода');
  textarea.spellcheck = false;
  textarea.value = readDraft(task.id) ?? task.starterCode ?? '';
  host.innerHTML = `
    ${copy ? `<div class="copy-reference"><strong>ОБРАЗЕЦ · ${escapeHtml(name)}</strong><pre>${escapeHtml(task.sourceCode)}</pre></div>` : ''}
    <div class="editor-toolbar">
      <div class="editor-filename"><span class="editor-filedot"></span>${escapeHtml(name)}</div>
      <span class="editor-lang">${copy ? 'ВАША КОПИЯ' : 'JAVA'}</span>
    </div>
    <div class="editor-body"></div>
    <div class="editor-footnote">${escapeHtml(copy
      ? 'Перепишите сами: Java допускает другой стиль отступов; в YAML отступы важны. Черновик сохраняется.'
      : 'Код выполняется только на вашем компьютере • лимит 5 секунд')}</div>`;
  host.querySelector('.editor-body').append(textarea);

  const persist = debounce(() => saveDraft(task.id, editor ? editor.getValue() : textarea.value), 350);
  let editor = null;
  if (window.CodeMirror?.fromTextArea && language === 'java') {
    editor = window.CodeMirror.fromTextArea(textarea, {
      mode: 'text/x-java', lineNumbers: true, lineWrapping: true,
      indentUnit: 4, tabSize: 4, indentWithTabs: false,
      extraKeys: { Tab: (instance) => instance.replaceSelection('    ') },
    });
    editor.setSize('100%', '300px');
    editor.on('change', persist);
    // The editor is created inside an already mounted host.
    requestAnimationFrame(() => editor?.refresh());
  } else {
    textarea.addEventListener('input', persist);
  }
  return {
    getValue: () => editor ? editor.getValue() : textarea.value,
    focus: () => editor ? editor.focus() : textarea.focus(),
    clearDraft: () => { persist.cancel(); clearDraft(task.id); },
    destroy: () => {
      persist.cancel();
      if (editor) { editor.off('change', persist); editor.toTextArea(); editor = null; }
      else textarea.removeEventListener('input', persist);
    },
  };
}
