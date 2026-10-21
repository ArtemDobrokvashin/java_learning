// Мультиязычная система локализации интерфейса и контента JavaДа (RU, EN, ES, DE, ZH)
const LANG_STORAGE_KEY = 'javada.lang';

export const SUPPORTED_LANGUAGES = [
  { code: 'ru', short: 'RU', label: 'Русский' },
  { code: 'en', short: 'EN', label: 'English' },
  { code: 'es', short: 'ES', label: 'Español' },
  { code: 'de', short: 'DE', label: 'Deutsch' },
  { code: 'zh', short: 'ZH', label: '中文' },
];

const VALID_CODES = new Set(SUPPORTED_LANGUAGES.map((item) => item.code));
let memoryLang = 'ru';

export function getLanguage() {
  try {
    const saved = globalThis.localStorage?.getItem(LANG_STORAGE_KEY);
    if (saved && VALID_CODES.has(saved)) return saved;
  } catch { /* fallback to memory */ }
  return VALID_CODES.has(memoryLang) ? memoryLang : 'ru';
}

export function setLanguage(lang) {
  const normalized = VALID_CODES.has(lang) ? lang : 'ru';
  memoryLang = normalized;
  try {
    globalThis.localStorage?.setItem(LANG_STORAGE_KEY, normalized);
  } catch { /* ignore storage errors */ }
  if (globalThis.document?.documentElement) {
    globalThis.document.documentElement.lang = normalized;
  }
  return normalized;
}

