<div align="center">

<img src="../../assets/banner.svg" alt="JavaDa — Interaktiver Java-Trainer" width="100%"/>

<br/>

**[🇷🇺 Русский](../../README.md)** · **[🇬🇧 English](README.en.md)** · **[🇪🇸 Español](README.es.md)** · **[🇩🇪 Deutsch](README.de.md)** · **[🇨🇳 中文](README.zh.md)**

<br/>

[![Java 17–25](https://img.shields.io/badge/Java-17%E2%80%9325-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](#schnellstart)
[![IntelliJ IDEA Plugin](https://img.shields.io/badge/IntelliJ_IDEA-2025.3%2B_Plugin-000000?style=for-the-badge&logo=intellijidea&logoColor=white)](../../java-trainer/idea-plugin/README.md)
[![Android App](https://img.shields.io/badge/Android-Jetpack_Compose-3DDC84?style=for-the-badge&logo=android&logoColor=white)](../../java-trainer/android-app/README.md)
[![Sprachen: 5](https://img.shields.io/badge/Sprachen-RU_%7C_EN_%7C_ES_%7C_DE_%7C_ZH-2563eb?style=for-the-badge)](#schnellstart)
[![Lizenz: MIT](https://img.shields.io/badge/License-MIT-10b981?style=for-the-badge)](../../LICENSE)

**Weniger „für später“ lesen — ab der ersten Lektion echten Java-Code schreiben.**  
Lokaler, interaktiver Offline-First-Java-Trainer mit echtem `javac`-Compiler, sofortiger Überprüfung, Gamification und Fortschrittssynchronisation zwischen **Browser**, **IntelliJ IDEA** und **Android**. Verfügbar in **5 Sprachen (`RU`, `EN`, `ES`, `DE`, `ZH`)**.

</div>

---

## ✨ Warum JavaDa?

| Vorteil | Funktionsweise |
| :--- | :--- |
| 🎯 **3 Umgebungen — 1 gemeinsames Profil** | Lernen Sie im **Browser**, direkt in **IntelliJ IDEA** (natives Kotlin-Plugin) oder unterwegs mit der **Android-App** (`Jetpack Compose`). Alle XP, Serien, Abzeichen und Code-Entwürfe werden über `.javada/progress.json` synchronisiert. |
| 🌍 **5 integrierte Sprachen** | Wechseln Sie jederzeit zwischen **Russisch (`RU`)**, **Englisch (`EN`)**, **Spanisch (`ES`)**, **Deutsch (`DE`)** und **Chinesisch (`ZH`)**. |
| ⚡ **Echter `javac`-Compiler & JUnit 5** | Auf dem Desktop wird Ihr Code mit `javac` kompiliert und auf der JVM ausgeführt (inkl. UTF-8 und JUnit 5); unter Android prüft ein integrierter Offline-Interpreter (`OfflineTaskValidator`). |
| 🧠 **6 interaktive Aufgabenformate** | Code-Vorlage abtippen (`copy`), Auswahl (`choice`), Lückentext (`fill`), Zuordnung (`matching`), Parsons-Code-Baukasten (`parsons`) und freies Programmieren (`code`). |
| 🏆 **Mini-Prüfungen & Hardcore-Aufgaben** | Jede Lektion bietet eine optionale `★ Hardcore`-Aufgabe (+90 XP) und jedes der 22 Module schließt mit einer Mini-Prüfung ohne Hinweise ab (Bestehensgrenze **80%**, Bonus **310–520 XP**). |

---

## 🚀 Schnellstart

### A. Web-Trainer (Browser)
```bash
cd java-trainer
python3 -m venv .venv
./.venv/bin/python -m pip install -r requirements.txt
./.venv/bin/python server.py
```
Öffnen Sie **[http://localhost:8000](http://localhost:8000)** und wählen Sie oben rechts `DE · Deutsch`.

### B. IntelliJ IDEA Plugin (`java-trainer/idea-plugin/`)
```bash
cd java-trainer/idea-plugin
./gradlew buildPlugin
```

### C. Android-App (`java-trainer/android-app/`)
```bash
cd java-trainer/android-app
./gradlew assembleDebug
```
