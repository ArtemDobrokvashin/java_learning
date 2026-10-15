import test from 'node:test';
import assert from 'node:assert/strict';
import { freshState, loadState, resetState, saveState } from '../../frontend/js/storage.js';
import { calendarReminder, isValidReminderTime, requestBrowserReminders, sendDueReminder,
  shouldRemind } from '../../frontend/js/reminders.js';
import { renderSettings } from '../../frontend/js/ui/settings.js';

let JSDOM;
try { ({ JSDOM } = await import('jsdom')); } catch { /* Optional browser UI tests. */ }

const localTime = (day, hour, minute = 0) => new Date(2026, 9, day, hour, minute);

test('reminders are opt-in, local-time based, once per day and never late by hours', () => {
  const state = freshState();
  assert.equal(state.remindersEnabled, false);
  assert.equal(state.reminderTime, '19:00');
  assert.ok(!shouldRemind(state, localTime(14, 19, 0)));
  state.remindersEnabled = true;
  assert.ok(!shouldRemind(state, localTime(14, 18, 59)));
  assert.ok(shouldRemind(state, localTime(14, 19, 0)));
  assert.ok(shouldRemind(state, localTime(14, 19, 59)));
  assert.ok(!shouldRemind(state, localTime(14, 20, 0)));
  class NotificationMock {
    static permission = 'granted';
    static calls = [];
    constructor(title, options) { NotificationMock.calls.push({ title, options }); }
  }
  assert.equal(sendDueReminder(state, { now: localTime(14, 19, 10), NotificationApi: NotificationMock }), true);
  assert.equal(state.lastReminderDay, '2026-10-14');
  assert.equal(NotificationMock.calls.length, 1);
  assert.match(NotificationMock.calls[0].options.body, /один небольшой шаг/);
  assert.ok(!sendDueReminder(state, { now: localTime(14, 19, 11), NotificationApi: NotificationMock }));
  assert.ok(shouldRemind(state, localTime(15, 19, 0)));
  state.lastActiveDate = '2026-10-15';
  assert.ok(!shouldRemind(state, localTime(15, 19, 0)), 'no reminder after studying today');
});

test('blocked or unsupported notifications never mark a reminder as delivered', async () => {
  const state = { ...freshState(), remindersEnabled: true };
  class Blocked {
    static permission = 'denied';
    static requestPermission() { throw new Error('should not be called'); }
  }
  assert.match((await requestBrowserReminders(Blocked)).message, /заблокированы/);
  assert.equal(sendDueReminder(state, { now: localTime(14, 19, 0), NotificationApi: Blocked }), false);
  assert.equal(state.lastReminderDay, null);
  const unsupported = await requestBrowserReminders({ permission: 'default' });
  assert.equal(unsupported.ok, false);
  class Allowed {
    static permission = 'default';
    static requests = 0;
    static async requestPermission() { this.requests++; this.permission = 'granted'; return 'granted'; }
  }
  assert.equal((await requestBrowserReminders(Allowed)).ok, true);
  assert.equal(Allowed.requests, 1);
  assert.equal((await requestBrowserReminders(Allowed)).ok, true);
  assert.equal(Allowed.requests, 1, 'permission is requested only once');
  class Failing {
    static permission = 'granted';
    constructor() { throw new Error('embedded preview blocked constructor'); }
  }
  assert.equal(sendDueReminder(state, { now: localTime(14, 19, 0), NotificationApi: Failing }), false);
  assert.equal(state.lastReminderDay, null);
});

test('calendar export recurs at local time with an alarm, safe folding and no backend', () => {
  assert.ok(isValidReminderTime('00:00') && isValidReminderTime('23:59'));
  for (const time of ['24:00', '19:60', '9:00', '19:00\nBEGIN:VEVENT']) {
    assert.ok(!isValidReminderTime(time));
    assert.throws(() => calendarReminder(time), RangeError);
  }
  const ics = calendarReminder('19:00', localTime(14, 19, 30));
  assert.ok(ics.startsWith('BEGIN:VCALENDAR\r\n'));
  assert.match(ics, /DTSTART:20261015T190000\r\n/);
  assert.match(ics, /RRULE:FREQ=DAILY\r\n/);
  assert.match(ics, /BEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:PT0M/);
  assert.match(ics, /UID:javada-daily-study@local/);
  assert.ok(ics.endsWith('END:VCALENDAR\r\n'));
  for (const line of ics.split('\r\n')) {
    assert.ok(new TextEncoder().encode(line).length <= 75, 'RFC 5545 lines must be folded');
  }
  assert.match(calendarReminder('19:00', localTime(14, 18, 0)), /DTSTART:20261014T190000/);
});

