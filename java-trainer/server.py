"""Local-only Java trainer: static files, course metadata and a small Java judge.

Running submitted Java is inherently unsafe without an OS/container sandbox. This
server binds to loopback by default and is intended ONLY for your own trusted code.
Timeouts and resource limits reduce accidents; they are NOT a security boundary.
"""

from __future__ import annotations

import io
import json
import logging
import os
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import time
import webbrowser
import zipfile
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from urllib.parse import urlsplit

from flask import Flask, jsonify, request, send_file, send_from_directory

import shared_progress

try:
    import resource  # POSIX only; imported before Flask starts worker threads.
except ImportError:  # Windows
    resource = None

ROOT = Path(__file__).resolve().parent
FRONTEND = ROOT / "frontend"
DATA = FRONTEND / "data"
PRIVATE = ROOT / "private"
PROJECTS = ROOT / "projects"
DOWNLOADABLE_PROJECTS = {"task-tracker", "task-api"}
TEMP = ROOT / "temp"
MAX_LAB_CASES = 4
MAX_CODE_BYTES = 16_384
MAX_REQUEST_BYTES = 24_576
MAX_PROGRESS_BYTES = 2 * 1024 * 1024  # existing browser history + drafts
MAX_OUTPUT_BYTES = 12_000
COMPILE_TIMEOUT_SECONDS = 10
RUN_TIMEOUT_SECONDS = 5
MAX_PARALLEL_JOBS = 2
LOOPBACK_HOSTS = {"localhost", "127.0.0.1", "::1"}
JOBS = threading.BoundedSemaphore(MAX_PARALLEL_JOBS)


def execution_enabled() -> bool:
    """Never execute learner code when explicitly bound to a public interface."""
    return (os.environ.get("TRAINER_HOST", "127.0.0.1") in LOOPBACK_HOSTS
            and os.environ.get("TRAINER_DISABLE_JAVA", "0") != "1")


@dataclass
class CommandResult:
    returncode: int | None
    stdout: str
    stderr: str
    timed_out: bool = False


VM_INIT_ERRORS = (
    "could not create the java virtual machine",
    "error occurred during initialization of vm",
    "could not reserve enough space",
    "failed to reserve memory",
    "unrecognized option",
    "invalid maximum heap size",
    "invalid initial heap size",
)


def is_vm_init_failure(stderr: str, returncode: int | None) -> bool:
    low = (stderr or "").lower()
    if any(err in low for err in VM_INIT_ERRORS):
        return True
    if returncode is not None and returncode in (-6, -11, -7, -4, 134, 139, 135, 132):
        return True
    return False


def is_javac_diagnostic(stderr: str, stdout: str) -> bool:
    combined = (stderr or "") + "\n" + (stdout or "")
    return bool(re.search(r'(?:^|[\\/\n])Main\.java:\d+:', combined))


def _course_files_token() -> tuple:
    files = [DATA / "modules.json", DATA / "labs.json", PRIVATE / "lab_cases.json"]
    files.extend(DATA.glob("module-*.json"))
    return tuple((str(p), p.stat().st_mtime_ns) for p in sorted(files) if p.exists())


_CACHED_TOKEN = None
_CACHED_INDEX = None
_CACHED_CODE_TASKS = None
_CACHED_IDE_TASKS = None


def course_revision() -> str:
    token = _course_files_token()
    import hashlib
    h = hashlib.sha256()
    for item in token:
        h.update(str(item).encode("utf-8"))
    return h.hexdigest()[:16]


def course_index() -> dict:
    global _CACHED_TOKEN, _CACHED_INDEX, _CACHED_CODE_TASKS, _CACHED_IDE_TASKS
    current_token = _course_files_token()
    if _CACHED_INDEX is None or _CACHED_TOKEN != current_token:
        with (DATA / "modules.json").open(encoding="utf-8") as source:
            _CACHED_INDEX = json.load(source)
        _CACHED_CODE_TASKS = None
        _CACHED_IDE_TASKS = None
        _CACHED_TOKEN = current_token
    return _CACHED_INDEX


