import { mergeProgress, saveSharedDraft, resetSharedProgress } from './api.js';
import { listDrafts, restoreDrafts } from './storage.js';

// Browser localStorage remains an offline copy. The loopback server owns a
// mergeable copy shared with IDEA. Never erase a legacy save on migration.
export function createSharedProgress({ getState, applyState }) {
  let timer = null;
  let running = false;
  let inFlight = null;
  let dirty = 0;
  let paused = false;
  let unavailable = false;
  let epoch = 0;
  const tombstones = new Set();

  function onPersist(removedDraftId) {
    if (paused || unavailable) return;
    dirty++;
    if (removedDraftId) tombstones.add(removedDraftId);
    clearTimeout(timer);
    timer = setTimeout(() => { void sync(); }, 400);
  }

  function sync() {
    if (paused || unavailable) return Promise.resolve();
    if (running) return inFlight;
    running = true;
    const version = dirty;
    const currentEpoch = epoch;
    clearTimeout(timer);
    inFlight = (async () => {
      try {
        for (const id of tombstones) {
          await saveSharedDraft(id, '');
          tombstones.delete(id);
        }
        const response = await mergeProgress(getState(), listDrafts());
        // An in-flight response must not overwrite a new answer or a reset.
        if (currentEpoch === epoch && version === dirty && !paused) {
          applyState(response.state);
          restoreDrafts(response.drafts);
        }
      } catch (error) {
        if (error.status === 503) unavailable = true; // public preview: local-only save
        else console.warn('Общее хранилище пока недоступно; сохранение браузера оставлено.', error);
      } finally {
        running = false;
        inFlight = null;
        if (!unavailable && !paused && version !== dirty) onPersist();
      }
    })();
    return inFlight;
  }

  async function reset() {
    paused = true;
    epoch++;
    clearTimeout(timer);
    tombstones.clear();
    try {
      if (inFlight) await inFlight; // never let an old merge undo reset
      if (!unavailable) await resetSharedProgress();
    } finally {
      dirty++;
      paused = false;
    }
  }

  return { onPersist, sync, reset };
}
