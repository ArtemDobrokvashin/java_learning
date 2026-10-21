# Changelog

Все заметные изменения в проекте **JavaДа** документируются в этом файле. Формат основан на [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/), проект следует [Semantic Versioning](https://semver.org/lang/ru/).

---

## [v2.0.0] — 2026-10-21

### 🚀 Крупный релиз экосистемы JavaДа 2.0 (Web + IntelliJ IDEA + Android + 5 языков)

#### Добавлено
- **📱 Автономное Android-приложение «JavaДа Mobile» (`java-trainer/android-app/`)**:
  - Нативное приложение на **Kotlin 2.1 + Jetpack Compose (Material 3)**, работающее **100% офлайн** без разрешения `INTERNET` и без необходимости запускать внешний сервер.
  - Автоматическая упаковка всех 22 модулей, 99 уроков, 825 заданий, 22 мини-экзаменов и 5 лабораторных работ в `assets/` APK при сборке через Gradle-задачу `syncCourseAssets`.
  - Встроенный офлайн-валидатор и интерпретатор `OfflineTaskValidator.kt` для всех 6 типов задач (`copy`, `choice`, `fill`, `matching`, `parsons`, `code`).
  - Двусторонняя синхронизация единого файла прогресса `.javada/progress.json` с ПК (Импорт / Экспорт через системный файловый пикер Android `Storage Access Framework`) с монотонным слиянием наград (`xpLedger`) и черновиков в `MobileProgressManager.kt`.
- **🧩 Полнофункциональный автономный плагин для IntelliJ IDEA 2025.3+ (`java-trainer/idea-plugin/`)**:
  - Полная реализация на **Kotlin** (JDK 25 Toolchain, `--enable-preview`, IntelliJ Platform Gradle Plugin).
  - Окно инструмента **«JavaДа»** с деревом курса (`CourseTree.kt`), поиском `TreeSpeedSearch`, Split View теории в формате Markdown/HTML и интерактивными виджетами задач.
  - Нативный `TaskChecker.kt` с поддержкой компиляции и запуска через `GeneralCommandLine` (`javac`/`java` со сверкой `stdout` и `expected.txt`) и запуска модульных тестов через `JUnit Platform Console` (JUnit 5).
  - Живая синхронизация `.javada/progress.json` между браузером и IDE через `WatchService` и атомарную запись (`ATOMIC_MOVE`).
- **🌍 Мультиязычная локализация всех версий и курса на 5 языков (`RU`, `EN`, `ES`, `DE`, `ZH`)**:
  - Генератор локализованных пакетов курса `java-trainer/tools/generate_i18n_courses.py` и готовые наборы данных в `java-trainer/frontend/data/i18n/{en,es,de,zh}/` (`modules.json`, `module-01..22.json`, `labs.json`).
  - Переключатель языка интерфейса и курса в веб-тренажёре (`frontend/js/i18n.js`), в плагине IntelliJ IDEA (`JavadaI18n.kt`) и в Android-приложении (`values-{en,es,de,zh}/strings.xml` + переключатель в TopAppBar).
  - Многоязычная документация репозитория: `README.md` (RU) и переводы в `docs/i18n/README.{en,es,de,zh}.md`.
- **🎨 Витрина проекта и инфраструктура Open-Source**:
  - Кастомная SVG-графика (`assets/banner.svg` и `assets/ecosystem.svg`).
  - Файлы сообщества: `LICENSE` (MIT), `CONTRIBUTING.md`, `SECURITY.md`, шаблоны Issue (`.github/ISSUE_TEMPLATE/`), шаблон Pull Request (`.github/PULL_REQUEST_TEMPLATE.md`) и шаблон CI (`java-trainer/docs/ci-workflow.yml`).
