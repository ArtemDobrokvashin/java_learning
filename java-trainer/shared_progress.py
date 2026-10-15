"""Single-user, loopback-only progress shared by the web trainer and IDEA plugin.

Legacy browser saves stay in localStorage. On first connection the browser
merges a *copy* into this on-disk profile; an XP ledger records only new awards
so concurrent browser/IDE work can be unioned without counting an award twice.
This is not an authentication or anti-cheat system: the owner controls the PC.
"""

from __future__ import annotations

import json
import os
import re
import tempfile
import threading
import time
from datetime import date, datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent
_LOCK = threading.RLock()
_ID = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9:_.-]{0,99}$")
_ARRAYS = ("completedLessons", "completedPractices", "completedLabs", "awardedTasks", "legacyAwardedTasks", "milestones", "achievements")
_DEFAULT = {
    "version": 1, "xp": 0, "legacyXp": 0, "xpLedger": {},
    "streak": 0, "lastActiveDate": None, "milestones": [],
    "completedLessons": [], "completedPractices": [], "completedLabs": [],
    "legacyAwardedTasks": [], "careerChecklist": [], "bossScores": {}, "examScores": {}, "attempts": {},
    "awardedTasks": [], "reviewRewards": {}, "achievements": [],
    "theme": "light", "remindersEnabled": False, "reminderTime": "19:00", "lastReminderDay": None,
}


def profile_path() -> Path:
    return Path(os.environ.get("JAVADA_PROFILE_DIR", str(ROOT / ".javada"))) / "progress.json"


def _number(value, limit=10_000_000):
    return value if type(value) is int and 0 <= value <= limit else 0


def _list(value, max_items=5000):
    if not isinstance(value, list):
        return []
    result = []
    for item in value[:max_items]:
        if ((isinstance(item, str) and _ID.fullmatch(item)) or
            (type(item) is int and 0 < item <= 100)) and item not in result:
            result.append(item)
    return result


def _keys(value, max_items=5000):
    return {key: item for key, item in list(value.items())[:max_items]
            if isinstance(key, str) and _ID.fullmatch(key)} if isinstance(value, dict) else {}