// Полный словарь точных переводов строк интерфейса для EN, ES, DE, ZH
const UI_DICTIONARY = {
  'ТВОЙ JAVA-ТРЕНАЖЁР': {
    en: 'YOUR JAVA TRAINER',
    es: 'TU ENTRENADOR JAVA',
    de: 'DEIN JAVA-TRAINER',
    zh: '你的 JAVA 实战教练',
  },
  'МЕНЮ': {
    en: 'MENU',
    es: 'MENÚ',
    de: 'MENÜ',
    zh: '导航菜单',
  },
  'Карта курса': {
    en: 'Course Map',
    es: 'Mapa del curso',
    de: 'Kurskarte',
    zh: '课程地图',
  },
  'Повторение': {
    en: 'Review',
    es: 'Repaso',
    de: 'Wiederholung',
    zh: '错题复习',
  },
  'К junior': {
    en: 'Junior Path',
    es: 'Ruta Junior',
    de: 'Junior-Pfad',
    zh: '进阶 Junior',
  },
  'Настройки': {
    en: 'Settings',
    es: 'Ajustes',
    de: 'Einstellungen',
    zh: '设置',
  },
  'Локальный тренажёр': {
    en: 'Local Trainer',
    es: 'Entrenador local',
    de: 'Lokaler Trainer',
    zh: '本地离线训练器',
  },
  'Данные остаются у вас': {
    en: 'Your data stays on your device',
    es: 'Tus datos permanecen contigo',
    de: 'Daten bleiben auf deinem Gerät',
    zh: '数据完全保存在本地',
  },
  'МОЙ ПУТЬ': {
    en: 'MY PATH',
    es: 'MI RUTA',
    de: 'MEIN PFAD',
    zh: '学习路径',
  },
  'Практика для junior': {
    en: 'Junior Practice',
    es: 'Práctica para Junior',
    de: 'Junior-Praxis',
    zh: 'Junior 实战练习',
  },
  'Результат': {
    en: 'Result',
    es: 'Resultado',
    de: 'Ergebnis',
    zh: '练习结果',
  },
  'Курс Java': {
    en: 'Java Course',
    es: 'Curso de Java',
    de: 'Java-Kurs',
    zh: 'Java 课程',
  },
  'ВАШ УРОВЕНЬ': {
    en: 'YOUR LEVEL',
    es: 'TU NIVEL',
    de: 'DEIN LEVEL',
    zh: '当前等级',
  },
  'дней': {
    en: 'days',
    es: 'días',
    de: 'Tage',
    zh: '天连胜',
  },
  'Сделано для маленьких побед каждый день ✦': {
    en: 'Built for small wins every single day ✦',
    es: 'Hecho para pequeñas victorias cada día ✦',
    de: 'Gemacht für kleine Erfolge jeden Tag ✦',
    zh: '为每天的微小进步而打造 ✦',
  },
  'Java · в своём темпе': {
    en: 'Java · at your own pace',
    es: 'Java · a tu propio ritmo',
    de: 'Java · in deinem Tempo',
    zh: 'Java · 按你的节奏学习',
  },
  'ЛИЧНЫЙ МАРШРУТ': {
    en: 'PERSONAL LEARNING PATH',
    es: 'RUTA PERSONAL',
    de: 'PERSÖNLICHER LERNPFAD',
    zh: '专属学习路线',
  },
  'Одна задача за другой. Ваш темп, ваш прогресс, ваш Java.': {
    en: 'One task at a time. Your pace, your progress, your Java.',
    es: 'Una tarea tras otra. Tu ritmo, tu progreso, tu Java.',
    de: 'Eine Aufgabe nach der anderen. Dein Tempo, dein Fortschritt, dein Java.',
    zh: '循序渐进，一步一脚印。你的节奏，你的进度，你的 Java。',
  },
  '22 модуля': {
    en: '22 modules',
    es: '22 módulos',
    de: '22 Module',
    zh: '22 个模块',
  },
  'ПРОДОЛЖАЙТЕ В ТОМ ЖЕ ДУХЕ': {
    en: 'KEEP UP THE MOMENTUM',
    es: 'SIGUE CON EL BUEN RITMO',
    de: 'BLEIB DRAN',
    zh: '保持学习势头',
  },
  'Продолжить обучение': {
    en: 'Continue Learning',
    es: 'Continuar aprendiendo',
    de: 'Weiterlernen',
    zh: '继续学习',
  },
  'Скомпилировано!': {
    en: 'Compiled!',
    es: '¡Compilado!',
    de: 'Kompiliert!',
    zh: '编译成功！',
  },
  'ИНТЕРВАЛЬНОЕ ПОВТОРЕНИЕ': {
    en: 'SPACED REPETITION',
    es: 'REPETICIÓN ESPACIADA',
    de: 'VERTEILTE WIEDERHOLUNG',
    zh: '间隔重复复习',
  },
  'Знания под контролем': {
    en: 'Knowledge under control',
    es: 'Conocimientos bajo control',
    de: 'Wissen im Griff',
    zh: '知识点已牢固掌握',
  },
  'Ошибки и старые вопросы появятся здесь.': {
    en: 'Mistakes and older questions will appear here.',
    es: 'Los errores y preguntas antiguas aparecerán aquí.',
    de: 'Fehler und ältere Fragen erscheinen hier.',
    zh: '错题和需要复习的旧题将显示在这里。',
  },
  'Повторите темы, в которых были ошибки.': {
    en: 'Review topics where you made mistakes.',
    es: 'Repasa los temas donde tuviste errores.',
    de: 'Wiederhole Themen mit früheren Fehlern.',
    zh: '温习曾出错的知识点。',
  },
  'ЦЕЛЬ НА СЕГОДНЯ': {
    en: 'TODAY’S GOAL',
    es: 'META DE HOY',
    de: 'TAGESZIEL',
    zh: '今日目标',
  },
  'Сегодня вы уже учились!': {
    en: 'You already studied today!',
    es: '¡Hoy ya has estudiado!',
    de: 'Du hast heute schon gelernt!',
    zh: '今天已完成学习打卡！',
  },
  'Один шаг — и день ваш': {
    en: 'One step and the day is yours',
    es: 'Un paso y el día es tuyo',
    de: 'Ein Schritt — und der Tag gehört dir',
    zh: '完成一个小练习，点亮今日进度',
  },
  'Серия продолжается. Так держать!': {
    en: 'Streak continues. Keep it up!',
    es: '¡La racha continúa! ¡Sigue así!',
    de: 'Serie hält an. Weiter so!',
    zh: '连胜记录继续保持，继续加油！',
  },
  'Решите любое задание, чтобы сохранить серию.': {
    en: 'Solve any exercise to keep your streak alive.',
    es: 'Resuelve cualquier ejercicio para mantener tu racha.',
    de: 'Löse eine Aufgabe, um deine Serie zu halten.',
    zh: '完成任意一道题目即可保持连胜记录。',
  },
  'СЛЕДУЮЩИЙ ШАГ ПОСЛЕ ТЕОРИИ': {
    en: 'NEXT STEP AFTER THEORY',
    es: 'SIGUIENTE PASO TRAS LA TEORÍA',
    de: 'NÄCHSTER SCHRITT NACH DER THEORIE',
    zh: '理论之后的进阶之路',
  },
  'Хотите приблизиться к junior Java backend?': {
    en: 'Want to get closer to Junior Java Backend?',
    es: '¿Quieres acercarte a Junior Java Backend?',
    de: 'Bereit für den Schritt Richtung Junior Java Backend?',
    zh: '想向 Junior Java 后端工程师迈进吗？',
  },
  'Решайте задачи на нескольких входах и соберите два проекта, которые можно показать и объяснить. XP не заменяет практику.': {
    en: 'Solve multi-case labs and build two portfolio projects you can demo and explain.',
    es: 'Resuelve laboratorios con múltiples entradas y construye dos proyectos para tu portafolio.',
    de: 'Löse mehrteilige Laboraufgaben und baue zwei vorzeigbare Projekte.',
    zh: '挑战多组测试数据的算法实验，并完成两个可展示的实战项目。',
  },
  'Практический маршрут': {
    en: 'Practical Track',
    es: 'Ruta práctica',
    de: 'Praxis-Pfad',
    zh: '进入实战路线',
  },
  'ВАША ПРОГРАММА': {
    en: 'YOUR CURRICULUM',
    es: 'TU PROGRAMA',
    de: 'DEIN LEHRPLAN',
    zh: '课程大纲',
  },
  'От первой переменной до CI/CD — шаг за шагом.': {
    en: 'From your first variable to CI/CD — step by step.',
    es: 'Desde tu primera variable hasta CI/CD — paso a paso.',
    de: 'Von der ersten Variable bis CI/CD — Schritt für Schritt.',
    zh: '从第一个变量到 CI/CD 持续交付 —— 循序渐进。',
  },
  'Все модули': {
    en: 'All Modules',
    es: 'Todos los módulos',
    de: 'Alle Module',
    zh: '全部模块',
  },
  'Доступные': {
    en: 'Available',
    es: 'Disponibles',
    de: 'Verfügbar',
    zh: '已解锁',
  },
  'Доступен': {
    en: 'Available',
    es: 'Disponible',
    de: 'Verfügbar',
    zh: '可学习',
  },
  'Пройден': {
    en: 'Completed',
    es: 'Completado',
    de: 'Abgeschlossen',
    zh: '已完成',
  },
  'Закрыт': {
    en: 'Locked',
    es: 'Bloqueado',
    de: 'Gesperrt',
    zh: '未解锁',
  },
  'Скоро': {
    en: 'Planned',
    es: 'Próximamente',
    de: 'Geplant',
    zh: '即将推出',
  },
  'К карте курса': {
    en: 'Back to Course Map',
    es: 'Volver al mapa del curso',
    de: 'Zurück zur Kurskarte',
    zh: '返回课程地图',
  },
  'Вернуться к карте': {
    en: 'Back to Course Map',
    es: 'Volver al mapa',
    de: 'Zurück zur Karte',
    zh: '返回课程地图',
  },
  'Подробная теория': {
    en: 'In-depth Theory',
    es: 'Teoría detallada',
    de: 'Ausführliche Theorie',
    zh: '详尽理论解析',
  },
  'Настоящая Java': {
    en: 'Real Java (javac)',
    es: 'Java real (javac)',
    de: 'Echtes Java (javac)',
    zh: '真实 Java 编译',
  },
  'XP за каждый шаг': {
    en: 'XP for Every Step',
    es: 'XP por cada paso',
    de: 'XP für jeden Schritt',
    zh: '每一步皆可获 XP',
  },
  'ПРОЙДЕНО': {
    en: 'COMPLETED',
    es: 'COMPLETADO',
    de: 'ABGESCHLOSSEN',
    zh: '完成度',
  },
  'УЧИТЕСЬ В СВОЁМ ТЕМПЕ': {
    en: 'LEARN AT YOUR OWN PACE',
    es: 'APRENDE A TU RITМО',
    de: 'LERNE IN DEINEM TEMPO',
    zh: '按自己的节奏学习',
  },
  'Ваши шаги': {
    en: 'Your Steps',
    es: 'Tus pasos',
    de: 'Deine Schritte',
    zh: '学习步骤',
  },
  'Изучено · можно повторить': {
    en: 'Completed · available for review',
    es: 'Completado · puedes repasar',
    de: 'Gelernt · wiederholbar',
    zh: '已学完 · 可随时复习',
  },
  'Перепишите пример · лёгкий · средний · сложный · хард по желанию': {
    en: 'Retype sample · easy · medium · hard · optional hardcore',
    es: 'Transcribe el ejemplo · fácil · medio · difícil · hardcore opcional',
    de: 'Beispiel abtippen · leicht · mittel · schwer · Hardcore optional',
    zh: '手敲示例 · 简单 · 中等 · 困难 · 可选高难挑战',
  },
  'Откроется после предыдущего урока': {
    en: 'Unlocks after the previous lesson',
    es: 'Se desbloquea tras la lección anterior',
    de: 'Wird nach der vorherigen Lektion freigeschaltet',
    zh: '完成上一课后解锁',
  },
  'Повторить': {
    en: 'Review',
    es: 'Repasar',
    de: 'Wiederholen',
    zh: '复习',
  },
  'Начать': {
    en: 'Start',
    es: 'Empezar',
    de: 'Starten',
    zh: '开始',
  },
  'Хард пройден · повторить': {
    en: 'Hardcore mastered · review',
    es: 'Hardcore superado · repasar',
    de: 'Hardcore gemeistert · wiederholen',
    zh: '高难挑战已通关 · 再次练习',
  },
  'Необязательный хард · 90 XP и достижение': {
    en: 'Optional Hardcore · 90 XP & achievement badge',
    es: 'Hardcore opcional · 90 XP y logro',
    de: 'Optionaler Hardcore · 90 XP & Abzeichen',
    zh: '可选高难挑战 · +90 XP 与专属徽章',
  },
  'ПРОМЕЖУТОЧНЫЙ СТОП-УРОК': {
    en: 'INTERMEDIATE CHECKPOINT',
    es: 'CONTROL INTERMEDIO',
    de: 'ZWISCHENKONTROLLE',
    zh: '阶段性关卡测验',
  },
  'Стоп-урок': {
    en: 'Checkpoint Lesson',
    es: 'Lección de control',
    de: 'Kontrolllektion',
    zh: '关卡测验',
  },
  '5 вопросов · для зачёта нужно 4 правильных': {
    en: '5 questions · 4 correct answers required to pass',
    es: '5 preguntas · necesitas 4 aciertos para aprobar',
    de: '5 Fragen · 4 richtige Antworten zum Bestehen nötig',
    zh: '共 5 题 · 答对 4 题即可通过',
  },
  'ЗАКРЕПЛЕНИЕ МАТЕРИАЛА': {
    en: 'CONSOLIDATE KNOWLEDGE',
    es: 'CONSOLIDACIÓN DEL MATERIAL',
    de: 'WISSEN FESTIGEN',
    zh: '巩固所学知识',
  },
  'Финальная практика': {
    en: 'Final Practice',
    es: 'Práctica final',
    de: 'Abschlusspraxis',
    zh: '模块综合实战',
  },
  '7 разнообразных заданий · код проверяется javac': {
    en: '7 diverse exercises · code verified by javac',
    es: '7 ejercicios variados · código verificado con javac',
    de: '7 abwechslungsreiche Aufgaben · Code geprüft mit javac',
    zh: '7 道综合题型 · 使用 javac 编译器真实评测',
  },
  'ФИНАЛЬНОЕ ИСПЫТАНИЕ · БЕЗ ПОТЕРИ ЖИЗНЕЙ': {
    en: 'FINAL CHALLENGE · UNLIMITED ATTEMPTS',
    es: 'PRUEBA FINAL · SIN PÉRDIDA DE VIDAS',
    de: 'ABSCHLUSSPRÜFUNG · OHNE LEBENSVERLUST',
    zh: '期末挑战 · 无生命值限制',
  },
  'Мини-экзамен': {
    en: 'Mini-Exam',
    es: 'Mini-examen',
    de: 'Mini-Prüfung',
    zh: '模块小测验',
  },
  'Сдано': {
    en: 'Passed',
    es: 'Aprobado',
    de: 'Bestanden',
    zh: '已通过',
  },
  'Сдать': {
    en: 'Take Checkpoint',
    es: 'Realizar',
    de: 'Ablegen',
    zh: '开始测验',
  },
  'Открыть': {
    en: 'Open',
    es: 'Abrir',
    de: 'Öffnen',
    zh: '打开',
  },
  'После практики': {
    en: 'After Practice',
    es: 'Tras la práctica',
    de: 'Nach der Praxis',
    zh: '完成实战后解锁',
  },
  'Знания растут с практикой': {
    en: 'Knowledge grows with practice',
    es: 'El conocimiento crece con la práctica',
    de: 'Wissen wächst mit der Praxis',
    zh: '实践出真知',
  },
  'Уроков пройдено': {
    en: 'Lessons completed',
    es: 'Lecciones completadas',
    de: 'Lektionen abgeschlossen',
    zh: '已完成课时',
  },
  'Достижений за хард': {
    en: 'Hardcore badges',
    es: 'Logros hardcore',
    de: 'Hardcore-Abzeichen',
    zh: '高难挑战徽章',
  },
  'Практика': {
    en: 'Practice',
    es: 'Práctica',
    de: 'Praxis',
    zh: '综合实战',
  },
  'Готово ✓': {
    en: 'Done ✓',
    es: 'Listo ✓',
    de: 'Fertig ✓',
    zh: '已完成 ✓',
  },
  'Сдан · 🏆': {
    en: 'Passed · 🏆',
    es: 'Aprobado · 🏆',
    de: 'Bestanden · 🏆',
    zh: '已通过 · 🏆',
  },
  'Впереди': {
    en: 'Ahead',
    es: 'Pendiente',
    de: 'Offen',
    zh: '待完成',
  },
  'Язык интерфейса и курса': {
    en: 'Interface & Course Language',
    es: 'Idioma de la interfaz y del curso',
    de: 'Sprache der Oberfläche und des Kurses',
    zh: '界面与课程语言',
  },
  'Детали проверки': {
    en: 'Verification details',
    es: 'Detalles de la verificación',
    de: 'Prüfungsdetails',
    zh: '评测详情',
  },
  'Вывод программы': {
    en: 'Program output',
    es: 'Salida del programa',
    de: 'Programmausgabe',
    zh: '程序输出',
  },
};