def code_tasks() -> dict[str, dict]:
    global _CACHED_TOKEN, _CACHED_CODE_TASKS
    course_index()
    if _CACHED_CODE_TASKS is not None:
        return _CACHED_CODE_TASKS
    tasks = {}
    for module in course_index()["modules"]:
        if not module["contentFile"]:
            continue
        with (DATA / module["contentFile"]).open(encoding="utf-8") as source:
            content = json.load(source)
        activities = content["lessons"] + [content["practice"]]
        if content.get("boss"):
            activities.append(content["boss"])
        for activity in activities:
            for task in activity["tasks"]:
                if task["type"] == "code":
                    if task["id"] in tasks:
                        raise ValueError(f"Duplicate task ID: {task['id']}")
                    tasks[task["id"]] = task
    with (DATA / "labs.json").open(encoding="utf-8") as source:
        labs = json.load(source)["labs"]
    with (PRIVATE / "lab_cases.json").open(encoding="utf-8") as source:
        lab_cases = json.load(source)
    if {lab["id"] for lab in labs} != set(lab_cases):
        raise ValueError("Lab definitions and judge cases do not match")
    for lab in labs:
        cases = lab_cases[lab["id"]]
        if (lab["id"] in tasks or lab.get("type") != "code" or lab.get("mode") != "run"
            or not isinstance(cases, list) or len(cases) != MAX_LAB_CASES):
            raise ValueError(f"Invalid lab task: {lab['id']}")
        if any(not isinstance(case, dict) or not isinstance(case.get("input"), str)
               or not isinstance(case.get("expectedOutput"), str)
               or len(case["input"].encode("utf-8")) > 4096
               or len(case["expectedOutput"].encode("utf-8")) > 1024 for case in cases):
            raise ValueError(f"Invalid lab cases: {lab['id']}")
        tasks[lab["id"]] = {**lab, "testCases": cases}
    _CACHED_CODE_TASKS = tasks
    return _CACHED_CODE_TASKS


def ide_tasks() -> list[dict]:
    global _CACHED_IDE_TASKS
    course_index()
    if _CACHED_IDE_TASKS is not None:
        return _CACHED_IDE_TASKS
    result = []
    for module in course_index()["modules"]:
        content = json.loads((DATA / module["contentFile"]).read_text(encoding="utf-8"))
        for lesson in content["lessons"]:
            for task in lesson["tasks"]:
                if task["type"] == "code":
                    result.append({**task, "moduleId": module["id"], "moduleTitle": module["title"],
                                   "section": "Необязательный хард", "lessonTitle": lesson["title"]})
        for task in content["practice"]["tasks"]:
            if task["type"] == "code":
                result.append({**task, "moduleId": module["id"], "moduleTitle": module["title"],
                               "section": "Практика", "lessonTitle": "Финальная практика"})
    labs = json.loads((DATA / "labs.json").read_text(encoding="utf-8"))["labs"]
    for lab in labs:
        result.append({**lab, "prompt": lab["description"], "moduleId": 0,
                       "moduleTitle": "К junior", "section": "Лаборатории", "lessonTitle": lab["title"]})
    if len(result) != 44 + 99 + len(labs):
        raise ValueError("IDE catalog must contain every coding exercise")
    _CACHED_IDE_TASKS = result
    return _CACHED_IDE_TASKS


def parse_java_version(output: str) -> int | None:
    m = re.search(r'(?:javac|version)\s+["\']?(\d+)(?:\.(\d+))?', output, re.IGNORECASE)
    if not m:
        m = re.search(r'["\']?(\d+)(?:\.(\d+))?', output)
    if m:
        major = int(m.group(1))
        if major == 1 and m.group(2):
            return int(m.group(2))
        return major
    return None


_JDK_HEALTH_CACHE: tuple[float, tuple[str | None, str | None], tuple[bool, str]] | None = None
_JDK_LOCK = threading.Lock()


