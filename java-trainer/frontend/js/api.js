import { readCache, writeCache } from './storage.js';
import { courseStatuses } from './utils/course.js';

async function requestJson(url, options = {}, timeoutMs = 10_000) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(data.error || 'Сервер не смог обработать запрос.');
      error.kind = data.kind || 'server';
      error.status = response.status;
      throw error;
    }
    return data;
  } catch (error) {
    console.error('Ошибка запроса:', url, error);
    if (error.name === 'AbortError') throw new Error('Сервер отвечает слишком долго. Попробуйте ещё раз.');
    if (error instanceof TypeError) throw new Error('Нет связи с локальным сервером. Проверьте, что python server.py запущен.');
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export async function getModules(state) {
  const cached = readCache('index');
  if (cached) return courseStatuses(cached, state);
  const completed = state.completedPractices.join(',');
  const bossPassed = Object.entries(state.bossScores)
    .filter(([, score]) => score?.passed).map(([id]) => id).join(',');
  const query = new URLSearchParams({ completed, bossPassed });
  try {
    const data = await requestJson(`/api/modules?${query}`);
    writeCache('index', data.modules);
    return courseStatuses(data.modules, state);
  } catch (error) {
    const stale = readCache('index', { allowStale: true });
    if (stale) return courseStatuses(stale, state);
    throw error;
  }
}

export async function getModuleContent(moduleId) {
  if (!Number.isInteger(moduleId) || moduleId < 1 || moduleId > 22) throw new Error('Неизвестный модуль.');
  const key = `module-${moduleId}`;
  const cached = readCache(key);
  if (cached) return cached;
  try {
    const data = await requestJson(`/data/module-${String(moduleId).padStart(2, '0')}.json`);
    writeCache(key, data);
    return data;
  } catch (error) {
    const stale = readCache(key, { allowStale: true });
    if (stale) return stale;
    throw error;
  }
}

export async function getLabs() {
  const cached = readCache('labs');
  if (cached) return cached;
  try {
    const data = await requestJson('/data/labs.json');
    writeCache('labs', data.labs);
    return data.labs;
  } catch (error) {
    const stale = readCache('labs', { allowStale: true });
    if (stale) return stale;
    throw error;
  }
}

export function getHealth() {
  return requestJson('/api/health');
}

export function mergeProgress(state, drafts) {
  return requestJson('/api/progress/merge', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ source: 'browser', state, drafts }),
  });
}

export function saveSharedDraft(taskId, code) {
  return requestJson('/api/progress/draft', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ taskId, code }),
  });
}

export function resetSharedProgress() {
  return requestJson('/api/progress/reset', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ confirm: true }),
  });
}

export function submitCode(code, taskId, taskType = 'run') {
  return requestJson('/api/compile', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code, taskType, taskId }),
  }, 25_000);
}
