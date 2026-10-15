import { escapeHtml, shuffle } from '../utils/helpers.js';
import { parsonsIndentIssues } from '../validator.js';
import { icon } from './icons.js';

const PAIR_COLORS = ['#58b71a', '#805ee9', '#e8a425', '#238bb1', '#e37868'];

// Click-to-connect works with a mouse, touch screen and keyboard. SVG curves
// are decoration; the numbered, coloured buttons also show each connection.
export function renderMatching(host, task) {
  const right = shuffle(task.pairs);
  const links = new Map(); // left ID -> right ID
  let selected = null;
  host.innerHTML = `
    <p class="interaction-help">Нажмите понятие слева, затем описание справа. Нажмите на пару ещё раз, чтобы изменить её.</p>
    <div class="match-stage">
      <div class="match-column" aria-label="Понятия">
        ${task.pairs.map((pair, index) => `<button type="button" class="match-item match-left" data-left="${escapeHtml(pair.id)}" aria-pressed="false"><span class="match-index">${String(index + 1).padStart(2, '0')}</span><span>${escapeHtml(pair.left)}</span></button>`).join('')}
      </div>
      <svg class="match-lines" aria-hidden="true"></svg>
      <div class="match-column" aria-label="Описания">
        ${right.map((pair) => `<button type="button" class="match-item match-right" data-right="${escapeHtml(pair.id)}" aria-pressed="false"><span>${escapeHtml(pair.right)}</span><span class="match-endpoint"></span></button>`).join('')}
      </div>
    </div>`;
  const stage = host.querySelector('.match-stage');
  const svg = host.querySelector('.match-lines');
  const help = host.querySelector('.interaction-help');

  function drawLines() {
    const bounds = stage.getBoundingClientRect();
    svg.setAttribute('viewBox', `0 0 ${bounds.width} ${bounds.height}`);
    svg.setAttribute('width', bounds.width);
    svg.setAttribute('height', bounds.height);
    svg.replaceChildren();
    for (const [leftId, rightId] of links) {
      const left = [...host.querySelectorAll('[data-left]')].find((button) => button.dataset.left === leftId);
      const rightButton = [...host.querySelectorAll('[data-right]')].find((button) => button.dataset.right === rightId);
      if (!left || !rightButton) continue;
      const from = left.getBoundingClientRect();
      const to = rightButton.getBoundingClientRect();
      const x1 = from.right - bounds.left + 3, y1 = from.top - bounds.top + from.height / 2;
      const x2 = to.left - bounds.left - 3, y2 = to.top - bounds.top + to.height / 2;
      const line = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      const middle = (x1 + x2) / 2;
      line.setAttribute('d', `M${x1} ${y1} C${middle} ${y1}, ${middle} ${y2}, ${x2} ${y2}`);
      line.setAttribute('stroke', PAIR_COLORS[task.pairs.findIndex((pair) => pair.id === leftId) % PAIR_COLORS.length]);
      line.setAttribute('stroke-width', '3');
      line.setAttribute('stroke-linecap', 'round');
      line.setAttribute('fill', 'none');
      svg.append(line);
    }
  }

  function update() {
    for (const [index, pair] of task.pairs.entries()) {
      const button = [...host.querySelectorAll('[data-left]')].find((element) => element.dataset.left === pair.id);
      button.classList.toggle('is-selected', selected === pair.id);
      button.classList.toggle('is-paired', links.has(pair.id));
      button.style.setProperty('--pair-color', PAIR_COLORS[index % PAIR_COLORS.length]);
      button.setAttribute('aria-pressed', String(selected === pair.id || links.has(pair.id)));
    }
    for (const button of host.querySelectorAll('[data-right]')) {
      const leftId = [...links].find(([, rightId]) => rightId === button.dataset.right)?.[0];
      button.classList.toggle('is-paired', !!leftId);
      button.setAttribute('aria-pressed', String(!!leftId));
      if (leftId) {
        const index = task.pairs.findIndex((pair) => pair.id === leftId);
        button.style.setProperty('--pair-color', PAIR_COLORS[index % PAIR_COLORS.length]);
        button.querySelector('.match-endpoint').textContent = String(index + 1);
      } else {
        button.style.removeProperty('--pair-color');
        button.querySelector('.match-endpoint').textContent = '';
      }
    }
    help.textContent = selected ? 'Теперь выберите описание справа.' : `${links.size} из ${task.pairs.length} пар соединено`;
    requestAnimationFrame(drawLines);
  }

  function onClick(event) {
    const left = event.target.closest('[data-left]');
    const rightButton = event.target.closest('[data-right]');
    if (left) {
      selected = left.dataset.left;
      links.delete(selected);
    } else if (rightButton && selected) {
      for (const [leftId, rightId] of links) if (rightId === rightButton.dataset.right) links.delete(leftId);
      links.set(selected, rightButton.dataset.right);
      selected = null;
    } else return;
    update();
  }

  host.addEventListener('click', onClick);
  window.addEventListener('resize', drawLines);
  const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(drawLines) : null;
  observer?.observe(stage);
  update();
  return {
    getValue: () => Object.fromEntries(links),
    focus: () => host.querySelector('.match-left')?.focus(),
    destroy: () => { host.removeEventListener('click', onClick); window.removeEventListener('resize', drawLines); observer?.disconnect(); },
  };
}