test('calendar keeps the chosen wall-clock hour across a daylight-saving change', () => {
  const previousTimezone = process.env.TZ;
  try {
    process.env.TZ = 'Europe/Amsterdam';
    const ics = calendarReminder('19:00', new Date(2026, 2, 28, 21, 0));
    assert.match(ics, /DTSTART:20260329T190000/);
    const skippedHour = calendarReminder('02:30', new Date(2026, 2, 29, 1, 0));
    assert.match(skippedHour, /DTSTART:20260330T023000/,
      'a nonexistent hour is skipped, not shifted to 03:30 forever');
  } finally {
    if (previousTimezone === undefined) delete process.env.TZ;
    else process.env.TZ = previousTimezone;
  }
});

test('reminder choice survives reload and reset opts out again', () => {
  const original = globalThis.localStorage;
  const values = new Map();
  globalThis.localStorage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
    key: (index) => [...values.keys()][index],
    get length() { return values.size; },
  };
  try {
    const state = { ...freshState(), remindersEnabled: true, reminderTime: '08:30',
      lastReminderDay: '2026-10-14' };
    saveState(state);
    assert.equal(loadState().remindersEnabled, true);
    assert.equal(loadState().reminderTime, '08:30');
    assert.equal(loadState().lastReminderDay, '2026-10-14');
    assert.equal(resetState().remindersEnabled, false);
    assert.equal(loadState().reminderTime, '19:00');
  } finally {
    if (original === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = original;
  }
});

test('settings ask permission only on click, save chosen time and allow disabling', { skip: !JSDOM }, async () => {
  const dom = new JSDOM('<main id="view"></main>', { url: 'http://127.0.0.1:8000' });
  const prior = { window: globalThis.window, document: globalThis.document, Notification: globalThis.Notification };
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  class NotificationMock {
    static permission = 'default';
    static requests = 0;
    static async requestPermission() { this.requests++; this.permission = 'granted'; return 'granted'; }
  }
  globalThis.Notification = NotificationMock;
  try {
    const state = freshState();
    const root = document.querySelector('#view');
    renderSettings(root, { state, health: { jdkAvailable: false, message: 'Нет JDK' },
      onThemeChange: () => {}, onReminderChange: (changes) => Object.assign(state, changes),
      onReset: () => {}, onBack: () => {} });
    const time = root.querySelector('#reminder-time');
    const toggle = root.querySelector('[data-reminders]');
    assert.equal(NotificationMock.requests, 0);
    assert.equal(toggle.getAttribute('aria-checked'), 'false');
    time.value = '21:15';
    time.dispatchEvent(new dom.window.Event('change', { bubbles: true }));
    assert.equal(state.reminderTime, '21:15');
    toggle.click();
    await new Promise(resolve => setTimeout(resolve, 0));
    assert.equal(NotificationMock.requests, 1);
    assert.equal(state.remindersEnabled, true);
    assert.equal(toggle.getAttribute('aria-checked'), 'true');
    toggle.click();
    assert.equal(state.remindersEnabled, false);
    assert.equal(toggle.getAttribute('aria-checked'), 'false');
    NotificationMock.permission = 'denied';
    toggle.click();
    await new Promise(resolve => setTimeout(resolve, 0));
    assert.equal(state.remindersEnabled, false);
    assert.match(root.querySelector('[data-reminder-status]').textContent, /заблокированы/);
    assert.ok(root.querySelector('[data-calendar]'), 'calendar fallback remains available');
    const originalCreate = URL.createObjectURL;
    const originalClick = dom.window.HTMLAnchorElement.prototype.click;
    let exported;
    let downloadedName;
    try {
      URL.createObjectURL = (blob) => { exported = blob; return 'blob:javada-test'; };
      dom.window.HTMLAnchorElement.prototype.click = function () { downloadedName = this.download; };
      root.querySelector('[data-calendar]').click();
      assert.equal(downloadedName, 'javada-daily-reminder.ics');
      assert.match(await exported.text(), /DTSTART:\d{8}T211500/);
      assert.match(root.querySelector('[data-reminder-status]').textContent, /Импортируйте/);
      state.remindersEnabled = true;
      renderSettings(root, { state, health: null, onThemeChange: () => {},
        onReminderChange: (changes) => Object.assign(state, changes), onReset: () => {}, onBack: () => {} });
      assert.match(root.querySelector('[data-reminder-status]').textContent, /больше не разрешает/);
    } finally {
      URL.createObjectURL = originalCreate;
      dom.window.HTMLAnchorElement.prototype.click = originalClick;
    }
  } finally {
    for (const [key, value] of Object.entries(prior)) {
      if (value === undefined) delete globalThis[key];
      else globalThis[key] = value;
    }
    dom.window.close();
  }
});
