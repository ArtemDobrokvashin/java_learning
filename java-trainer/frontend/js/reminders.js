import { dayKey } from './utils/helpers.js';

// Browser notifications need an open page. For reminders when the page is
// closed, the user can import a recurring event into their own calendar.
export function isValidReminderTime(value) {
  return typeof value === 'string' && /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value);
}

export function shouldRemind(state, now = new Date()) {
  if (!state.remindersEnabled || !isValidReminderTime(state.reminderTime)) return false;
  const today = dayKey(now);
  if (state.lastReminderDay === today || state.lastActiveDate === today) return false;
  const [hour, minute] = state.reminderTime.split(':').map(Number);
  const scheduled = hour * 60 + minute;
  const current = now.getHours() * 60 + now.getMinutes();
  // Do not fire a stale notification hours after the scheduled time if a
  // laptop wakes up or a previously closed tab is reopened late at night.
  return current >= scheduled && current < scheduled + 60;
}

export async function requestBrowserReminders(NotificationApi = globalThis.Notification) {
  if (globalThis.isSecureContext === false || typeof NotificationApi !== 'function'
    || typeof NotificationApi.requestPermission !== 'function') {
    return { ok: false, message: 'Браузерные уведомления недоступны здесь. Воспользуйтесь экспортом в календарь.' };
  }
  if (NotificationApi.permission === 'denied') {
    return { ok: false, message: 'Уведомления заблокированы в браузере. Разрешите их в настройках сайта или используйте календарь.' };
  }
  try {
    const permission = NotificationApi.permission === 'granted'
      ? 'granted' : await NotificationApi.requestPermission();
    return permission === 'granted'
      ? { ok: true, message: 'Браузерные напоминания включены.' }
      : { ok: false, message: 'Разрешение не получено. Можно импортировать напоминание в календарь.' };
  } catch {
    return { ok: false, message: 'Браузер не разрешил уведомления. Можно импортировать напоминание в календарь.' };
  }
}

export function sendDueReminder(state, { now = new Date(), NotificationApi = globalThis.Notification } = {}) {
  if (NotificationApi?.permission !== 'granted' || !shouldRemind(state, now)) return false;
  try {
    new NotificationApi('Время для Java', {
      body: 'Загляните в Javada: один небольшой шаг сегодня поможет закрепить материал.',
      tag: 'javada-daily-study',
    });
    state.lastReminderDay = dayKey(now);
    return true;
  } catch {
    // Embedded previews and some browsers disallow constructing notifications
    // even after permission is granted. Never mark an undelivered reminder.
    return false;
  }
}

function calendarDate(time, now) {
  const [hour, minute] = time.split(':').map(Number);
  const startDay = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 12);
  const scheduled = hour * 60 + minute;
  if (now.getHours() * 60 + now.getMinutes() >= scheduled) startDay.setDate(startDay.getDate() + 1);
  // Skip a nonexistent local hour on a spring DST transition. Never change
  // the recurring hour to the Date object's normalized time (e.g. 03:30).
  while (true) {
    const occurrence = new Date(startDay.getFullYear(), startDay.getMonth(), startDay.getDate(), hour, minute);
    if (dayKey(occurrence) === dayKey(startDay)
      && occurrence.getHours() === hour && occurrence.getMinutes() === minute) break;
    startDay.setDate(startDay.getDate() + 1);
  }
  return `${dayKey(startDay).replaceAll('-', '')}T${time.replace(':', '')}00`;
}

function foldIcs(line) {
  const encoder = new TextEncoder();
  let result = '';
  let bytes = 0;
  for (const char of line) {
    const size = encoder.encode(char).length;
    if (bytes + size > 75) { result += '\r\n '; bytes = 1; }
    result += char;
    bytes += size;
  }
  return result;
}

export function calendarReminder(time, now = new Date()) {
  if (!isValidReminderTime(time)) throw new RangeError('Некорректное время напоминания');
  // Floating local time (no Z/TZID) repeats at the chosen hour in the
  // calendar's timezone, even across daylight-saving changes.
  const lines = [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Javada//Daily Study//RU', 'CALSCALE:GREGORIAN',
    'BEGIN:VEVENT', 'UID:javada-daily-study@local',
    `DTSTAMP:${now.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z')}`,
    `DTSTART:${calendarDate(time, now)}`, 'DURATION:PT15M', 'RRULE:FREQ=DAILY',
    'SUMMARY:Позаниматься Java',
    'DESCRIPTION:Откройте Javada и сделайте один шаг в обучении Java.',
    'BEGIN:VALARM', 'ACTION:DISPLAY', 'TRIGGER:PT0M',
    'DESCRIPTION:Время для Java — откройте Javada и позанимайтесь.',
    'END:VALARM', 'END:VEVENT', 'END:VCALENDAR',
  ];
  return `${lines.map(foldIcs).join('\r\n')}\r\n`;
}

export function downloadCalendarReminder(time) {
  const blob = new Blob([calendarReminder(time)], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'javada-daily-reminder.ics';
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