def _date(value):
    if not isinstance(value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        return None
    try:
        date.fromisoformat(value)
        return value
    except ValueError:
        return None


def normalize_state(raw):
    """Allowlist old and new fields; keep legacy XP without recalculating it."""
    raw = raw if isinstance(raw, dict) else {}
    state = {**_DEFAULT}
    for key in _ARRAYS:
        state[key] = _list(raw.get(key))
    state["careerChecklist"] = _list(raw.get("careerChecklist"))
    if "legacyAwardedTasks" not in raw:
        state["legacyAwardedTasks"] = _list(raw.get("awardedTasks"))
    state["legacyXp"] = _number(raw.get("legacyXp", raw.get("xp", 0)))
    state["xpLedger"] = {key: _number(value, 1000) for key, value in _keys(raw.get("xpLedger")).items()}
    tracked = sum(amount for key, amount in state["xpLedger"].items()
                  if not key.startswith("task:") or key[5:] not in state["legacyAwardedTasks"])
    # Preserve old bonuses not present in a ledger, without double-counting a
    # task that was completed in the browser before the profile was created.
    if _number(raw.get("xp")) > state["legacyXp"] + tracked:
        state["legacyXp"] = _number(raw.get("xp")) - tracked
    state["xp"] = state["legacyXp"] + tracked
    state["streak"] = _number(raw.get("streak"), 50_000)
    state["lastActiveDate"] = _date(raw.get("lastActiveDate"))
    state["lastReminderDay"] = _date(raw.get("lastReminderDay"))
    state["theme"] = "dark" if raw.get("theme") == "dark" else "light"
    state["remindersEnabled"] = raw.get("remindersEnabled") is True
    if isinstance(raw.get("reminderTime"), str) and re.fullmatch(r"(?:[01]\d|2[0-3]):[0-5]\d", raw["reminderTime"]):
        state["reminderTime"] = raw["reminderTime"]
    for field in ("bossScores", "examScores", "attempts", "reviewRewards"):
        state[field] = _keys(raw.get(field))
    return state


def merge_states(stored, incoming, *, browser=False):
    """Monotonic learning progress; a browser may additionally set preferences."""
    left, right = normalize_state(stored), normalize_state(incoming)
    merged = {**left}
    for key in _ARRAYS:
        merged[key] = list(dict.fromkeys([*left[key], *right[key]]))
    merged["legacyXp"] = max(left["legacyXp"], right["legacyXp"])
    merged["xpLedger"] = {**left["xpLedger"]}
    for key, amount in right["xpLedger"].items():
        merged["xpLedger"][key] = max(merged["xpLedger"].get(key, 0), amount)
    # Both clients may have an old award for the same task before migration.
    # Its XP already lives in legacyXp and must not be counted a second time.
    merged["xp"] = merged["legacyXp"] + sum(
        amount for key, amount in merged["xpLedger"].items()
        if not key.startswith("task:") or key[5:] not in merged["legacyAwardedTasks"])
    # A streak is associated with its last active day, not a forever-growing
    # counter. A stale browser tab must not restore a streak after a break.
    left_day, right_day = left["lastActiveDate"], right["lastActiveDate"]
    merged["lastActiveDate"] = max(filter(None, (left_day, right_day)), default=None)
    if left_day == right_day:
        merged["streak"] = max(left["streak"], right["streak"])
    else:
        newest = left if merged["lastActiveDate"] == left_day else right
        merged["streak"] = newest["streak"]
        merged["milestones"] = newest["milestones"]
    if not merged["lastActiveDate"] or date.fromisoformat(merged["lastActiveDate"]) < date.today() - timedelta(days=1):
        merged["streak"] = 0
        merged["milestones"] = []
    for field in ("bossScores", "examScores", "attempts", "reviewRewards"):
        merged[field] = {**left[field]}
        for key, value in right[field].items():
            old = merged[field].get(key)
            if field in {"bossScores", "examScores"} and isinstance(value, dict):
                old = old if isinstance(old, dict) else {}
                merged[field][key] = {"best": max(_number(old.get("best"), 100), _number(value.get("best"), 100)),
                                       "passed": old.get("passed") is True or value.get("passed") is True}
            elif field == "attempts" and isinstance(value, dict):
                old = old if isinstance(old, dict) else {}
                newest = value if str(value.get("lastAttempt", "")) >= str(old.get("lastAttempt", "")) else old
                merged[field][key] = {**newest,
                                      "mistakes": max(_number(value.get("mistakes")), _number(old.get("mistakes"))),
                                      "correct": value.get("correct") is True or old.get("correct") is True}
            elif field == "reviewRewards" and isinstance(value, str):
                merged[field][key] = max(value, old if isinstance(old, str) else "")
    if browser:
        for field in ("theme", "remindersEnabled", "reminderTime", "lastReminderDay", "careerChecklist"):
            merged[field] = right[field]
    return merged


def normalize_drafts(raw):
    drafts = {}
    for task_id, draft in _keys(raw, 300).items():
        if not isinstance(draft, dict) or not isinstance(draft.get("code"), str):
            continue
        code = draft["code"]
        if len(code.encode("utf-8")) > 16_384:
            continue
        drafts[task_id] = {"code": code, "updatedAt": _number(draft.get("updatedAt"), 10_000_000_000_000)}
    return drafts


def _read() -> dict:
    path = profile_path()
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(payload, dict) and payload.get("version") == 1:
            return {"version": 1, "revision": _number(payload.get("revision")),
                    "state": normalize_state(payload.get("state")),
                    "drafts": normalize_drafts(payload.get("drafts")),
                    "exams": {key: value for key, value in _keys(payload.get("exams"), 22).items()
                              if isinstance(value, dict)}}
    except (OSError, ValueError, TypeError):
        pass
    return {"version": 1, "revision": 0, "state": normalize_state({}), "drafts": {}, "exams": {}}


def _write(profile: dict) -> None:
    path = profile_path()
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    # Replace atomically so a terminated server cannot leave half a JSON file.
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                     prefix=".progress-", delete=False) as stream:
        name = stream.name
        json.dump(profile, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    try:
        os.chmod(name, 0o600)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def get_profile() -> dict:
    with _LOCK:
        return _read()


def merge_profile(state, drafts=None, *, browser=False) -> dict:
    with _LOCK:
        current = _read()
        merged = merge_states(current["state"], state, browser=browser)
        merged["examScores"] = current["state"]["examScores"]  # only the exam judge may certify a pass
        result_drafts = dict(current["drafts"])
        for task_id, draft in normalize_drafts(drafts).items():
            if draft["updatedAt"] > result_drafts.get(task_id, {}).get("updatedAt", -1):
                result_drafts[task_id] = draft
        if merged != current["state"] or result_drafts != current["drafts"]:
            current.update(state=merged, drafts=result_drafts, revision=current["revision"] + 1)
            _write(current)
        return current


def save_draft(task_id: str, code: str) -> dict:
    if not isinstance(task_id, str) or not _ID.fullmatch(task_id) or not isinstance(code, str) or len(code.encode()) > 16_384:
        raise ValueError("Некорректный черновик.")
    with _LOCK:
        profile = _read()
        draft = {"code": code, "updatedAt": int(time.time() * 1000)}
        previous = profile["drafts"].get(task_id, {})
        draft["updatedAt"] = max(draft["updatedAt"], previous.get("updatedAt", 0) + 1)
        profile["drafts"][task_id] = draft
        profile["revision"] += 1
        _write(profile)
        return profile


def award_code_task(task_id: str, task: dict) -> dict:
    """Called only after a *successful real compilation and run* on loopback."""
    with _LOCK:
        profile = _read()
        state = profile["state"]
        is_lab = task_id.startswith("lab-")
        now = datetime.now().astimezone()
        today = now.date().isoformat()
        if not is_lab and state["lastActiveDate"] != today:
            prior = date.fromisoformat(state["lastActiveDate"]) if state["lastActiveDate"] else None
            consecutive = prior is not None and (now.date() - prior).days == 1
            state["streak"] = state["streak"] + 1 if consecutive else 1
            if not consecutive:
                state["milestones"] = []
            state["lastActiveDate"] = today
        if task_id not in state["awardedTasks"]:
            state["awardedTasks"].append(task_id)
            # Labs have no XP (just like in the web application); the 44
            # original practice tasks keep their 50 XP base reward.
            base = 0 if is_lab else 90 if task.get("difficulty") == "hardcore" else 50
            amount = (base * 11 + 5) // 10 if state["streak"] >= 2 else base
            if amount:
                state["xpLedger"]["task:" + task_id] = amount
        milestone = {7: 50, 30: 200}.get(state["streak"])
        if not is_lab and milestone and state["streak"] not in state["milestones"]:
            state["milestones"].append(state["streak"])
            state["xpLedger"][f"milestone:{state['streak']}:{today}"] = milestone
        state["xp"] = state["legacyXp"] + sum(
            value for key, value in state["xpLedger"].items()
            if not key.startswith("task:") or key[5:] not in state["legacyAwardedTasks"])
        if is_lab and task_id not in state["completedLabs"]:
            state["completedLabs"].append(task_id)
        state["attempts"][task_id] = {"correct": True, "type": "code",
                                      "lastAttempt": now.isoformat(),
                                      "mistakes": state["attempts"].get(task_id, {}).get("mistakes", 0),
                                      "needsReview": False}
        if task.get("difficulty") == "hardcore" and task.get("lessonId"):
            badge = "mastery:" + task["lessonId"]
            if badge not in state["achievements"]:
                state["achievements"].append(badge)
        # A practice is only finished when *all seven* tasks have been solved;
        # the plugin alone cannot skip the five non-code questions in the web UI.
        profile["revision"] += 1
        _write(profile)
        return profile


def reset_profile() -> dict:
    with _LOCK:
        path = profile_path()
        try:
            path.unlink()
        except FileNotFoundError:
            pass
        return _read()


def update_profile(update):
    """Atomic server-owned updates, preserving concurrent browser/IDE saves."""
    with _LOCK:
        profile = _read()
        update(profile)
        profile["revision"] += 1
        _write(profile)
        return profile
