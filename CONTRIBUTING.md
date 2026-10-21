# Как внести вклад в проект JavaДа (Contributing Guide)

Спасибо за интерес к развитию **JavaДа**! Мы приветствуем исправления опечаток в теории, улучшение формулировок заданий, развитие плагина для IntelliJ IDEA, будущего Android-приложения и локального веб-тренажёра.

---

## 1. Структура репозитория

- `java-trainer/frontend/` — клиентская часть веб-тренажёра (HTML/CSS/JS без внешних CDN) и сгенерированные JSON-файлы курса (`frontend/data/`).
- `java-trainer/server.py`, `shared_progress.py`, `exams.py` — локальный Flask-сервер, компиляция Java и синхронизация `.javada/progress.json`.
- `java-trainer/tools/` — авторские спецификации уроков, практик, хард-задач и мини-экзаменов (`generate_course.py`).
- `java-trainer/idea-plugin/` — автономный плагин для IntelliJ IDEA на Kotlin (Gradle 8.12+, Java 25 Toolchain).
- `java-trainer/tests/` — автотесты на Python (`unittest`) и JavaScript (`node --test`).

---

## 2. Как вносить изменения в контент курса

1. Для модулей 1–3 базовые задания редактируются в `java-trainer/frontend/data/module-01..03.json`.
2. Для модулей 4–22, а также заметок к теории, вторых пошаговых примеров, хард-задач и мини-экзаменов редактируйте исходники в `java-trainer/tools/` (`modules_04_08.py` .. `modules_19_22.py`, `lesson_notes.py`, `guided_lessons.py`, `mastery_challenges.py`, `module_exams.py`).
3. После правки файлов в `tools/` пересоберите JSON-файлы курса:
   ```bash
   cd java-trainer
   python3 tools/generate_course.py
   ```

---

## 3. Обязательная проверка перед открытием Pull Request

Перед отправкой изменений убедитесь, что все тесты проходят:

```bash
cd java-trainer

# 1. Тесты курса, сервера, прогресса и экзаменов
python3 -m unittest discover -s tests -p "test_*.py" -v

# 2. Тесты валидаторов и UI (Node.js 20+)
npm ci
npm test
```

Если вы меняли код плагина для IntelliJ IDEA (`java-trainer/idea-plugin/`), проверьте сборку архива плагина:

```bash
cd java-trainer/idea-plugin
./gradlew buildPlugin
```

---

## 4. Требования к коду и безопасности

- Не добавляйте внешние CDN-скрипты во фронтенд: тренажёр должен работать полностью офлайн после клонирования.
- Все операции чтения/записи исходников и потоков `javac`/`java` должны явно использовать кодировку **UTF-8**.
- Не коммитьте личный файл `.javada/progress.json`, папки `.venv/`, `node_modules/` или `build/`.
