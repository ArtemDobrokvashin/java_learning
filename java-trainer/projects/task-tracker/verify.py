"""Executable acceptance checks for the learner's CLI project. Python 3.10+ + JDK 17+."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent


def scenario(classes: Path, data: Path, commands: str, expected: str, name: str) -> None:
    result = subprocess.run(
        [shutil.which("java") or "java", "-cp", str(classes), "Main", "--data", str(data)],
        input=commands, text=True, encoding="utf-8", capture_output=True, timeout=6,
        check=False,
    )
    actual = result.stdout.strip().replace("\r\n", "\n")
    if result.returncode != 0 or actual != expected.strip():
        raise AssertionError(
            f"{name}: expected:\n{expected.strip()}\n\nactual:\n{actual}\n\nstderr:\n{result.stderr}"
        )
    print(f"✓ {name}")


def main() -> int:
    if not shutil.which("javac") or not shutil.which("java"):
        print("Нужен JDK 17+: проверьте javac и java в PATH.", file=sys.stderr)
        return 2
    with tempfile.TemporaryDirectory(prefix="task-tracker-tests-") as folder:
        root = Path(folder)
        classes = root / "out"
        classes.mkdir()
        sources = sorted((ROOT / "src").glob("*.java"))
        result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(classes),
                                 *(str(path) for path in sources)],
                                text=True, capture_output=True, timeout=25, check=False)
        if result.returncode != 0:
            print(result.stderr, file=sys.stderr)
            return 1
        data = root / "tasks.tsv"
        scenario(classes, data,
                 "add Молоко\nadd Проверить API\nlist\ndone 1\nlist\nexit\n",
                 "ADDED 1\nADDED 2\n1|todo|Молоко\n2|todo|Проверить API\nDONE 1\n"
                 "1|done|Молоко\n2|todo|Проверить API\nBYE", "создание, список, завершение")
        scenario(classes, data,
                 "list\ndone 1\ndone 99\nadd Третья задача\nlist\nexit\n",
                 "1|done|Молоко\n2|todo|Проверить API\nERROR already_done\nERROR not_found\n"
                 "ADDED 3\n1|done|Молоко\n2|todo|Проверить API\n3|todo|Третья задача\nBYE",
                 "перезапуск, сохранение и стабильные ID")
        scenario(classes, root / "other.tsv",
                 "list\nadd   \ndone nope\nadd Задача со словами\nlist\nexit\n",
                 "EMPTY\nERROR empty_title\nERROR invalid_command\nADDED 1\n"
                 "1|todo|Задача со словами\nBYE", "пустые данные и ошибочный ввод")
    print("Все проверки пройдены. Добавьте собственные крайние случаи и CI.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, subprocess.TimeoutExpired) as error:
        print(f"Проверка не пройдена: {error}", file=sys.stderr)
        raise SystemExit(1)
