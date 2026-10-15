import { escapeHtml } from '../utils/helpers.js';
import { downloadCalendarReminder, isValidReminderTime, requestBrowserReminders } from '../reminders.js';
import { icon } from './icons.js';

export function renderSettings(root, { state, health, onThemeChange, onReminderChange, onReset, onBack }) {
  const dark = state.theme === 'dark';
  const enabled = state.remindersEnabled;
  const blocked = enabled && (globalThis.isSecureContext === false
    || globalThis.Notification?.permission !== 'granted');
  root.innerHTML = `<div class="settings-page page-enter"><button type="button" class="back-link" data-back>${icon('back', 18)} К карте курса</button>
    <div class="page-heading"><div><div class="eyebrow"><span class="eyebrow-line"></span> ВАШ ТРЕНАЖЁР</div><h1>Настройки <em>и помощь.</em></h1><p>Учитесь так, как удобно именно вам.</p></div></div>
    <div class="settings-grid">
      <section class="settings-panel"><span class="section-overline">ИНТЕРФЕЙС</span><h2>Внешний вид</h2><div class="setting-row"><span class="setting-icon">${icon(dark ? 'moon' : 'sun', 22)}</span><span class="setting-text"><strong>Тёмная тема</strong><small>Бережнее к глазам вечером</small></span><button type="button" role="switch" aria-label="Тёмная тема" aria-checked="${dark}" class="toggle ${dark ? 'is-on' : ''}" data-theme><span></span></button></div><div class="settings-divider"></div><span class="section-overline">ПРОГРЕСС</span><h2>Ваши данные</h2><p>XP, уроки, история ответов, настройки и черновики хранятся только в localStorage этого браузера. Сервер не создаёт учётную запись.</p><button type="button" class="button button-danger-outline" data-reset>Сбросить прогресс и черновики</button></section>
      <section class="settings-panel"><span class="section-overline">НАСТРОЙКА КОМПИЛЯТОРА</span><h2>Пишите настоящую Java</h2><div class="jdk-status ${health?.jdkAvailable ? 'is-ready' : ''}">${icon(health?.jdkAvailable ? 'check' : 'warning', 20)} ${health?.jdkAvailable ? 'JDK готов к работе' : health?.message?.includes('отключён') ? 'Компиляция отключена в предпросмотре' : 'JDK пока не найден'}</div><p>Для заданий с кодом установите <strong>JDK 17 или новее</strong>. Убедитесь, что команды <code>java -version</code> и <code>javac -version</code> работают в том же терминале, где запускается сервер.</p><div class="setup-commands"><code>pip install -r requirements.txt</code><code>python server.py</code></div><p class="settings-footnote">${escapeHtml(health?.message || 'Если JDK установлен, перезапустите сервер и обновите страницу.')}</p></section>
      <section class="settings-panel reminders-panel"><span class="section-overline">ПРИВЫЧКА УЧИТЬСЯ</span><h2>Напоминания о занятиях</h2><p>Выберите время по часам вашего устройства. Напоминания не включаются без вашего согласия.</p>
        <label class="reminder-label" for="reminder-time">Каждый день в</label><input class="reminder-time" id="reminder-time" type="time" value="${escapeHtml(state.reminderTime)}" required>
        <div class="setting-row reminder-switch-row"><span class="setting-icon">${icon('clock', 22)}</span><span class="setting-text"><strong>Уведомления браузера</strong><small>Пока вкладка работает; спящий браузер не отправит</small></span><button type="button" role="switch" aria-label="Уведомления браузера" aria-checked="${enabled}" class="toggle ${enabled ? 'is-on' : ''}" data-reminders><span></span></button></div>
        <p class="reminder-status" role="status" aria-live="polite" data-reminder-status>${blocked ? 'Настройка сохранена, но браузер больше не разрешает уведомления. Проверьте разрешение сайта или используйте календарь.' : enabled ? 'Включены в этом браузере. Для доставки вкладка должна оставаться открытой и работать.' : 'Выключены. Нажмите переключатель, чтобы запросить разрешение браузера.'}</p>
        <div class="settings-divider"></div><h3>Напоминать, даже когда сайт закрыт</h3><p>Скачайте событие и <strong>импортируйте .ics в календарь</strong>. Оно повторяется ежедневно в выбранное время; доставка зависит от приложения календаря. Смена времени здесь не меняет уже импортированное событие — удалите старое и импортируйте новое.</p><button type="button" class="button button-outline" data-calendar>${icon('calendar', 18)} Скачать событие .ics</button>
      </section>
      <section class="settings-panel safety-panel"><span class="section-overline">ВАЖНО О БЕЗОПАСНОСТИ</span><h2>Только ваш код, только локально</h2><p>Java-программы запускаются на вашем ноутбуке с вашими правами. Таймаут и лимиты ресурсов не заменяют изолированную песочницу. Не вставляйте чужой код и не открывайте сервер в публичной сети.</p><div class="safety-detail">${icon('info', 19)} По умолчанию сервер слушает только 127.0.0.1.</div></section>
    </div>
  </div>`;
  root.querySelector('[data-back]').addEventListener('click', onBack);
  root.querySelector('[data-theme]').addEventListener('click', () => onThemeChange(dark ? 'light' : 'dark'));
  root.querySelector('[data-reset]').addEventListener('click', () => {
    if (window.confirm('Удалить весь прогресс, историю ответов, настройки напоминаний и черновики кода в этом браузере? Импортированное событие в календаре удаляется отдельно.')) onReset();
  });

  const time = root.querySelector('#reminder-time');
  const toggle = root.querySelector('[data-reminders]');
  const status = root.querySelector('[data-reminder-status]');
  const message = (text) => { status.textContent = text; };
  time.addEventListener('change', () => {
    if (!isValidReminderTime(time.value)) { message('Выберите корректное время.'); return; }
    onReminderChange({ reminderTime: time.value });
    message('Время сохранено. Если событие уже импортировано в календарь, замените его вручную.');
  });
  toggle.addEventListener('click', async () => {
    if (toggle.getAttribute('aria-checked') === 'true') {
      onReminderChange({ remindersEnabled: false });
      toggle.setAttribute('aria-checked', 'false');
      toggle.classList.remove('is-on');
      message('Браузерные напоминания выключены. Импортированный календарь настраивается отдельно.');
      return;
    }
    if (!isValidReminderTime(time.value)) { message('Сначала выберите корректное время.'); return; }
    // The permission prompt follows this explicit user click, never page load.
    toggle.disabled = true;
    const permission = await requestBrowserReminders();
    if (!toggle.isConnected) return;
    toggle.disabled = false;
    message(permission.message);
    if (!permission.ok) return;
    onReminderChange({ remindersEnabled: true, reminderTime: time.value });
    toggle.setAttribute('aria-checked', 'true');
    toggle.classList.add('is-on');
  });
  root.querySelector('[data-calendar]').addEventListener('click', () => {
    if (!isValidReminderTime(time.value)) { message('Сначала выберите корректное время.'); return; }
    try {
      onReminderChange({ reminderTime: time.value });
      downloadCalendarReminder(time.value);
      message('Файл скачан. Импортируйте его в календарь: одного скачивания для напоминания недостаточно.');
    } catch {
      message('Не удалось скачать файл. Попробуйте открыть тренажёр в обычной вкладке браузера.');
    }
  });
}
