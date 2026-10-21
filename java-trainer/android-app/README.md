# 📱 JavaДа Mobile — Автономное Android-приложение (Kotlin + Jetpack Compose)

Полностью автономное (100% Offline) Android-приложение тренажёра **JavaДа** на **Kotlin + Jetpack Compose (Material 3)**. Позволяет изучать теорию, решать задачи всех 6 форматов, проходить финальные мини-экзамены и лабораторные работы на смартфоне или планшете без подключения к интернету и без необходимости запускать внешний сервер, сохраняя совместимость с единым профилем `.javada/progress.json`.

---

## Возможности приложения

1. **100% автономная работа (Zero-Network / Offline-First)**:
   - При сборке APK Gradle-задача `syncCourseAssets` автоматически упаковывает актуальные JSON-файлы курса из `../frontend/data/` (`modules.json`, `module-01..22.json`, `labs.json`) и `../private/lab_cases.json` в `assets/` приложения (без дублирования файлов в Git).
   - Приложение вообще не запрашивает разрешение `INTERNET` в `AndroidManifest.xml`.
2. **Полная программа курса**:
   - **22 модуля**, **99 уроков** с развёрнутой теорией, примерами кода, разбором типичных ошибок и пошаговыми руководствами;
   - **825 заданий курса**, включая стоп-уроки, финальную практику модулей и **146 экзаменационных задач**;
   - **5 лабораторных работ** раздела «К junior».
3. **Все 6 интерактивных форматов заданий на экране смартфона**:
   - `copy` — ручное переписывание образца с проверкой лексического потока токенов Java;
   - `choice` — одиночный и множественный выбор;
   - `fill` — заполнение пропусков в шаблоне кода (чипы выбора, текстовый ввод, проверка синонимов `accepted` и числовых диапазонов `integerRange`);
   - `matching` — сопоставление пар понятий;
   - `parsons` — интерактивный конструктор строк кода с управлением порядком (`↑`/`↓`) и отступами вложенности (`←`/`→`, уровни `0..3`);
   - `code` — мобильный редактор `Main.java` с панелью быстрого ввода символов Java (`{`, `}`, `(`, `)`, `;`, `"`, `=`, `System.out.println();`), автосохранением черновиков и встроенным автономным анализатором/интерпретатором `OfflineTaskValidator`.
4. **Режим мини-экзамена (22 экзамена)**:
   - Последовательное прохождение 5–8 задач модуля с отключёнными подсказками.
   - При результате **≥ 80%** (`correct * 100 >= 80 * total`, без округления вверх) начисляются бонусные **310–520 XP**, выдаётся бейдж `exam:mXX` и разблокируется следующий модуль.
5. **Двусторонняя синхронизация `progress.json` с ПК (Браузер и IntelliJ IDEA)**:
   - Кнопки **Импорт** (`FileUpload`) и **Экспорт** (`FileDownload`) в верхней панели приложения используют системный файловый пикер Android (`Storage Access Framework`).
   - Вы можете экспортировать `progress.json` со смартфона на ПК или импортировать `progress.json` с ПК на смартфон: `MobileProgressManager` выполнит монотонное слияние (`mergeProfiles` + `xpLedger`) без потери или задвоения XP, серий дней и черновиков.

---

## Архитектура и структура проекта

```text
java-trainer/android-app/
├── README.md
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── app/
    ├── build.gradle.kts                 # Конфигурация Android SDK 35, Compose BOM и задачи syncCourseAssets
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/io/javada/android/
        │   │   ├── MainActivity.kt                  # Экраны Jetpack Compose (Карта курса, Урок, Задачи, Мини-экзамен)
        │   │   ├── data/
        │   │   │   ├── CourseModels.kt              # Модели модулей, уроков, теории, задач и экзаменов
        │   │   │   ├── AssetCourseRepository.kt     # Чтение JSON курса из Android AssetManager
        │   │   │   └── MobileProgressManager.kt     # Атомарное хранение и слияние .javada/progress.json
        │   │   ├── domain/
        │   │   │   └── OfflineTaskValidator.kt      # Автономный валидатор всех 6 типов задач и интерпретатор Main.java
        │   │   └── ui/
        │   │       ├── JavadaViewModel.kt           # Управление состоянием (StateFlow, навигация, экзамены)
        │   │       └── theme/
        │   │           └── JavadaTheme.kt           # Тёмная и светлая палитра Material 3
        │   └── res/
        │       └── values/
        │           └── strings.xml
        └── test/
            └── java/io/javada/android/
                └── OfflineValidatorAndProgressTest.kt # Модульные тесты валидатора, экзаменов и слияния progress.json
```

---

## Сборка и запуск в Android Studio

1. Откройте **Android Studio Ladybug (2024.2.1+)** или новее с установленным **Android SDK 35** и **JDK 17+**.
2. Выберите **File → Open…** и укажите каталог `java-trainer/android-app`.
3. Дождитесь завершения синхронизации Gradle (задача `syncCourseAssets` автоматически скопирует JSON-файлы курса из `../frontend/data` и `../private` в `build/generated/courseAssets`).
4. Нажмите **Run (`Shift+F10`)** для запуска на Android-эмуляторе или физическом устройстве (Android 8.0 / API 26 и выше), либо соберите APK из терминала:
   ```bash
   cd java-trainer/android-app
   ./gradlew assembleDebug
   ```
   Готовый APK будет создан по пути `app/build/outputs/apk/debug/app-debug.apk`.