def check_jdk_health(force: bool = False) -> tuple[bool, str]:
    global _JDK_HEALTH_CACHE
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        return False, "JDK не найден. Установите JDK 17+ и перезапустите сервер."
    now = time.monotonic()
    with _JDK_LOCK:
        if not force and _JDK_HEALTH_CACHE is not None:
            cached_time, cached_paths, cached_res = _JDK_HEALTH_CACHE
            if cached_paths == (javac, java) and now - cached_time < 30.0:
                return cached_res
        # Probe javac version
        try:
            ver_res = run_command([javac, "-version"], Path(tempfile.gettempdir()), 5.0, compile_step=True)
            v = parse_java_version((ver_res.stdout or "") + " " + (ver_res.stderr or ""))
            if v is not None and v < 17:
                res = False, f"Требуется JDK версии 17 или новее (найдена версия {v})."
                _JDK_HEALTH_CACHE = (now, (javac, java), res)
                return res
        except Exception:
            pass
        # Trial compilation and execution
        try:
            with tempfile.TemporaryDirectory(prefix="jdk-probe-", dir=TEMP) as folder:
                folder_p = Path(folder)
                probe_src = (
                    "public class Main {\n"
                    "    public static void main(String[] args) {\n"
                    "        System.out.println(\"ok\");\n"
                    "    }\n"
                    "}\n"
                )
                (folder_p / "Main.java").write_text(probe_src, encoding="utf-8")
                comp = run_command([javac, "-J-Xmx256m", "-J-XX:CompressedClassSpaceSize=64m",
                                    "-J-XX:ReservedCodeCacheSize=64m", "-encoding", "UTF-8", "-d", str(folder_p),
                                    str(folder_p / "Main.java")], folder_p, 10.0, compile_step=True)
                if comp.returncode != 0:
                    diag = (comp.stderr or "").strip() or (comp.stdout or "").strip()
                    res = False, f"Ошибка пробной компиляции JDK: {diag or 'не удалось скомпилировать'}"
                    _JDK_HEALTH_CACHE = (now, (javac, java), res)
                    return res
                run = run_command([java, "-Xms16m", "-Xmx96m", "-XX:MaxMetaspaceSize=128m",
                                   "-XX:CompressedClassSpaceSize=64m", "-XX:ReservedCodeCacheSize=64m",
                                   "-XX:ActiveProcessorCount=2", "-Dfile.encoding=UTF-8",
                                   "-cp", str(folder_p), "Main"], folder_p, 5.0, compile_step=False)
                if run.returncode != 0 or run.stdout.strip() != "ok":
                    diag = (run.stderr or "").strip() or (run.stdout or "").strip()
                    res = False, f"Ошибка пробного запуска JDK: {diag or 'не удалось запустить JVM'}"
                    _JDK_HEALTH_CACHE = (now, (javac, java), res)
                    return res
        except Exception as e:
            res = False, f"Не удалось выполнить проверку JDK: {e}"
            _JDK_HEALTH_CACHE = (now, (javac, java), res)
            return res
        res = True, "JDK готов к работе."
        _JDK_HEALTH_CACHE = (now, (javac, java), res)
        return res


def parse_id_set(value: str, valid_ids: set[int]) -> set[int]:
    if not value:
        return set()
    parts = value.split(",")
    if len(parts) > len(valid_ids) or any(not re.fullmatch(r"[0-9]{1,2}", part) for part in parts):
        raise ValueError("Некорректный список модулей.")
    result = {int(part) for part in parts}
    if not result.issubset(valid_ids):
        raise ValueError("Неизвестный номер модуля.")
    return result


def modules_with_status(completed: set[int], boss_passed: set[int]) -> list[dict]:
    """Statuses use query parameters; personal data lives in a separate local profile."""
    unlocked = True
    result = []
    for module in course_index()["modules"]:
        number = module["id"]
        finished = number in completed and (not module.get("boss") or number in boss_passed)
        if not unlocked:
            status = "locked"
        elif module["contentStatus"] != "ready":
            status = "planned"  # Only for future entries missing a content file.
        else:
            status = "completed" if finished else "available"
        result.append({**module, "status": status})
        unlocked = unlocked and finished
    return result


def _limited_process(compile_step: bool) -> None:
    """Best-effort POSIX limits. Not an isolation mechanism for hostile code."""
    if resource is None:
        return
    try:
        cpu = COMPILE_TIMEOUT_SECONDS + 1 if compile_step else RUN_TIMEOUT_SECONDS + 1
        resource.setrlimit(resource.RLIMIT_CPU, (cpu, cpu + 1))
        resource.setrlimit(resource.RLIMIT_FSIZE, (128 * 1024, 128 * 1024))
        if hasattr(resource, "RLIMIT_AS") and sys.platform.startswith("linux"):
            # JVM reserves more virtual memory than its -Xmx heap setting;
            # macOS uses virtual address space differently, so omit AS there.
            limit = 3 * 1024**3 if compile_step else 2 * 1024**3
            resource.setrlimit(resource.RLIMIT_AS, (limit, limit))
    except (OSError, ValueError):
        # Windows and some POSIX hosts do not expose all limits. Wall-time and
        # JVM heap limits are still enforced independently.
        pass


