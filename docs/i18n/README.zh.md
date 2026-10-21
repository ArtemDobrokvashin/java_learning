<div align="center">

<img src="../../assets/banner.svg" alt="JavaDa — 交互式 Java 实战训练器" width="100%"/>

<br/>

**[🇷🇺 Русский](../../README.md)** · **[🇬🇧 English](README.en.md)** · **[🇪🇸 Español](README.es.md)** · **[🇩🇪 Deutsch](README.de.md)** · **[🇨🇳 中文](README.zh.md)**

<br/>

[![Java 17–25](https://img.shields.io/badge/Java-17%E2%80%9325-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](#快速开始)
[![IntelliJ IDEA Plugin](https://img.shields.io/badge/IntelliJ_IDEA-2025.3%2B_Plugin-000000?style=for-the-badge&logo=intellijidea&logoColor=white)](../../java-trainer/idea-plugin/README.md)
[![Android App](https://img.shields.io/badge/Android-Jetpack_Compose-3DDC84?style=for-the-badge&logo=android&logoColor=white)](../../java-trainer/android-app/README.md)
[![多语言支持: 5](https://img.shields.io/badge/Languages-RU_%7C_EN_%7C_ES_%7C_DE_%7C_ZH-2563eb?style=for-the-badge)](#快速开始)
[![License: MIT](https://img.shields.io/badge/License-MIT-10b981?style=for-the-badge)](../../LICENSE)

**少一点“以后再看”——从第一课开始亲手编写 Java 代码。**  
本地离线优先（Offline-First）交互式 Java 训练器，内置真实 `javac` 编译器评测、游戏化激励，并支持在 **浏览器**、**IntelliJ IDEA** 与 **Android 手机端** 之间无缝同步学习进度。全面支持 **5 种语言（`RU`、`EN`、`ES`、`DE`、`ZH`）**。

</div>

---

## ✨ 核心特性

| 特性 | 工作方式 |
| :--- | :--- |
| 🎯 **三大客户端 · 统一进度档案** | 可在 **浏览器**、**IntelliJ IDEA 原生 Kotlin 插件** 或 **Android 移动应用 (`Jetpack Compose`)** 中自由切换学习。所有 XP、连胜天数、成就徽章和代码草稿均通过 `.javada/progress.json` 幂等合并同步。 |
| 🌍 **内置 5 国语言切换** | 支持随时在 **俄语 (`RU`)**、**英语 (`EN`)**、**西班牙语 (`ES`)**、**德语 (`DE`)** 与 **中文 (`ZH`)** 之间切换界面与全部 22 个模块的课程包。 |
| ⚡ **真实 `javac` 编译器与 JUnit 5** | 桌面端调用真实 `javac` 与 JVM 运行评测（完整支持 UTF-8、`stdout`/`expected.txt` 比对与 JUnit 5 单元测试）；Android 端内置 100% 离线解析器与求值引擎 (`OfflineTaskValidator`)。 |
| 🧠 **6 种交互式练习题型** | 涵盖示例代码手敲 (`copy`)、单选/多选题 (`choice`)、代码填空 (`fill`)、概念配对 (`matching`)、控制缩进的帕森斯代码拼图 (`parsons`) 以及从零编写完整程序 (`code`)。 |
| 🏆 **模块小测验与高难挑战** | 每课均包含可选 `★ 高难挑战`（+90 XP 与专属徽章），每个模块末尾设有无提示期末小测验（**80%** 及格线，奖励 **310–520 XP**）。 |

---

## 🚀 快速开始

### 方式 A：Web 浏览器端
```bash
cd java-trainer
python3 -m venv .venv
./.venv/bin/python -m pip install -r requirements.txt
./.venv/bin/python server.py
```
打开 **[http://localhost:8000](http://localhost:8000)**，在右上角语言切换器中选择 `ZH · 中文`。

### 方式 B：IntelliJ IDEA 插件 (`java-trainer/idea-plugin/`)
```bash
cd java-trainer/idea-plugin
./gradlew buildPlugin
```

### 方式 C：Android 独立离线应用 (`java-trainer/android-app/`)
```bash
cd java-trainer/android-app
./gradlew assembleDebug
```