// HTML drag-and-drop for pointer users; explicit up/down and indent controls
// provide an equally complete keyboard/touch alternative.
export function renderParsons(host, task) {
  const order = shuffle(task.lines.map((line) => line.id));
  if (order.length > 1 && order.every((id, index) => id === task.solution[index].id)) {
    [order[0], order[1]] = [order[1], order[0]];
  }
  const indents = new Map(order.map((id) => [id, 0]));
  const lines = new Map(task.lines.map((line) => [line.id, line.text]));
  let dragging = null;
  let markedIndents = new Set();
  host.innerHTML = `<p class="interaction-help">Проверяются и порядок, и отступы. Перетаскивайте строки или используйте стрелки ↑ ↓; кнопки − и + меняют уровень отступа (число между ними).</p><div class="parsons-list" role="list" aria-label="Строки задания"></div>`;
  const list = host.querySelector('.parsons-list');

  function draw(focusId, focusAction) {
    list.innerHTML = order.map((id, index) => `
      <div class="parsons-row ${markedIndents.has(id) ? 'indent-needs-work' : ''}" role="listitem" draggable="true" data-line="${escapeHtml(id)}">
        <span class="drag-grip" aria-hidden="true">${icon('grip', 18)}</span>
        <div class="line-content" style="--indent:${indents.get(id)}"><span class="line-number">${index + 1}.</span><code>${escapeHtml(lines.get(id))}</code></div>
        <div class="line-controls">
          <button type="button" data-action="out" aria-label="Уменьшить отступ строки ${index + 1}" ${indents.get(id) === 0 ? 'disabled' : ''}>−</button>
          <span class="indent-count" title="Уровень отступа">${indents.get(id)}</span>
          <button type="button" data-action="in" aria-label="Увеличить отступ строки ${index + 1}" ${indents.get(id) === 3 ? 'disabled' : ''}>+</button>
          <button type="button" data-action="up" aria-label="Поднять строку ${index + 1}" ${index === 0 ? 'disabled' : ''}>↑</button>
          <button type="button" data-action="down" aria-label="Опустить строку ${index + 1}" ${index === order.length - 1 ? 'disabled' : ''}>↓</button>
        </div>
      </div>`).join('');
    if (focusId && focusAction) {
      [...list.querySelectorAll('.parsons-row')]
        .find((row) => row.dataset.line === focusId)?.querySelector(`[data-action="${focusAction}"]`)?.focus();
    }
  }

  function onClick(event) {
    const button = event.target.closest('[data-action]');
    const row = button?.closest('[data-line]');
    if (!row) return;
    const id = row.dataset.line;
    const index = order.indexOf(id);
    if (button.dataset.action === 'up' && index > 0) [order[index - 1], order[index]] = [order[index], order[index - 1]];
    if (button.dataset.action === 'down' && index < order.length - 1) [order[index + 1], order[index]] = [order[index], order[index + 1]];
    if (button.dataset.action === 'in') indents.set(id, Math.min(3, indents.get(id) + 1));
    if (button.dataset.action === 'out') indents.set(id, Math.max(0, indents.get(id) - 1));
    if (['in', 'out'].includes(button.dataset.action) && markedIndents.size) {
      // Re-evaluate against whichever valid order the learner has assembled,
      // not the canonical solution row (which may be a different line).
      const issues = parsonsIndentIssues(task, order.map((lineId) =>
        ({ id: lineId, indent: indents.get(lineId) })));
      markedIndents = new Set(issues?.map(({ id: issueId }) => issueId) || []);
    }
    if (['up', 'down'].includes(button.dataset.action)) markedIndents.clear();
    draw(id, button.dataset.action);
  }

  function onDragStart(event) {
    const row = event.target.closest('[data-line]');
    if (!row) return;
    dragging = row.dataset.line;
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', dragging);
    row.classList.add('is-dragging');
  }
  function onDragOver(event) {
    const row = event.target.closest('[data-line]');
    if (!row || !dragging) return;
    event.preventDefault();
    list.querySelectorAll('.drag-over').forEach((entry) => entry.classList.remove('drag-over'));
    row.classList.add('drag-over');
  }
  function onDrop(event) {
    const row = event.target.closest('[data-line]');
    if (!row || !dragging) return;
    event.preventDefault();
    const target = row.dataset.line;
    if (target !== dragging) {
      const after = event.clientY > row.getBoundingClientRect().top + row.offsetHeight / 2;
      order.splice(order.indexOf(dragging), 1);
      order.splice(order.indexOf(target) + (after ? 1 : 0), 0, dragging);
    }
    dragging = null;
    markedIndents.clear();
    draw();
  }
  function onDragEnd() {
    dragging = null;
    list.querySelectorAll('.drag-over, .is-dragging').forEach((row) => row.classList.remove('drag-over', 'is-dragging'));
  }
  list.addEventListener('click', onClick);
  list.addEventListener('dragstart', onDragStart);
  list.addEventListener('dragover', onDragOver);
  list.addEventListener('drop', onDrop);
  list.addEventListener('dragend', onDragEnd);
  draw();
  return {
    getValue: () => order.map((id) => ({ id, indent: indents.get(id) })),
    focus: () => list.querySelector('[data-action="down"]')?.focus(),
    markIndentRows: (ids) => {
      markedIndents = new Set(ids);
      list.querySelectorAll('.parsons-row').forEach((row) =>
        row.classList.toggle('indent-needs-work', markedIndents.has(row.dataset.line)));
    },
    destroy: () => {
      list.removeEventListener('click', onClick);
      list.removeEventListener('dragstart', onDragStart);
      list.removeEventListener('dragover', onDragOver);
      list.removeEventListener('drop', onDrop);
      list.removeEventListener('dragend', onDragEnd);
    },
  };
}
