"""Local exam lifecycle. No lives, server-clock cooldown, idempotent XP award.

Like the rest of this single-user trainer this is not an anti-cheat system:
course answers are public and the computer's owner controls progress.json.
"""
import re
import threading
import time
import uuid

import shared_progress

LOCK = threading.Lock()
PASS_PERCENT = 80
COOLDOWN_SECONDS = 3600


class ExamError(Exception):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.status = status


def check_answer(task, answer):
    if task["type"] == "choice":
        return (isinstance(answer, list) and len(answer) == len(task["correct"])
                and all(isinstance(x, str) for x in answer) and set(answer) == set(task["correct"]))
    if task["type"] == "fill":
        if not isinstance(answer, dict):
            return False
        for blank in task["blanks"]:
            value = answer.get(blank["id"], "")
            if not isinstance(value, str):
                return False
            value = value.strip()
            validation = blank.get("validation", {})
            if validation.get("kind") == "integerRange":
                if not re.fullmatch(r"-?(?:0|[1-9][0-9]*)", value) or len(value) > 12:
                    return False
                if not validation["min"] <= int(value) <= validation["max"]:
                    return False
            elif value not in [str(item).strip() for item in blank.get("accepted", [blank["answer"]])]:
                return False
        return True
    if task["type"] == "matching":
        return isinstance(answer, dict) and all(answer.get(p["id"]) == p["id"] for p in task["pairs"])
    if task["type"] == "parsons":
        if not isinstance(answer, list) or len(answer) != len(task["lines"]):
            return False
        texts = {line["id"]: line["text"] for line in task["lines"]}
        if (any(not isinstance(row, dict) or not isinstance(row.get("id"), str)
                or row["id"] not in texts or type(row.get("indent")) is not int for row in answer)
                or len({row["id"] for row in answer}) != len(texts)):
            return False
        indents = {row["id"]: row["indent"] for row in task["solution"]}
        orders = [[row["id"] for row in task["solution"]], *task.get("alternativeOrders", [])]
        return any(all(texts[row["id"]] == texts[id] and row["indent"] == indents[id]
                       for row, id in zip(answer, order)) for order in orders)
    return False


def require_practice(module, state):
    if module["id"] not in state["completedPractices"]:
        raise ExamError("Сначала завершите практику модуля.", 403)


def snapshot(record):
    return {**record, "serverNow": int(time.time()), "passingPercent": PASS_PERCENT}


def start(module):
    with LOCK:
        profile = shared_progress.get_profile()
        require_practice(module, profile["state"])
        key = str(module["id"])
        old = profile["exams"].get(key, {})
        if old.get("active") or old.get("result", {}).get("passed") or old.get("retryAt", 0) > time.time():
            return snapshot(old)
        record = {"attemptId": uuid.uuid4().hex, "active": True, "startedAt": int(time.time()), "retryAt": 0}
        shared_progress.update_profile(lambda p: p["exams"].update({key: record}))
        return snapshot(record)


def submit(module, attempt_id, answers, judge):
    # A concurrent submission gets a retryable error, not another grade/award.
    if not LOCK.acquire(blocking=False):
        raise ExamError("Экзамен уже проверяется. Повторите запрос через несколько секунд.", 429)
    try:
        profile = shared_progress.get_profile()
        require_practice(module, profile["state"])
        key = str(module["id"])
        record = profile["exams"].get(key, {})
        if not isinstance(attempt_id, str) or attempt_id != record.get("attemptId"):
            raise ExamError("Попытка не найдена. Откройте экзамен заново.", 409)
        if not record.get("active"):
            return {**snapshot(record), "progress": profile}  # safe retry after a lost response
        if not isinstance(answers, dict):
            raise ExamError("Ожидается объект с ответами.")
        tasks = module["exam"]["tasks"]
        for task in tasks:
            answer = answers.get(task["id"])
            if task["type"] == "code" and answer is not None:
                if not isinstance(answer, str) or len(answer.encode("utf-8")) > 16_384:
                    raise ExamError("Код задания должен быть строкой не длиннее 16 КБ.")
        details = []
        for task in tasks:
            answer = answers.get(task["id"])
            if task["type"] == "code" and isinstance(answer, str) and answer.strip():
                result, status = judge(task, answer)
                if status >= 400 or result.get("kind") in {"unavailable", "compile_timeout", "busy"}:
                    # Toolchain/network failures must not consume the attempt or start a cooldown.
                    raise ExamError(result.get("error", "Проверка временно недоступна."), status if status >= 400 else 503)
                correct = result.get("success") is True
                diagnostic = result.get("error", "")
                output = result.get("output", "")
            else:
                correct = check_answer(task, answer)
                diagnostic = "" if correct else "Ответ неверен или задание пропущено."
                output = ""
            details.append({"id": task["id"], "correct": correct,
                            "explanation": task.get("explanation", ""), "error": diagnostic, "output": output})
        correct = sum(row["correct"] for row in details)
        passed = correct * 100 >= PASS_PERCENT * len(tasks)  # never round a failing score up
        result = {"correct": correct, "total": len(tasks), "percent": round(correct * 100 / len(tasks), 1),
                  "passed": passed, "details": details, "xp": module["exam"]["xpBonus"] if passed else 0}
        record = {**record, "active": False, "result": result,
                  "retryAt": 0 if passed else int(time.time()) + COOLDOWN_SECONDS}

        def persist(p):
            current = p["exams"].get(key, {})
            if current.get("attemptId") != attempt_id or not current.get("active"):
                raise ExamError("Попытка была сброшена во время проверки. Откройте экзамен заново.", 409)
            p["exams"][key] = record
            state = p["state"]
            previous = state["examScores"].get(key, {})
            state["examScores"][key] = {"best": max(previous.get("best", 0), int(result["percent"])),
                                        "passed": previous.get("passed", False) or passed}
            if passed:
                state["xpLedger"][f"exam:{key}"] = result["xp"]
                badge = f"exam:m{module['id']:02d}"
                if badge not in state["achievements"]:
                    state["achievements"].append(badge)
                state["xp"] = state["legacyXp"] + sum(
                    amount for id, amount in state["xpLedger"].items()
                    if not id.startswith("task:") or id[5:] not in state["legacyAwardedTasks"])
        profile = shared_progress.update_profile(persist)
        return {**snapshot(record), "progress": profile}
    finally:
        LOCK.release()