def _read_capped(file) -> str:
    file.seek(0)
    data = file.read(MAX_OUTPUT_BYTES + 1)
    truncated = len(data) > MAX_OUTPUT_BYTES
    text = data[:MAX_OUTPUT_BYTES].decode("utf-8", errors="replace")
    return text + ("\n… Вывод обрезан (лимит 12 КБ)." if truncated else "")


def run_command(args: list[str], directory: Path, timeout: float, *, compile_step: bool,
                stdin_data: str | None = None) -> CommandResult:
    """No shell or inherited stdin; bound output and kill the process group on timeout."""
    environment = {
        "PATH": os.environ.get("PATH", "/usr/bin:/bin"),
        "HOME": str(directory),
        "TMPDIR": str(directory),
        "LANG": "C.UTF-8",
    }
    for var in ("SystemRoot", "WINDIR", "TEMP", "TMP"):
        if var in os.environ:
            environment[var] = os.environ[var]
        elif var.upper() in os.environ:
            environment[var] = os.environ[var.upper()]
    with tempfile.TemporaryFile(dir=directory) as stdout, tempfile.TemporaryFile(dir=directory) as stderr:
        input_file = tempfile.TemporaryFile(dir=directory) if stdin_data is not None else None
        try:
            if input_file is not None:
                input_file.write(stdin_data.encode("utf-8"))
                input_file.seek(0)
            process = subprocess.Popen(
                args,
                cwd=directory,
                env=environment,
                stdin=input_file if input_file is not None else subprocess.DEVNULL,
                stdout=stdout,
                stderr=stderr,
                start_new_session=(os.name == "posix"),
                preexec_fn=(lambda: _limited_process(compile_step)) if os.name == "posix" else None,
            )
            try:
                process.wait(timeout=timeout)
                timed_out = False
            except subprocess.TimeoutExpired:
                timed_out = True
                if os.name == "posix":
                    try:
                        os.killpg(process.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                else:
                    process.kill()
                process.wait()
            return CommandResult(process.returncode, _read_capped(stdout), _read_capped(stderr), timed_out)
        finally:
            if input_file is not None:
                input_file.close()


def normalized_output(text: str) -> str:
    """Ignore line-end padding/newline style, but keep meaningful inner spaces.

    A solution printing "0 1 2 3 " is as valid as "0 1 2 3"; changing
    "Alice:31" to "Alice :31" or reordering lines is still a wrong answer.
    """
    lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    return "\n".join(line.rstrip(" \t") for line in lines).rstrip("\n")


def api_error(kind: str, message: str, status: int):
    """Use the same envelope for learner errors and infrastructure errors."""
    return jsonify(success=False, kind=kind, output="", error=message), status


def origin_allowed(origin: str, host: str) -> bool:
    """Same origin, or a localhost-to-localhost request for local development."""
    try:
        parsed = urlsplit(origin)
        target = urlsplit("http://" + host)
        return parsed.scheme in {"http", "https"} and (
            parsed.netloc.lower() == host.lower()
            or (parsed.hostname in LOOPBACK_HOSTS and target.hostname in LOOPBACK_HOSTS)
        )
    except ValueError:
        return False


def create_app() -> Flask:
    app = Flask(__name__, static_folder=None)
    app.config["MAX_CONTENT_LENGTH"] = MAX_PROGRESS_BYTES
    TEMP.mkdir(parents=True, exist_ok=True)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    @app.before_request
    def guard_origin():
        if request.path == "/api/compile" and request.content_length and request.content_length > MAX_REQUEST_BYTES:
            return api_error("bad_request", "Код слишком длинный (максимум 16 КБ).", 413)
        if (request.path.startswith("/api/progress") or request.path == "/api/ide/tasks") and not execution_enabled():
            # The live preview is public. Never expose private drafts/progress
            # or accept writes except on a local loopback-only server.
            return api_error("unavailable", "Общее хранилище доступно только локально.", 503)
        if request.path.startswith("/api/") and (request.method in {"POST", "OPTIONS"}
                                                  or request.path.startswith("/api/progress")):
            origin = request.headers.get("Origin")
            if origin and not origin_allowed(origin, request.host):
                return api_error("forbidden", "Запрос с другого сайта отклонён.", 403)
            # A browser at another local port must not read/reset a private
            # profile. The IDEA client sends no Origin; the web UI is same-origin.
            if origin and request.path.startswith("/api/progress") and urlsplit(origin).netloc.lower() != request.host.lower():
                return api_error("forbidden", "Профиль доступен только со страницы тренажёра.", 403)
        return None

    @app.after_request
    def add_headers(response):
        if request.path.startswith("/api/progress"):
            response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            "font-src 'self'; img-src 'self' data:; connect-src 'self'; "
            "object-src 'none'; base-uri 'none'; form-action 'none'"
        )
        origin = request.headers.get("Origin")
        if request.path.startswith("/api/") and origin and origin_allowed(origin, request.host):
            # Same-origin needs no CORS, but this also permits localhost dev tools.
            if urlsplit(origin).hostname in LOOPBACK_HOSTS:
                response.headers["Access-Control-Allow-Origin"] = origin
                response.headers["Vary"] = "Origin"
                response.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
                response.headers["Access-Control-Allow-Headers"] = "Content-Type"
        return response

    @app.errorhandler(413)
    def too_large(_error):
        return api_error("bad_request", "Код слишком длинный (максимум 16 КБ).", 413)

    @app.get("/")
    def index():
        return send_from_directory(FRONTEND, "index.html")

    @app.get("/api/health")
    def health():
        if not execution_enabled():
            return jsonify(jdkAvailable=False,
                           revision=course_revision(),
                           message="Запуск Java отключён в сетевом предпросмотре. Запустите сервер локально без TRAINER_HOST.")
        force = request.args.get("refresh") == "1"
        available, msg = check_jdk_health(force=force)
        return jsonify(jdkAvailable=available,
                       revision=course_revision(),
                       message=msg)

    @app.get("/api/modules")
    def modules():
        all_modules = course_index()["modules"]
        valid = {module["id"] for module in all_modules}
        try:
            completed = parse_id_set(request.args.get("completed", ""), valid)
            boss_passed = parse_id_set(request.args.get("bossPassed", ""), valid)
        except ValueError as error:
            return jsonify(error=str(error)), 400
        return jsonify(modules=modules_with_status(completed, boss_passed), total=len(all_modules),
                       revision=course_revision(),
                       progressSource="browser-query")

    @app.get("/api/ide/tasks")
    def idea_catalog():
        return jsonify(version=course_index()["version"], revision=course_revision(), tasks=ide_tasks(),
                       existingPracticeCount=44, lessonHardcoreCount=99)

    @app.get("/api/progress")
    def progress():
        return jsonify(shared_progress.get_profile())

    @app.post("/api/progress/merge")
    def merge_progress():
        payload = request.get_json(silent=True)
        if not isinstance(payload, dict) or not isinstance(payload.get("state"), dict):
            return api_error("bad_request", "Ожидается JSON с сохранённым прогрессом.", 400)
        if payload.get("drafts") is not None and not isinstance(payload["drafts"], dict):
            return api_error("bad_request", "Неверный формат черновиков.", 400)
        return jsonify(shared_progress.merge_profile(payload["state"], payload.get("drafts"),
                                                     browser=payload.get("source") == "browser"))

    @app.post("/api/progress/draft")
    def save_shared_draft():
        payload = request.get_json(silent=True)
        if not isinstance(payload, dict):
            return api_error("bad_request", "Ожидается JSON с черновиком.", 400)
        try:
            result = shared_progress.save_draft(payload.get("taskId"), payload.get("code"))
        except ValueError as error:
            return api_error("bad_request", str(error), 400)
        return jsonify(result)

    @app.post("/api/progress/reset")
    def reset_shared_progress():
        if not request.is_json or request.get_json(silent=True) != {"confirm": True}:
            return api_error("bad_request", "Подтвердите сброс сохранения.", 400)
        return jsonify(shared_progress.reset_profile())

    @app.get("/api/projects/<project_id>/starter")
    def download_starter(project_id: str):
        if project_id not in DOWNLOADABLE_PROJECTS:
            return api_error("not_found", "Неизвестный проект.", 404)
        buffer = io.BytesIO()
        folder = PROJECTS / project_id
        with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(folder.rglob("*")):
                if (not path.is_file() or path.is_symlink()
                    or path.suffix in {".class", ".pyc", ".db", ".tmp"}
                    or path.name == "tasks.tsv"
                    or any(part in {"__pycache__", "out", "target", ".venv", ".git"}
                           for part in path.relative_to(folder).parts)):
                    continue
                archive.write(path, arcname=f"{project_id}/{path.relative_to(folder).as_posix()}")
        buffer.seek(0)
        return send_file(buffer, mimetype="application/zip", as_attachment=True,
                         download_name=f"{project_id}-starter.zip")

    @app.route("/api/compile", methods=["POST", "OPTIONS"])
    def compile_java():
        if request.method == "OPTIONS":
            return "", 204
        if not request.is_json:
            return api_error("bad_request", "Отправьте JSON с кодом.", 400)
        payload = request.get_json(silent=True)
        if not isinstance(payload, dict):
            return api_error("bad_request", "Неверный формат JSON.", 400)

        code = payload.get("code")
        task_type = payload.get("taskType")
        task_id = payload.get("taskId")
        record_progress = payload.get("recordProgress", False)
        if type(record_progress) is not bool:
            return api_error("bad_request", "Неверный параметр записи прогресса.", 400)
        if not isinstance(code, str) or not code.strip() or len(code.encode("utf-8")) > MAX_CODE_BYTES:
            return api_error("bad_request", "Введите Java-код не длиннее 16 КБ.", 400)
        if task_type not in {"compile", "run"} or (task_id is not None and not isinstance(task_id, str)):
            return api_error("bad_request", "Неизвестный тип проверки.", 400)
        # Standalone compilation is useful for experimenting, but running is
        # restricted to known exercises with server-owned expected output.
        task = code_tasks().get(task_id) if task_id else None
        if (task_id and not task) or (task and task.get("mode") != task_type) or (task_type == "run" and not task):
            return api_error("bad_request", "Неизвестное задание для выполнения.", 400)
        if not execution_enabled():
            return api_error("unavailable", "Запуск Java отключён в сетевом предпросмотре. Используйте локальный адрес 127.0.0.1.", 503)

        javac, java = shutil.which("javac"), shutil.which("java")
        if not javac or (task_type == "run" and not java):
            return jsonify(success=False, kind="unavailable", output="",
                           error="JDK не найден. Установите JDK 17+ и перезапустите сервер."), 503
        if not JOBS.acquire(blocking=False):
            return jsonify(success=False, kind="busy", output="",
                           error="Компилятор занят. Подождите несколько секунд и попробуйте снова."), 429

        try:
            app.logger.info("Java check: type=%s task=%s bytes=%d", task_type, task_id, len(code.encode("utf-8")))
            with tempfile.TemporaryDirectory(prefix="java-", dir=TEMP) as folder:
                directory = Path(folder)
                (directory / "Main.java").write_text(code, encoding="utf-8")
                compiled = run_command([javac, "-J-Xmx256m", "-J-XX:CompressedClassSpaceSize=64m",
                                        "-J-XX:ReservedCodeCacheSize=64m", "-encoding", "UTF-8", "-d", str(directory),
                                        str(directory / "Main.java")], directory, COMPILE_TIMEOUT_SECONDS,
                                       compile_step=True)
                if compiled.timed_out:
                    return jsonify(success=False, kind="compile_timeout", output="",
                                   error="Компиляция заняла слишком много времени (лимит 10 секунд). Проверьте структуру кода или повторите попытку."), 503
                if compiled.returncode != 0:
                    combined_err = compiled.stderr or compiled.stdout or ""
                    if not is_javac_diagnostic(compiled.stderr, compiled.stdout):
                        return jsonify(success=False, kind="unavailable", output="",
                                       error="Сбой запуска компилятора javac: " + (combined_err.strip() or "не удалось скомпилировать Main.java.")), 503
                    diagnostic = compiled.stderr.replace(str(directory) + os.sep, "")
                    if not diagnostic.strip() and compiled.stdout:
                        diagnostic = compiled.stdout.replace(str(directory) + os.sep, "")
                    return jsonify(success=False, kind="compile_error", output="", error=diagnostic or
                                   "Не удалось скомпилировать Main.java.")
                if task_type == "compile":
                    return jsonify(success=True, kind="ok", output="Компиляция прошла успешно.", error="")

                java_args = [java, "-Xms16m", "-Xmx96m", "-XX:MaxMetaspaceSize=128m",
                             "-XX:CompressedClassSpaceSize=64m", "-XX:ReservedCodeCacheSize=64m",
                             "-XX:ActiveProcessorCount=2", "-Dfile.encoding=UTF-8",
                             "-cp", str(directory), "Main"]
                is_lab = "testCases" in task
                cases = task["testCases"] if is_lab else [{"expectedOutput": task["expectedOutput"]}]
                # Compilation has a separate 10s cap; ALL executions of this
                # submission share one 5s wall-clock budget, not 5s per case.
                deadline = time.monotonic() + RUN_TIMEOUT_SECONDS
                for number, case in enumerate(cases, 1):
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        return jsonify(success=False, kind="timeout", output="",
                                       error="Проверки превысили общий лимит 5 секунд.")
                    kwargs = {"stdin_data": case["input"]} if is_lab else {}
                    executed = run_command(java_args, directory, remaining,
                                           compile_step=False, **kwargs)
                    output = executed.stdout
                    diagnostic = executed.stderr.replace(str(directory) + os.sep, "")
                    if executed.timed_out:
                        label = f"Проверка {number}/{len(cases)}. Ввод:\n{case['input']}\n" if is_lab else ""
                        return jsonify(success=False, kind="timeout", output=output,
                                       error=label + "Время выполнения превысило 5 секунд. Проверьте цикл или ожидание ввода.")
                    if executed.returncode != 0:
                        if is_vm_init_failure(executed.stderr, executed.returncode):
                            return jsonify(success=False, kind="unavailable", output="",
                                           error="Сбой запуска виртуальной машины JVM: " + (diagnostic.strip() or f"код завершения {executed.returncode}")), 503
                        label = f"Проверка {number}/{len(cases)}. Ввод:\n{case['input']}\n" if is_lab else ""
                        return jsonify(success=False, kind="runtime_error", output=output,
                                       error=label + (diagnostic or "Программа завершилась с ошибкой."))
                    if normalized_output(output) != normalized_output(case["expectedOutput"]):
                        detail = (f"Проверка {number}/{len(cases)}. Ввод:\n{case['input']}\n"
                                  f"Ожидалось:\n{case['expectedOutput']}") if is_lab else (
                                  "Программа запустилась, но вывод отличается от ожидаемого.")
                        return jsonify(success=False, kind="wrong_output", output=output, error=detail)
                shared = (shared_progress.award_code_task(task_id, task)
                          if record_progress else None)
                return jsonify(success=True, kind="ok",
                               output=f"Пройдено {len(cases)} из {len(cases)} проверок." if is_lab else output,
                               error="", **({"progress": shared} if shared else {}))
        except OSError:
            app.logger.exception("Could not start Java toolchain")
            return jsonify(success=False, kind="unavailable", output="",
                           error="Не удалось запустить JDK. Проверьте установку и права доступа."), 503
        finally:
            JOBS.release()

    @app.get("/<path:filename>")
    def frontend_file(filename: str):
        return send_from_directory(FRONTEND, filename)

    return app


app = create_app()

if __name__ == "__main__":
    host = os.environ.get("TRAINER_HOST", "127.0.0.1")
    port = int(os.environ.get("TRAINER_PORT", "8000"))
    if host not in LOOPBACK_HOSTS:
        app.logger.warning("Bound to %s: Java execution is DISABLED outside loopback.", host)
    if os.environ.get("TRAINER_OPEN_BROWSER", "1") != "0":
        threading.Timer(1.0, lambda: webbrowser.open(f"http://localhost:{port}")).start()
    print(f"Java trainer: http://localhost:{port}  (Ctrl+C to stop)", flush=True)
    app.run(host=host, port=port, debug=False, use_reloader=False, threaded=True)
