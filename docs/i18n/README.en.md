<div align="center">

<img src="../../assets/banner.svg" alt="JavaDa — Interactive Java Trainer" width="100%"/>

<br/>

**[🇷🇺 Русский](../../README.md)** · **[🇬🇧 English](README.en.md)** · **[🇪🇸 Español](README.es.md)** · **[🇩🇪 Deutsch](README.de.md)** · **[🇨🇳 中文](README.zh.md)**

<br/>

[![Java 17–25](https://img.shields.io/badge/Java-17%E2%80%9325-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](#quick-start)
[![IntelliJ IDEA Plugin](https://img.shields.io/badge/IntelliJ_IDEA-2025.3%2B_Plugin-000000?style=for-the-badge&logo=intellijidea&logoColor=white)](../../java-trainer/idea-plugin/README.md)
[![Android App](https://img.shields.io/badge/Android-Jetpack_Compose-3DDC84?style=for-the-badge&logo=android&logoColor=white)](../../java-trainer/android-app/README.md)
[![Languages: 5](https://img.shields.io/badge/Languages-RU_%7C_EN_%7C_ES_%7C_DE_%7C_ZH-2563eb?style=for-the-badge)](#multi-language-support)
[![License: MIT](https://img.shields.io/badge/License-MIT-10b981?style=for-the-badge)](../../LICENSE)

**Read less “for later” — write real Java code from Lesson 1.**  
A local, offline-first interactive Java trainer with real `javac` compilation, instant verification, gamification, and unified progress synchronization across **Browser**, **IntelliJ IDEA**, and **Android**. Available in **5 languages (`RU`, `EN`, `ES`, `DE`, `ZH`)**.

</div>

---

## ✨ Why JavaDa

| Feature | How It Works |
| :--- | :--- |
| 🎯 **3 Environments — 1 Shared Profile** | Learn in your **Browser**, inside **IntelliJ IDEA** (native Kotlin plugin), or on the go with the **Android App** (`Jetpack Compose`). All XP, streaks, badges, and code drafts synchronize via `.javada/progress.json`. |
| 🌍 **5 Languages Built-In** | Switch interface and course language at any time between **Russian (`RU`)**, **English (`EN`)**, **Spanish (`ES`)**, **German (`DE`)**, and **Chinese (`ZH`)** across Web, IDEA, and Android. |
| ⚡ **Real `javac` & JUnit 5** | Desktop environments compile your code with real `javac` and run it on the JVM (with UTF-8 support, `stdout` / `expected.txt` verification, and JUnit 5 tests), while Android uses a built-in offline evaluator (`OfflineTaskValidator`). |
| 🧠 **6 Interactive Exercise Formats** | Code sample retyping (`copy`), single/multiple `choice`, `fill`-in-the-blanks (with `integerRange` & synonyms), `matching` pairs, `parsons` code puzzle with indentation checking, and full `code` exercises. |
| 🏆 **Mini-Exams, Checkpoints & Hardcore Tasks** | Every lesson includes an optional `★ Hardcore` challenge (+90 XP & mastery badge), and every module ends with an unassisted Mini-Exam (80% pass threshold, +310..520 XP). |

---

## 🗺 Curriculum Overview (22 Modules · 99 Lessons · 825 Tasks)

1. **Data Types, Variables, and Constants** (4 lessons + Mini-Exam)
2. **Branching & Conditionals** (`if-else`, boolean logic, `switch-case`)
3. **Loops & Arrays** (`for`, `while`, `do-while`, 1D arrays)
4. **Methods & Introduction to Classes** (decomposition, parameters, `return`, scope)
5. **Object-Oriented Programming** (state, behavior, constructors, static members)
6. **Inheritance** (`extends`, `@Override`, `abstract`, interfaces, default methods)
7. **Encapsulation** (access modifiers, invariants)
8. **Polymorphism & Generics** (parameterized types, dynamic dispatch)
9. **Java Collections Framework** (`List`, `Set`, `Map`, `equals`/`hashCode`)
10. **Working with Files & I/O** (streams, string parsing, serialization)
11. **Errors & Exceptions in Java** (`try-catch-finally`, custom exceptions, `assert`)
12. **Multithreading & Concurrency** (`Thread`, `synchronized`, `Callable`, `Executors`)
13. **Algorithms & Data Structures** (Big-O complexity, dynamic programming, graphs)
14. **Design Patterns** (Creational, Builder, Singleton, Structural/Proxy, Behavioral/Iterator)
15. **MVC Pattern & Simple Application** (`Model`, `View`, `Controller`)
16. **Databases & SQL** (SQL queries, normalization, JDBC, `JOIN`, indexes)
17. **Introduction to Spring & Spring Web** (Maven, IoC/DI, Spring Boot, `GetMapping`/`PostMapping`)
18. **Databases in Spring** (`JdbcTemplate`, JPA, Hibernate, entity relations)
19. **Introduction to Spring REST** (HTTP, REST controllers, status codes)
20. **Software Testing** (test pyramid, JUnit 5, Mockito)
21. **Software Development Process** (Waterfall, Agile, Scrum, Kanban, team roles)
22. **CI/CD & Docker** (Dockerfiles, containers, GitLab CI/CD pipelines)

---

## 🚀 Quick Start

### A. Web Trainer (Browser)
```bash
cd java-trainer
python3 -m venv .venv
./.venv/bin/python -m pip install -r requirements.txt
./.venv/bin/python server.py
```
Open **[http://localhost:8000](http://localhost:8000)** and select your preferred language (`RU | EN | ES | DE | ZH`) in the top bar.

### B. IntelliJ IDEA Plugin (`java-trainer/idea-plugin/`)
```bash
cd java-trainer/idea-plugin
./gradlew buildPlugin
```
Install `build/distributions/javada-idea-plugin-2.0.0.zip` in **IntelliJ IDEA 2025.3+** (**Settings → Plugins → ⚙️ → Install Plugin from Disk…**).

### C. Android Application (`java-trainer/android-app/`)
```bash
cd java-trainer/android-app
./gradlew assembleDebug
```
Install `app/build/outputs/apk/debug/app-debug.apk` on any Android 8.0+ (API 26+) device.