export function tr(ruText) {
  const lang = getLanguage();
  if (lang === 'ru' || !ruText) return ruText;
  const entry = UI_DICTIONARY[ruText];
  if (entry && entry[lang]) return entry[lang];
  return ruText;
}

const REGEX_TRANSLATIONS = {
  en: [
    [/^МОДУЛЬ (\d+)$/, 'MODULE $1'],
    [/^(\d+) из (\d+) шагов$/, '$1 of $2 steps'],
    [/^(\d+) из (\d+) модулей завершено$/, '$1 of $2 modules completed'],
    [/^(\d+) из (\d+) завершено$/, '$1 of $2 completed'],
    [/^(\d+) \/ (\d+) XP до нового уровня$/, '$1 / $2 XP to next level'],
    [/^XP · ур\. (\d+)$/, 'XP · lvl $1'],
    [/^УРОК (\d+)(.*)$/, 'LESSON $1$2'],
    [/· НАЧНИТЕ ЗДЕСЬ/g, '· START HERE'],
    [/^(\d+) заданий · зачёт от 80% · \+(\d+) XP$/, '$1 tasks · 80% to pass · +$2 XP'],
  ],
  es: [
    [/^МОДУЛЬ (\d+)$/, 'MÓDULO $1'],
    [/^(\d+) из (\d+) шагов$/, '$1 de $2 pasos'],
    [/^(\d+) из (\d+) модулей завершено$/, '$1 de $2 módulos completados'],
    [/^(\d+) из (\d+) завершено$/, '$1 de $2 completados'],
    [/^(\d+) \/ (\d+) XP до нового уровня$/, '$1 / $2 XP para el siguiente nivel'],
    [/^XP · ур\. (\d+)$/, 'XP · niv. $1'],
    [/^УРОК (\d+)(.*)$/, 'LECCIÓN $1$2'],
    [/· НАЧНИТЕ ЗДЕСЬ/g, '· EMPIEZA AQUÍ'],
    [/^(\d+) заданий · зачёт от 80% · \+(\d+) XP$/, '$1 tareas · 80% para aprobar · +$2 XP'],
  ],
  de: [
    [/^МОДУЛЬ (\d+)$/, 'MODUL $1'],
    [/^(\d+) из (\d+) шагов$/, '$1 von $2 Schritten'],
    [/^(\d+) из (\d+) модулей завершено$/, '$1 von $2 Modulen abgeschlossen'],
    [/^(\d+) из (\d+) завершено$/, '$1 von $2 abgeschlossen'],
    [/^(\d+) \/ (\d+) XP до нового уровня$/, '$1 / $2 XP bis zum nächsten Level'],
    [/^XP · ур\. (\d+)$/, 'XP · Lvl. $1'],
    [/^УРОК (\d+)(.*)$/, 'LEKTION $1$2'],
    [/· НАЧНИТЕ ЗДЕСЬ/g, '· HIER STARTEN'],
    [/^(\d+) заданий · зачёт от 80% · \+(\d+) XP$/, '$1 Aufgaben · ab 80% bestanden · +$2 XP'],
  ],
  zh: [
    [/^МОДУЛЬ (\d+)$/, '模块 $1'],
    [/^(\d+) из (\d+) шагов$/, '已完成 $1 / $2 步'],
    [/^(\d+) из (\d+) модулей завершено$/, '已完成 $1 / $2 个模块'],
    [/^(\d+) из (\d+) завершено$/, '已完成 $1 / $2'],
    [/^(\d+) \/ (\d+) XP до нового уровня$/, '距下一等级还需 $1 / $2 XP'],
    [/^XP · ур\. (\d+)$/, 'XP · 等级 $1'],
    [/^УРОК (\d+)(.*)$/, '第 $1 课$2'],
    [/· НАЧНИТЕ ЗДЕСЬ/g, '· 从这里开始'],
    [/^(\d+) заданий · зачёт от 80% · \+(\d+) XP$/, '$1 道题 · 80% 及格 · +$2 XP'],
  ],
};

function translateSingleString(str, lang) {
  const trimmed = str.trim();
  if (!trimmed) return str;
  const exact = UI_DICTIONARY[trimmed]?.[lang];
  if (exact) return str.replace(trimmed, exact);
  for (const [pattern, replacement] of REGEX_TRANSLATIONS[lang] || []) {
    if (pattern.test(trimmed)) {
      return str.replace(trimmed, trimmed.replace(pattern, replacement));
    }
  }
  return str;
}

export function translateDom(root) {
  const lang = getLanguage();
  if (lang === 'ru' || !root || !globalThis.document) return;
  const walker = globalThis.document.createTreeWalker(root, 4 /* NodeFilter.SHOW_TEXT */);
  const textNodes = [];
  let node = walker.nextNode();
  while (node) {
    const parentTag = node.parentElement?.tagName;
    if (parentTag !== 'PRE' && parentTag !== 'CODE' && parentTag !== 'TEXTAREA' && parentTag !== 'SCRIPT') {
      textNodes.push(node);
    }
    node = walker.nextNode();
  }
  for (const textNode of textNodes) {
    const updated = translateSingleString(textNode.nodeValue, lang);
    if (updated !== textNode.nodeValue) textNode.nodeValue = updated;
  }
}
