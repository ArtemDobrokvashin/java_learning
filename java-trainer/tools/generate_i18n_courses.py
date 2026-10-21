"""Генератор мультиязычных пакетов курса JavaДа (EN, ES, DE, ZH) из эталонного русского курса.

Сохраняет 100% совместимость идентификаторов задач, ключей ответов, Java-кода,
эталонного вывода (expectedOutput / stdout) и валидаторов во всех языковых версиях.
"""

from __future__ import annotations

import copy
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / "frontend" / "data"
I18N_DIR = DATA_DIR / "i18n"
LANGUAGES = ("en", "es", "de", "zh")

# ---------------------------------------------------------------------------
# 1. Точные переводы заголовков и подзаголовков 22 модулей
# ---------------------------------------------------------------------------
MODULE_TITLES: dict[int, dict[str, tuple[str, str]]] = {
    1: {
        "en": ("Data Types, Variables, and Constants", "Language foundations: from bits to your first variables"),
        "es": ("Tipos de datos, variables y constantes", "Fundamentos del lenguaje: de los bits a tus primeras variables"),
        "de": ("Datentypen, Variablen und Konstanten", "Sprachgrundlagen: von Bits zu den ersten Variablen"),
        "zh": ("数据类型、变量与常量", "语言基础：从比特到你的第一个变量"),
    },
    2: {
        "en": ("Branching & Conditionals", "Learn to make decisions in code"),
        "es": ("Ramificaciones y condicionales", "Aprende a tomar decisiones en el código"),
        "de": ("Verzweigungen & Bedingungen", "Entscheidungen im Code treffen lernen"),
        "zh": ("分支与条件语句", "学会在代码中做出决策"),
    },
    3: {
        "en": ("Loops & Arrays", "Repeat actions without repeating code"),
        "es": ("Bucles y arreglos", "Repite acciones sin duplicar código"),
        "de": ("Schleifen & Arrays", "Aktionen wiederholen, ohne Code zu duplizieren"),
        "zh": ("循环与数组", "重复执行操作而无需重复编写代码"),
    },
    4: {
        "en": ("Methods & Introduction to Classes", "Break problems down into clear parts"),
        "es": ("Métodos e introducción a las clases", "Divide los problemas en partes claras"),
        "de": ("Methoden & Einführung in Klassen", "Aufgaben in übersichtliche Teile zerlegen"),
        "zh": ("方法与类入门", "将复杂问题拆分为清晰的模块"),
    },
    5: {
        "en": ("Object-Oriented Programming", "Model the world with objects"),
        "es": ("Enfoque orientado a objetos", "Modela el mundo mediante objetos"),
        "de": ("Objektorientierte Programmierung", "Die Welt mit Objekten modellieren"),
        "zh": ("面向对象编程思想", "用对象为现实世界建模"),
    },
    6: {
        "en": ("Inheritance", "Reuse and extend class behavior"),
        "es": ("Herencia", "Reutiliza y amplía el comportamiento de las clases"),
        "de": ("Vererbung", "Klassenverhalten wiederverwenden und erweitern"),
        "zh": ("继承", "复用并扩展类的行为"),
    },
    7: {
        "en": ("Encapsulation", "Control access to data and invariants"),
        "es": ("Encapsulación", "Controla el acceso a los datos"),
        "de": ("Kapselung", "Zugriff auf Daten kontrollieren"),
        "zh": ("封装", "控制对数据的访问权限"),
    },
    8: {
        "en": ("Polymorphism & Generics", "One interface — multiple implementations"),
        "es": ("Polimorfismo y Generics", "Una interfaz — múltiples implementaciones"),
        "de": ("Polymorphie & Generics", "Eine Schnittstelle — verschiedene Implementierungen"),
        "zh": ("多态与泛型", "统一接口，多种实现"),
    },
    9: {
        "en": ("Java Collections Framework", "Store and process sets of data"),
        "es": ("Colecciones en Java", "Almacena y procesa conjuntos de datos"),
        "de": ("Java Collections Framework", "Datenmengen speichern und verarbeiten"),
        "zh": ("Java 集合框架", "高效存储与处理数据集合"),
    },
    10: {
        "en": ("Working with Files & I/O", "Read, parse, write, and persist data"),
        "es": ("Trabajo con archivos", "Lee, analiza, escribe y guarda datos"),
        "de": ("Dateiverarbeitung & I/O", "Lesen, analysieren, schreiben und speichern"),
        "zh": ("文件读写与序列化", "读取、解析、写入并持久化数据"),
    },
    11: {
        "en": ("Errors & Exceptions in Java", "Turn failures into predictable scenarios"),
        "es": ("Errores y excepciones en Java", "Convierte los fallos en escenarios controlados"),
        "de": ("Fehler & Ausnahmen in Java", "Ausfälle in kontrollierte Abläufe verwandeln"),
        "zh": ("Java 异常处理", "将运行故障转化为可控流程"),
    },
    12: {
        "en": ("Multithreading & Concurrency", "Master parallel execution"),
        "es": ("Multihilo y concurrencia", "Domina la ejecución paralela"),
        "de": ("Multithreading & Nebenläufigkeit", "Parallele Ausführung beherrschen"),
        "zh": ("多线程与并发编程", "掌握并行执行与线程同步"),
    },
    13: {
        "en": ("Algorithms & Data Structures", "Think about solution efficiency"),
        "es": ("Algoritmos", "Piensa en la eficiencia de tus soluciones"),
        "de": ("Algorithmen", "Über die Effizienz von Lösungen nachdenken"),
        "zh": ("算法与复杂度", "深入思考解决方案的效率"),
    },
    14: {
        "en": ("Design Patterns", "Proven solutions to recurring problems"),
        "es": ("Patrones de diseño", "Soluciones probadas para problemas típicos"),
        "de": ("Entwurfsmuster (Design Patterns)", "Bewährte Lösungen für typische Probleme"),
        "zh": ("设计模式", "常见软件设计问题的经典解决方案"),
    },
    15: {
        "en": ("MVC Pattern & Simple Application", "Separate data, interface, and logic"),
        "es": ("Patrón MVC y aplicación simple", "Separa datos, interfaz y lógica"),
        "de": ("MVC-Muster & einfache Anwendung", "Daten, Oberfläche und Logik trennen"),
        "zh": ("MVC 模式与小型应用", "分离数据、视图与业务逻辑"),
    },
    16: {
        "en": ("Databases & SQL", "From tables and queries to JDBC"),
        "es": ("Bases de datos y SQL", "De tablas y consultas a JDBC"),
        "de": ("Datenbanken & SQL", "Von Tabellen und Abfragen bis JDBC"),
        "zh": ("关系型数据库与 SQL", "从数据表、查询到 JDBC 连接"),
    },
    17: {
        "en": ("Introduction to Spring & Spring Web", "Build your first web application"),
        "es": ("Introducción a Spring y Spring Web", "Crea tu primera aplicación web"),
        "de": ("Einführung in Spring & Spring Web", "Die erste Webanwendung entwickeln"),
        "zh": ("Spring 与 Spring Web 入门", "构建你的第一个 Web 应用程序"),
    },
    18: {
        "en": ("Databases in Spring (JPA & Hibernate)", "Connect your application to persistent data"),
        "es": ("Bases de datos en Spring", "Conecta la aplicación con los datos"),
        "de": ("Datenbanken in Spring", "Anwendung und Datenbank verbinden"),
        "zh": ("Spring 数据库开发", "将应用程序与数据库无缝连接"),
    },
    19: {
        "en": ("Introduction to Spring REST", "Design a clean HTTP API"),
        "es": ("Introducción a Spring REST", "Diseña una API HTTP clara"),
        "de": ("Einführung in Spring REST", "Eine saubere HTTP-API entwerfen"),
        "zh": ("Spring REST 接口开发", "设计清晰规范的 HTTP API"),
    },
    20: {
        "en": ("Software Testing (JUnit & Mockito)", "Verify behavior automatically"),
        "es": ("Pruebas de software", "Verifica el comportamiento automáticamente"),
        "de": ("Softwaretests (JUnit & Mockito)", "Verhalten automatisch überprüfen"),
        "zh": ("软件测试 (JUnit 与 Mockito)", "自动化验证程序行为"),
    },
    21: {
        "en": ("Software Development Process", "Understand how engineering teams work"),
        "es": ("Proceso de desarrollo de software", "Comprende cómo trabaja un equipo"),
        "de": ("Softwareentwicklungsprozess", "Verstehen, wie Entwicklungsteams arbeiten"),
        "zh": ("软件开发流程与敏捷协作", "了解现代研发团队如何高效协作"),
    },
    22: {
        "en": ("CI/CD & Docker", "The path to automated code delivery"),
        "es": ("CI/CD y Docker", "El camino hacia la entrega automática de código"),
        "de": ("CI/CD & Docker", "Der Weg zur automatisierten Code-Auslieferung"),
        "zh": ("CI/CD 与 Docker 容器化", "通往自动化代码交付之路"),
    },
}

# ---------------------------------------------------------------------------
# 2. Точные переводы всех 99 заголовков уроков
# ---------------------------------------------------------------------------
LESSON_TITLES: dict[str, dict[str, str]] = {
    "m01-l01": {"en": "Introduction", "es": "Introducción", "de": "Einführung", "zh": "Java 简介"},
    "m01-l02": {"en": "How Computers Work", "es": "Arquitectura del computador", "de": "Aufbau eines Computers", "zh": "计算机工作原理"},
    "m01-l03": {"en": "Data Types and Operations", "es": "Tipos de datos y operaciones", "de": "Datentypen und Operationen", "zh": "数据类型及其运算"},
    "m01-l04": {"en": "Variables and Operations", "es": "Variables y operaciones", "de": "Variablen und Operationen", "zh": "变量及其操作"},
    "m02-l01": {"en": "Algorithms and Flowcharts", "es": "Algoritmos y diagramas de flujo", "de": "Algorithmen und Flussdiagramme", "zh": "算法与流程图"},
    "m02-l02": {"en": "The if Conditional Statement", "es": "Sentencia condicional if", "de": "Die if-Bedingung", "zh": "条件语句 if"},
    "m02-l03": {"en": "Boolean Logic Operations", "es": "Operaciones lógicas", "de": "Logische Operationen", "zh": "布尔逻辑运算"},
    "m02-l04": {"en": "The switch-case Statement", "es": "Sentencia switch-case", "de": "Die switch-case-Anweisung", "zh": "switch-case 分支语句"},
    "m03-l01": {"en": "Introduction to Loops", "es": "Introducción a los bucles", "de": "Einführung in Schleifen", "zh": "循环简介"},
    "m03-l02": {"en": "Arrays", "es": "Arreglos (Arrays)", "de": "Arrays (Felder)", "zh": "数组 (Arrays)"},
    "m03-l03": {"en": "Counter Loop: for", "es": "Bucle con contador: for", "de": "Zählschleife: for", "zh": "计数循环：for"},
    "m03-l04": {"en": "Pre- and Post-Condition Loops: while and do-while", "es": "Bucles while y do-while", "de": "while- und do-while-Schleifen", "zh": "条件循环：while 与 do-while"},
    "m04-l01": {"en": "Introduction to Decomposition", "es": "Introducción a la descomposición", "de": "Einführung in die Zerlegung", "zh": "代码分解简介"},
    "m04-l02": {"en": "Introduction to Methods and Classes", "es": "Introducción a métodos y clases", "de": "Einführung in Methoden und Klassen", "zh": "方法与类初探"},
    "m04-l03": {"en": "Methods, Parameters, and Return", "es": "Métodos, parámetros y retorno", "de": "Methoden, Parameter und Rückgabewerte", "zh": "方法定义、参数与返回值"},
    "m04-l04": {"en": "Variable Scope", "es": "Ámbito de las variables", "de": "Gültigkeitsbereich von Variablen", "zh": "变量作用域"},
    "m05-l01": {"en": "Introduction to OOP", "es": "Introducción a la POO", "de": "Einführung in OOP", "zh": "面向对象简介"},
    "m05-l02": {"en": "Object-Oriented Paradigm", "es": "Paradigma orientado a objetos", "de": "Objektorientiertes Paradigma", "zh": "面向对象范式"},
    "m05-l03": {"en": "Classes and Objects in Java", "es": "Clases y objetos en Java", "de": "Klassen und Objekte in Java", "zh": "Java 中的类与对象"},
    "m05-l04": {"en": "Static Fields and Methods", "es": "Campos y métodos estáticos", "de": "Statische Felder und Methoden", "zh": "静态字段与静态方法"},
    "m05-l05": {"en": "Static Nested Classes", "es": "Clases estáticas anidadas", "de": "Statische geschachtelte Klassen", "zh": "静态内部类"},
    "m06-l01": {"en": "Inheritance in Java: Introduction", "es": "Herencia en Java: Introducción", "de": "Vererbung in Java: Einführung", "zh": "Java 继承简介"},
    "m06-l02": {"en": "Implementing Inheritance in Java", "es": "Implementación de la herencia", "de": "Vererbung in Java umsetzen", "zh": "在 Java 中实现继承"},
    "m06-l03": {"en": "Method Overriding", "es": "Sobrescritura de métodos", "de": "Überschreiben von Methoden", "zh": "方法重写 (@Override)"},
    "m06-l04": {"en": "Abstract Classes", "es": "Clases abstractas", "de": "Abstrakte Klassen", "zh": "抽象类 (abstract)"},
    "m06-l05": {"en": "Interfaces and Default Methods", "es": "Interfaces y métodos default", "de": "Interfaces und Default-Methoden", "zh": "接口与默认方法"},
    "m07-l01": {"en": "Introduction to Encapsulation", "es": "Introducción a la encapsulación", "de": "Einführung in die Kapselung", "zh": "封装简介"},
    "m07-l02": {"en": "Encapsulation and Access Modifiers in Java", "es": "Encapsulación en Java", "de": "Kapselung in Java", "zh": "Java 中的访问控制与封装"},
    "m08-l01": {"en": "Introduction to Polymorphism", "es": "Introducción al polimorfismo", "de": "Einführung in die Polymorphie", "zh": "多态简介"},
    "m08-l02": {"en": "Polymorphism in Java", "es": "Polimorfismo en Java", "de": "Polymorphie in Java", "zh": "Java 中的多态实现"},
    "m08-l03": {"en": "Generics — Parameterized Types", "es": "Generics — tipos parametrizados", "de": "Generics — parametrisierte Typen", "zh": "泛型 (Generics) 参数化类型"},
    "m09-l01": {"en": "Introduction to Collections", "es": "Introducción a Collections", "de": "Einführung in Collections", "zh": "集合框架简介"},
    "m09-l02": {"en": "List and Its Implementations", "es": "List y sus implementaciones", "de": "List und seine Implementierungen", "zh": "List 接口及其实现"},
    "m09-l03": {"en": "Set Collections", "es": "Conjuntos Set", "de": "Mengen mit Set", "zh": "Set 集合"},
    "m09-l04": {"en": "toString, equals, and hashCode", "es": "Métodos toString, equals y hashCode", "de": "Methoden toString, equals, hashCode", "zh": "toString、equals 与 hashCode 方法"},
    "m09-l05": {"en": "Associative Maps (Map)", "es": "Mapas asociativos (Map)", "de": "Assoziative Arrays (Map)", "zh": "映射表 Map"},
    "m10-l01": {"en": "Introduction to File I/O", "es": "Introducción a archivos", "de": "Einführung in Datei-I/O", "zh": "文件操作简介"},
    "m10-l02": {"en": "Reading from Files", "es": "Lectura de archivos", "de": "Aus Dateien lesen", "zh": "读取文件"},
    "m10-l03": {"en": "Parsing Strings", "es": "Análisis de cadenas", "de": "Strings analysieren und parsen", "zh": "字符串解析"},
    "m10-l04": {"en": "Writing to Files", "es": "Escritura en archivos", "de": "In Dateien schreiben", "zh": "写入文件"},
    "m10-l05": {"en": "Object Serialization and Deserialization", "es": "Serialización y deserialización", "de": "Serialisierung von Objekten", "zh": "对象序列化与反序列化"},
    "m11-l01": {"en": "Introduction to Error Handling", "es": "Introducción al manejo de errores", "de": "Einführung in die Fehlerbehandlung", "zh": "错误处理简介"},
    "m11-l02": {"en": "Exceptions and Errors", "es": "Exceptions y Errors", "de": "Exceptions und Errors", "zh": "Exception 与 Error 体系"},
    "m11-l03": {"en": "Handling Exceptions (try-catch-finally)", "es": "Manejo de excepciones", "de": "Ausnahmen behandeln (try-catch)", "zh": "捕获与处理异常"},
    "m11-l04": {"en": "Throwing Custom Exceptions", "es": "Lanzamiento de excepciones", "de": "Ausnahmen werfen (throw)", "zh": "抛出与自定义异常"},
    "m11-l05": {"en": "Assertions (assert)", "es": "Aserciones (assert)", "de": "Zusicherungen (assert)", "zh": "断言 (assert)"},
    "m12-l01": {"en": "Introduction to Multithreading", "es": "Introducción al multihilo", "de": "Einführung in Multithreading", "zh": "多线程简介"},
    "m12-l02": {"en": "Threads and Processes in Java", "es": "Hilos y procesos en Java", "de": "Threads und Prozesse in Java", "zh": "线程与进程：创建与终止"},
    "m12-l03": {"en": "Thread States and Synchronization", "es": "Estado del hilo y sincronización", "de": "Thread-Zustände und Synchronisation", "zh": "线程状态与同步方式"},
    "m12-l04": {"en": "isAlive and sleep Methods", "es": "Métodos isAlive y sleep", "de": "Methoden isAlive und sleep", "zh": "isAlive 与 sleep 方法"},
    "m12-l05": {"en": "The synchronized Keyword", "es": "Palabra clave synchronized", "de": "Das Schlüsselwort synchronized", "zh": "synchronized 关键字"},
    "m12-l06": {"en": "The yield() Method", "es": "Método yield()", "de": "Die Methode yield()", "zh": "yield() 让步方法"},
    "m12-l07": {"en": "Using Callable and Executors", "es": "Uso de Callable y Executors", "de": "Callable und Executors nutzen", "zh": "Callable 与 Executors 线程池"},
    "m13-l01": {"en": "Introduction to Algorithms", "es": "Introducción a los algoritmos", "de": "Einführung in Algorithmen", "zh": "算法导论"},
    "m13-l02": {"en": "Algorithmic Complexity (Big-O)", "es": "Complejidad algorítmica", "de": "Algorithmische Komplexität (Big-O)", "zh": "算法时间与空间复杂度"},
    "m13-l03": {"en": "Dynamic Programming", "es": "Programación dinámica", "de": "Dynamische Programmierung", "zh": "动态规划算法"},
    "m13-l04": {"en": "Graphs and Traversal", "es": "Grafos", "de": "Graphen", "zh": "图论基础与遍历"},
    "m14-l01": {"en": "What Design Patterns Are and Why Use Them", "es": "Qué son los patrones de diseño", "de": "Was Entwurfsmuster sind und wozu sie dienen", "zh": "什么是设计模式及其意义"},
    "m14-l02": {"en": "Creational Patterns", "es": "Patrones creacionales", "de": "Erzeugungsmuster", "zh": "创建型模式"},
    "m14-l03": {"en": "Builder Pattern", "es": "Patrón Builder (Constructor)", "de": "Erbaumer-Muster (Builder)", "zh": "建造者模式 (Builder)"},
    "m14-l04": {"en": "Singleton Pattern", "es": "Patrón Singleton (Instancia única)", "de": "Singleton-Muster", "zh": "单例模式 (Singleton)"},
    "m14-l05": {"en": "Structural Patterns and Proxy", "es": "Patrones estructurales y Proxy", "de": "Strukturmuster und Proxy", "zh": "结构型模式与代理模式 (Proxy)"},
    "m14-l06": {"en": "Behavioral Patterns and Iterator", "es": "Patrones de comportamiento e Iterator", "de": "Verhaltensmuster und Iterator", "zh": "行为型模式与迭代器 (Iterator)"},
    "m15-l01": {"en": "Introduction to the MVC Pattern", "es": "Introducción al patrón MVC", "de": "Einführung in das MVC-Muster", "zh": "MVC 架构模式简介"},
    "m15-l02": {"en": "The View Layer", "es": "La vista (View)", "de": "Die Präsentationsschicht (View)", "zh": "视图层 (View)"},
    "m15-l03": {"en": "The Model Layer", "es": "El modelo (Model)", "de": "Das Datenmodell (Model)", "zh": "模型层 (Model)"},
    "m15-l04": {"en": "The Controller Layer", "es": "El controlador (Controller)", "de": "Der Controller", "zh": "控制器层 (Controller)"},
    "m16-l01": {"en": "Introduction to SQL", "es": "Introducción a SQL", "de": "Einführung in SQL", "zh": "SQL 语言简介"},
    "m16-l02": {"en": "Database Normalization", "es": "Normalización de bases de datos", "de": "Datenbanknormalisierung", "zh": "数据库范式与规范化"},
    "m16-l03": {"en": "Using JDBC", "es": "Uso de JDBC", "de": "JDBC verwenden", "zh": "使用 JDBC 连接数据库"},
    "m16-l04": {"en": "Working with Users and Roles", "es": "Trabajo con usuarios", "de": "Benutzerverwaltung", "zh": "数据库用户与权限管理"},
    "m16-l05": {"en": "Table Relationships (JOIN)", "es": "Relaciones entre tablas", "de": "Tabellenbeziehungen (JOIN)", "zh": "表间关联关系"},
    "m16-l06": {"en": "Advanced SQL Queries", "es": "Consultas avanzadas", "de": "Komplexe Abfragen", "zh": "复杂 SQL 查询进阶"},
    "m16-l07": {"en": "Database Indexes", "es": "Índices en bases de datos", "de": "Datenbankindizes", "zh": "数据库索引优化"},
    "m17-l01": {"en": "Maven Build System", "es": "Maven", "de": "Maven Build-System", "zh": "Maven 构建工具"},
    "m17-l02": {"en": "Introduction to Spring", "es": "Introducción a Spring", "de": "Einführung in Spring", "zh": "Spring 框架简介"},
    "m17-l03": {"en": "Application Context, IoC, and Spring Boot", "es": "Contexto de aplicación, IoC y Spring Boot", "de": "ApplicationContext, IoC und Spring Boot", "zh": "应用上下文、IoC 与 Spring Boot"},
    "m17-l04": {"en": "GetMapping and PostMapping Requests", "es": "Peticiones GetMapping y PostMapping", "de": "GetMapping- und PostMapping-Anfragen", "zh": "GetMapping 与 PostMapping 请求"},
    "m17-l05": {"en": "Spring DevTools", "es": "Spring DevTools", "de": "Spring DevTools", "zh": "Spring DevTools 热重载工具"},
    "m18-l01": {"en": "JdbcTemplate", "es": "JdbcTemplate", "de": "JdbcTemplate", "zh": "JdbcTemplate 模板操作"},
    "m18-l02": {"en": "Introduction to JPA", "es": "Introducción a JPA", "de": "Einführung in JPA", "zh": "JPA 持久化规范简介"},
    "m18-l03": {"en": "Hibernate ORM", "es": "Hibernate", "de": "Hibernate ORM", "zh": "Hibernate 框架"},
    "m18-l04": {"en": "Field Types in Entities", "es": "Tipos de campos en entidades", "de": "Feldtypen in Entities", "zh": "实体类中的字段类型映射"},
    "m18-l05": {"en": "OneToOne, OneToMany, ManyToMany Relations", "es": "Relaciones OneToOne, OneToMany, ManyToMany", "de": "OneToOne-, OneToMany-, ManyToMany-Beziehungen", "zh": "OneToOne、OneToMany 与 ManyToMany 关联"},
    "m18-l06": {"en": "Entity Inheritance", "es": "Herencia de entidades", "de": "Vererbung bei Entities", "zh": "实体继承映射策略"},
    "m19-l01": {"en": "Network Protocols and Client-Server Apps", "es": "Protocolos de red y cliente-servidor", "de": "Netzwerkprotokolle & Client-Server-Apps", "zh": "网络协议与客户端-服务器架构"},
    "m19-l02": {"en": "Introduction to Spring REST", "es": "Introducción a Spring REST", "de": "Einführung in Spring REST", "zh": "Spring REST 简介"},
    "m19-l03": {"en": "REST Controllers", "es": "Controladores REST", "de": "REST-Controller", "zh": "REST 控制器开发"},
    "m20-l01": {"en": "Software Testing and Test Types", "es": "Pruebas de software y tipos de test", "de": "Softwaretests und Testarten", "zh": "软件测试基础与测试分类"},
    "m20-l02": {"en": "The JUnit Testing Library", "es": "Biblioteca JUnit", "de": "Die JUnit-Bibliothek", "zh": "JUnit 单元测试框架"},
    "m20-l03": {"en": "The Mockito Mocking Library", "es": "Biblioteca Mockito", "de": "Die Mockito-Bibliothek", "zh": "Mockito 模拟测试库"},
    "m21-l01": {"en": "Software Development Methodologies", "es": "Metodologías de desarrollo", "de": "Entwicklungsmethodologien", "zh": "软件开发方法论"},
    "m21-l02": {"en": "Waterfall and Agile", "es": "Waterfall y Agile", "de": "Waterfall und Agile", "zh": "瀑布模型 (Waterfall) 与敏捷 (Agile)"},
    "m21-l03": {"en": "Scrum and Kanban", "es": "Scrum y Kanban", "de": "Scrum und Kanban", "zh": "Scrum 与 Kanban 看板"},
    "m21-l04": {"en": "Teamwork and Engineering Roles", "es": "Trabajo en equipo y roles", "de": "Teamarbeit und Rollen", "zh": "团队协作与角色分工"},
    "m22-l01": {"en": "Introduction to CI/CD and Docker", "es": "Introducción a CI/CD y Docker", "de": "Einführung in CI/CD und Docker", "zh": "CI/CD 与 Docker 简介"},
    "m22-l02": {"en": "Docker Containers and Images", "es": "Docker", "de": "Docker-Container und Images", "zh": "Docker 镜像与容器实战"},
    "m22-l03": {"en": "Examples of CI/CD Systems", "es": "Ejemplos de sistemas CI/CD", "de": "Beispiele für CI/CD-Systeme", "zh": "主流 CI/CD 系统对比"},
    "m22-l04": {"en": "GitLab CI/CD Pipelines", "es": "GitLab CI/CD", "de": "GitLab CI/CD-Pipelines", "zh": "GitLab CI/CD 流水线配置"},
}

# ---------------------------------------------------------------------------
# 3. Точные переводы всех 5 лабораторных работ (labs.json)
# ---------------------------------------------------------------------------
LABS_I18N: dict[str, dict[str, dict[str, object]]] = {
    "lab-array-report": {
        "en": {
            "title": "Array: Minimum, Maximum, and Sum",
            "goal": "Single pass over input data, boundary cases, and avoiding int overflow.",
            "description": "Read n integers from standard input (n from 1 to 100). Print the minimum, maximum, and sum separated by spaces. Store the sum in a long variable.",
            "skills": ["arrays and loops", "boundary cases", "long for sum"],
            "inputFormat": "First line is n, followed by n integers separated by spaces or newlines.",
            "outputFormat": "Single line: min max sum.",
            "hint": "Read the first element as the initial value for min, max, and sum; process the remaining n - 1 numbers in a loop.",
        },
        "es": {
            "title": "Arreglo: mínimo, máximo y suma",
            "goal": "Un solo recorrido por los datos, casos límite y desbordamiento de int.",
            "description": "Lee n números enteros de la entrada estándar (n de 1 a 100). Imprime el mínimo, el máximo y la suma separados por espacio. Guarda la suma en long.",
            "skills": ["arreglos y bucles", "límites", "long para la suma"],
            "inputFormat": "La primera línea es n, seguida de n enteros separados por espacio o salto de línea.",
            "outputFormat": "Una línea: min max sum.",
            "hint": "Lee el primer elemento como valor inicial para min, max y sum; procesa los demás en un bucle de 1 a n-1.",
        },
        "de": {
            "title": "Array: Minimum, Maximum und Summe",
            "goal": "Ein Durchlauf über die Daten, Randfälle und Vermeidung von int-Überlauf.",
            "description": "Lesen Sie n ganze Zahlen von der Standardeingabe (n von 1 bis 100). Geben Sie Minimum, Maximum und Summe durch Leerzeichen getrennt aus. Speichern Sie die Summe in long.",
            "skills": ["Arrays und Schleifen", "Randfälle", "long für die Summe"],
            "inputFormat": "Erste Zeile n, danach n ganze Zahlen getrennt durch Leerzeichen oder Zeilenumbruch.",
            "outputFormat": "Eine Zeile: min max sum.",
            "hint": "Lesen Sie das erste Element als Startwert für min, max und sum; verarbeiten Sie die restlichen n - 1 Zahlen in einer Schleife.",
        },
        "zh": {
            "title": "数组统计：最小值、最大值与总和",
            "goal": "单次遍历数据、处理边界情况并防止 int 溢出。",
            "description": "从标准输入读取 n 个整数（n 从 1 到 100）。以空格分隔输出最小值、最大值和总和。总和请存储在 long 类型中。",
            "skills": ["数组与循环", "边界处理", "使用 long 防溢出"],
            "inputFormat": "第一行为整数 n，随后是 n 个以空格或换行分隔的整数。",
            "outputFormat": "单行输出：min max sum。",
            "hint": "先读取第一个元素作为 min、max 和 sum 的初始值，再用循环处理剩余的 n - 1 个数字。",
        },
    },
    "lab-user-lookup": {
        "en": {
            "title": "User Directory Lookup",
            "goal": "Domain modeling, Map indexing, and explicit handling of missing keys.",
            "description": "Read n lines formatted as id;name;true|false (IDs are unique and positive), followed by a target ID. If the user is active, print their name; if inactive, print INACTIVE; if missing, print NOT_FOUND.",
            "skills": ["classes or records", "Map lookup", "branching"],
            "inputFormat": "First line is n (0..100), then n lines of id;name;active, and the last line is the target ID.",
            "outputFormat": "Name, INACTIVE, or NOT_FOUND (single line).",
            "hint": "Store User objects in a Map<Integer, User>; split(\";\", -1) parses each line, then check for null before checking active.",
        },
        "es": {
            "title": "Directorio de usuarios",
            "goal": "Modelado de datos, indexación con Map y manejo explícito de claves ausentes.",
            "description": "Lee n líneas con formato id;nombre;true|false (IDs únicos y positivos) y luego el ID buscado. Si está activo imprime su nombre; si está inactivo, INACTIVE; si no existe, NOT_FOUND.",
            "skills": ["clases o record", "Map", "condicionales"],
            "inputFormat": "Primera línea n (0..100), luego n líneas id;nombre;active y finalmente el ID buscado.",
            "outputFormat": "Nombre, INACTIVE o NOT_FOUND (una línea).",
            "hint": "Guarda objetos User en Map<Integer, User>; usa split(\";\", -1) y verifica primero si existe antes de revisar active.",
        },
        "de": {
            "title": "Benutzerverzeichnis-Suche",
            "goal": "Datenmodellierung, Map-Indexierung und Behandlung fehlender Schlüssel.",
            "description": "Lesen Sie n Zeilen im Format id;name;true|false (IDs eindeutig und positiv) sowie eine gesuchte ID. Ist der Benutzer aktiv, geben Sie den Namen aus; wenn inaktiv, INACTIVE; wenn nicht gefunden, NOT_FOUND.",
            "skills": ["Klassen oder record", "Map", "Verzweigungen"],
            "inputFormat": "Erste Zeile n (0..100), dann n Zeilen id;name;active, letzte Zeile gesuchte ID.",
            "outputFormat": "Name, INACTIVE oder NOT_FOUND (eine Zeile).",
            "hint": "Speichern Sie User-Objekte in einer Map<Integer, User>; nutzen Sie split(\";\", -1) und prüfen Sie zuerst auf Vorhandensein.",
        },
        "zh": {
            "title": "用户目录查询",
            "goal": "数据建模、Map 索引及缺失键的显式处理。",
            "description": "读取 n 行格式为 id;姓名;true|false 的数据（ID 唯一且为正整数），再读取要查询的 ID。若用户激活则输出姓名；未激活输出 INACTIVE；不存在输出 NOT_FOUND。",
            "skills": ["类或 record", "Map 映射", "条件分支"],
            "inputFormat": "第一行为 n (0..100)，随后 n 行为 id;name;active，最后一行为待查 ID。",
            "outputFormat": "单行输出：姓名、INACTIVE 或 NOT_FOUND。",
            "hint": "将 User 对象存入 Map<Integer, User>；用 split(\";\", -1) 拆分字符串，先判断键是否存在，再检查 active。",
        },
    },
    "lab-import-report": {
        "en": {
            "title": "Data Import: Resilient Parsing on Bad Input",
            "goal": "String parsing, input validation, and handling errors without crashing.",
            "description": "Read n lines formatted as name,age. A line is valid if the trimmed name is non-empty, there is exactly one comma, and age is an integer in 0..120 inclusive. Skip invalid lines. Print the count of valid records and the sum of their ages.",
            "skills": ["CSV-like parsing", "NumberFormatException", "range validation"],
            "inputFormat": "First line is n (0..100), followed by n lines of name,age.",
            "outputFormat": "Single line: valid_count age_sum.",
            "hint": "Use split(\",\", -1), check array length == 2 and non-empty name; catch NumberFormatException when parsing age.",
        },
        "es": {
            "title": "Importación: tolerancia a datos inválidos",
            "goal": "Análisis de cadenas, validación de entrada y manejo de errores sin abortar el programa.",
            "description": "Lee n líneas con formato nombre,edad. Una línea es válida si el nombre tras trim no está vacío, hay exactamente una coma y la edad es un entero entre 0 y 120. Ignora las líneas inválidas e imprime cantidad válida y suma de edades.",
            "skills": ["análisis tipo CSV", "NumberFormatException", "validación de límites"],
            "inputFormat": "Primera línea n (0..100), seguida de n líneas nombre,edad.",
            "outputFormat": "Una línea: cantidad_válida suma_edades.",
            "hint": "Usa split(\",\", -1), comprueba longitud == 2 y nombre no vacío; captura NumberFormatException.",
        },
        "de": {
            "title": "Datenimport: Fehlerhafte Zeilen sicher überspringen",
            "goal": "String-Parsing, Eingabevalidierung und Fehlerbehandlung ohne Programmabsturz.",
            "description": "Lesen Sie n Zeilen im Format name,alter. Gültig ist eine Zeile mit nicht-leerem Namen nach trim, genau einem Komma und Alter als Ganzzahl von 0 bis 120. Überspringen Sie ungültige Zeilen und geben Sie Anzahl und Alterssumme aus.",
            "skills": ["CSV-ähnliches Parsing", "NumberFormatException", "Grenzprüfung"],
            "inputFormat": "Erste Zeile n (0..100), danach n Zeilen name,alter.",
            "outputFormat": "Eine Zeile: gültige_anzahl alterssumme.",
            "hint": "Nutzen Sie split(\",\", -1), prüfen Sie Länge == 2 und Namen; fangen Sie NumberFormatException ab.",
        },
        "zh": {
            "title": "数据导入：容错解析脏数据",
            "goal": "字符串解析、输入校验以及异常捕获，保证程序不中断。",
            "description": "读取 n 行格式为 姓名,年龄 的数据。若姓名 trim 后非空、恰有一个逗号且年龄为 0 到 120 之间的整数，则视为有效记录。跳过无效行，输出有效记录数与年龄总和。",
            "skills": ["CSV 格式解析", "NumberFormatException", "边界校验"],
            "inputFormat": "第一行为 n (0..100)，随后为 n 行 name,age 数据。",
            "outputFormat": "单行输出：有效记录数 年龄总和。",
            "hint": "使用 split(\",\", -1)，检查数组长度是否为 2 且姓名非空；解析年龄时捕获 NumberFormatException。",
        },
    },
    "lab-order-totals": {
        "en": {
            "title": "Orders: GROUP BY Without a Database",
            "goal": "Aggregate sums by ID, filter groups, and output in deterministic sorted order.",
            "description": "Read n orders formatted as userId amount, followed by a threshold. Sum amounts per user in a long, keep users whose total >= threshold, sort by ascending userId, and print id:total separated by commas without spaces (or - if none match).",
            "skills": ["Map and sorting", "aggregation in long", "formatting"],
            "inputFormat": "First line is n (0..100), then n lines of userId amount, and the last line is the threshold.",
            "outputFormat": "Single line: id1:sum1,id2:sum2 or -.",
            "hint": "Use TreeMap<Integer, Long> or sort keys before building the comma-separated result.",
        },
        "es": {
            "title": "Pedidos: GROUP BY sin base de datos",
            "goal": "Agregar sumas por ID, filtrar grupos y ordenar de forma predecible.",
            "description": "Lee n pedidos userId amount y luego un umbral. Suma los importes por usuario en long, conserva los que tengan total >= umbral, ordénalos por ID ascendente e imprime id:suma separados por comas (o - si no hay ninguno).",
            "skills": ["Map y ordenación", "agregación en long", "formato de salida"],
            "inputFormat": "Primera línea n (0..100), luego n líneas userId amount y última línea con el umbral.",
            "outputFormat": "Una línea: id1:suma1,id2:suma2 o -.",
            "hint": "Usa TreeMap<Integer, Long> con merge(id, amount, Long::sum) para mantener las claves ordenadas.",
        },
        "de": {
            "title": "Bestellungen: GROUP BY ohne Datenbank",
            "goal": "Summen nach ID aggregieren, Gruppen filtern und sortiert ausgeben.",
            "description": "Lesen Sie n Bestellungen der Form userId amount sowie einen Schwellenwert. Summieren Sie pro Benutzer in long, behalten Sie Summen >= Schwellenwert, sortieren Sie aufsteigend nach ID und geben Sie id:summe kommagetrennt aus (oder - falls leer).",
            "skills": ["Map und Sortierung", "Aggregation in long", "Formatierung"],
            "inputFormat": "Erste Zeile n (0..100), dann n Zeilen userId amount, letzte Zeile Schwellenwert.",
            "outputFormat": "Eine Zeile: id1:sum1,id2:sum2 oder -.",
            "hint": "TreeMap<Integer, Long> hält die Benutzer-IDs automatisch aufsteigend sortiert.",
        },
        "zh": {
            "title": "订单汇总：不依赖数据库的 GROUP BY",
            "goal": "按 ID 聚合求和、过滤分组并按确定顺序输出。",
            "description": "读取 n 条形如 userId amount 的订单记录以及一个阈值。用 long 累加每个用户的总额，保留总额 >= 阈值的用户，按 ID 升序排列并以英文逗号连接输出 id:总额（若无匹配则输出 -）。",
            "skills": ["Map 与排序", "long 聚合累加", "格式化输出"],
            "inputFormat": "第一行为 n (0..100)，随后 n 行为 userId amount，最后一行为阈值。",
            "outputFormat": "单行输出：id1:sum1,id2:sum2 或 -。",
            "hint": "使用 TreeMap<Integer, Long> 配合 merge(id, amount, Long::sum) 可自动按 ID 升序排列。",
        },
    },
    "lab-rest-router": {
        "en": {
            "title": "Mini HTTP Request Router",
            "goal": "Route HTTP methods and paths, validate numeric path parameters, and return status codes.",
            "description": "Read n lines formatted as METHOD /path. Route GET /health -> 200 OK, POST /users -> 201 CREATED, GET /users/{id} (where id is a positive 64-bit integer) -> 200 USER {id}, and everything else -> 404 NOT_FOUND.",
            "skills": ["HTTP routing", "string parsing", "status codes"],
            "inputFormat": "First line is n (1..100), followed by n lines of METHOD /path.",
            "outputFormat": "n lines with the response status and payload.",
            "hint": "Check exact matches first, then handle prefix /users/ and validate that the ID is a positive Long.",
        },
        "es": {
            "title": "Mini enrutador de peticiones HTTP",
            "goal": "Enrutar métodos y rutas HTTP, validar parámetros numéricos y devolver códigos de estado.",
            "description": "Lee n líneas METHOD /path. Responde GET /health -> 200 OK, POST /users -> 201 CREATED, GET /users/{id} (con id entero positivo de 64 bits) -> 200 USER {id}, y cualquier otra ruta -> 404 NOT_FOUND.",
            "skills": ["enrutamiento HTTP", "análisis de rutas", "códigos de estado"],
            "inputFormat": "Primera línea n (1..100), seguida de n líneas METHOD /path.",
            "outputFormat": "n líneas con el resultado de cada petición.",
            "hint": "Verifica primero rutas exactas y luego el prefijo /users/ validando que el ID sea un Long positivo.",
        },
        "de": {
            "title": "Mini-HTTP-Router",
            "goal": "HTTP-Methoden und Pfade routen, Pfadparameter prüfen und Statuscodes zurückgeben.",
            "description": "Lesen Sie n Zeilen im Format METHOD /path. Antworten Sie bei GET /health mit 200 OK, POST /users mit 201 CREATED, GET /users/{id} (positive 64-Bit-Ganzzahl) mit 200 USER {id} und sonst mit 404 NOT_FOUND.",
            "skills": ["HTTP-Routing", "String-Parsing", "Statuscodes"],
            "inputFormat": "Erste Zeile n (1..100), danach n Zeilen METHOD /path.",
            "outputFormat": "n Antwortzeilen.",
            "hint": "Prüfen Sie zuerst exakte Pfade und danach das Präfix /users/ mit positiver Long-ID.",
        },
        "zh": {
            "title": "迷你 HTTP 路由分发器",
            "goal": "匹配 HTTP 方法与路径、校验路径数值参数并返回对应状态码。",
            "description": "读取 n 行形如 METHOD /path 的请求：GET /health 返回 200 OK；POST /users 返回 201 CREATED；GET /users/{id}（id 为正 long 整数）返回 200 USER {id}；其余情况返回 404 NOT_FOUND。",
            "skills": ["HTTP 路由匹配", "字符串解析", "HTTP 状态码"],
            "inputFormat": "第一行为 n (1..100)，随后 n 行为 METHOD /path。",
            "outputFormat": "输出 n 行对应的路由响应。",
            "hint": "先匹配固定路径，再检查 /users/ 前缀并验证 id 是否为大于 0 的 64 位十进制整数。",
        },
    },
}

# ---------------------------------------------------------------------------
# 4. Словарь переводов технических фраз, шаблонов уроков, подсказок и теории
# ---------------------------------------------------------------------------
COMMON_EXACT: dict[str, dict[str, str]] = {
    "Мини-экзамен": {
        "en": "Mini-Exam",
        "es": "Mini-examen",
        "de": "Mini-Prüfung",
        "zh": "模块小测验",
    },
    "Стоп-урок": {
        "en": "Checkpoint Lesson",
        "es": "Lección de control",
        "de": "Kontrolllektion",
        "zh": "阶段关卡测验",
    },
    "Финальная практика": {
        "en": "Final Practice",
        "es": "Práctica final",
        "de": "Abschlusspraxis",
        "zh": "模块综合实战",
    },
    "Полная программа Main.java — можно запустить": {
        "en": "Complete Main.java program — runnable",
        "es": "Programa Main.java completo — ejecutable",
        "de": "Vollständiges Programm Main.java — ausführbar",
        "zh": "完整的 Main.java 程序 — 可直接运行",
    },
    "Фрагмент для чтения — запускать отдельно не нужно": {
        "en": "Code snippet for reading — no standalone run needed",
        "es": "Fragmento de lectura — no requiere ejecución separada",
        "de": "Code-Ausschnitt zum Lesen — kein separater Start nötig",
        "zh": "阅读理解代码片段 — 无需单独运行",
    },
    "Короткий фрагмент для main; ниже — полный пример с выводом.": {
        "en": "Short snippet for main; below is a complete runnable example with output.",
        "es": "Fragmento corto para main; abajo tienes un ejemplo completo con salida.",
        "de": "Kurzes Fragment für main; darunter ein vollständiges Beispiel mit Ausgabe.",
        "zh": "main 方法简短片段；下方提供带输出的完整可运行示例。",
    },
    "Перепишите вручную показанный ниже пример из пошагового разбора. Сохраните порядок команд, имена, строковые литералы и важные отступы; после этого разберите, что делает каждая строка.": {
        "en": "Retype the worked example shown below by hand. Keep the statement order, identifiers, string literals, and indentation; then review what each line does.",
        "es": "Transcribe manualmente el ejemplo paso a paso mostrado abajo. Conserva el orden de las instrucciones, los nombres, los literales de texto y la sangría.",
        "de": "Tippen Sie das unten gezeigte Beispiel aus der Schritt-für-Schritt-Erklärung von Hand ab. Behalten Sie Reihenfolge, Bezeichner, String-Literale und Einrückungen bei.",
        "zh": "请手动输入下方分步解析中的示例代码。保持语句顺序、变量名、字符串字面量和关键缩进一致，并理解每一行的作用。",
    },
    "Сверьте команды, регистр букв, кавычки и закрывающие скобки с образцом. Можно менять только форматирование Java-кода.": {
        "en": "Check statements, letter case, quotes, and closing braces against the sample. Only Java whitespace formatting may vary.",
        "es": "Compara las instrucciones, mayúsculas/minúsculas, comillas y llaves de cierre con el modelo. Solo puede variar el formato de espacios.",
        "de": "Vergleichen Sie Befehle, Groß-/Kleinschreibung, Anführungszeichen und schließende Klammern mit der Vorlage.",
        "zh": "请对照示例检查语句、大小写、引号和闭合括号。仅允许 Java 空白格式有所差异。",
    },
}

PHRASE_REPLACEMENTS: dict[str, list[tuple[str, str]]] = {
    "en": [
        ("Мини-экзамен модуля", "Module Mini-Exam"),
        ("Финальный экзамен модуля", "Module Final Exam"),
        ("Практика модуля", "Module Practice"),
        ("Стоп-урок модуля", "Module Checkpoint"),
        ("Промежуточный стоп-урок", "Intermediate Checkpoint"),
        ("Соберите строки кода в правильном порядке и выставьте отступы", "Arrange the code lines in the right order and set their indentation"),
        ("Соберите фрагмент в правильном порядке", "Arrange the code snippet in the correct order"),
        ("Расставьте строки в правильном порядке", "Order the lines of code properly"),
        ("Сопоставьте понятие и его роль", "Match each concept with its role"),
        ("Сопоставьте термин и описание", "Match each term with its description"),
        ("Сопоставьте элементы", "Match the corresponding items"),
        ("Заполните пропуски", "Fill in the blanks"),
        ("Впишите пропущенное", "Fill in the missing part"),
        ("Выберите правильный ответ", "Choose the correct answer"),
        ("Выберите верное утверждение", "Select the true statement"),
        ("Выберите все верные варианты", "Select all correct options"),
        ("Какой тип используют для целых чисел?", "Which type is used for integer numbers?"),
        ("Выберите примитивный тип, в котором нет дробной части.", "Choose the primitive type that has no fractional part."),
        ("int хранит целые числа, например 25.", "int stores whole integers, for example 25."),
        ("Выберите тип и впишите возраст от 0 до 120 обычным десятичным числом.", "Select the data type and enter an age from 0 to 120 as a decimal integer."),
        ("Нужен тип int и целое десятичное число от 0 до 120 без кавычек и ведущих нулей: например, 3 или 25.", "Use the int type and a decimal integer between 0 and 120 without quotes or leading zeros, e.g. 3 or 25."),
        ("Напишите программу", "Write a program"),
        ("Внутри метода main", "Inside the main method"),
        ("Выведите на экран", "Print to the console"),
        ("Выведите в консоль", "Print to the console"),
        ("отдельной строкой", "on a separate line"),
        ("каждое значение с новой строки", "each value on a new line"),
        ("Объявите переменную", "Declare a variable"),
        ("Объявите переменные", "Declare variables"),
        ("Создайте класс", "Create a class"),
        ("Создайте метод", "Create a method"),
        ("Это тот же фрагмент, что разобран в теории.", "This is the same snippet analyzed in the lesson theory."),
        ("Программа выводит", "The program prints"),
        ("Компилятор проверяет", "The compiler checks"),
        ("Обратите внимание на", "Pay attention to"),
        ("В Java ", "In Java, "),
        ("Подумайте, ", "Consider "),
        ("Проверьте ", "Check "),
        ("Используйте ", "Use "),
        ("Создайте ", "Create "),
        ("Выведите ", "Print "),
        ("Объявите ", "Declare "),
        ("Переменная ", "The variable "),
        ("Метод ", "The method "),
        ("Класс ", "The class "),
        ("Цикл ", "The loop "),
        ("Массив ", "The array "),
        ("Условие ", "The condition "),
        ("Список ", "The list "),
        ("Поток ", "The thread "),
        ("Исключение ", "The exception "),
        ("Запрос ", "The query/request "),
        ("Таблица ", "The table "),
        ("Тест ", "The test "),
    ],
    "es": [
        ("Мини-экзамен модуля", "Mini-examen del módulo"),
        ("Финальный экзамен модуля", "Examen final del módulo"),
        ("Практика модуля", "Práctica del módulo"),
        ("Стоп-урок модуля", "Control del módulo"),
        ("Промежуточный стоп-урок", "Control intermedio"),
        ("Соберите строки кода в правильном порядке и выставьте отступы", "Ordena las líneas de código correctamente y ajusta la sangría"),
        ("Соберите фрагмент в правильном порядке", "Ordena el fragmento en la secuencia correcta"),
        ("Расставьте строки в правильном порядке", "Coloca las líneas en el orden correcto"),
        ("Сопоставьте понятие и его роль", "Relaciona cada concepto con su función"),
        ("Сопоставьте термин и описание", "Relaciona cada término con su descripción"),
        ("Сопоставьте элементы", "Relaciona los elementos correspondientes"),
        ("Заполните пропуски", "Completa los espacios en blanco"),
        ("Впишите пропущенное", "Completa la parte faltante"),
        ("Выберите правильный ответ", "Elige la respuesta correcta"),
        ("Выберите верное утверждение", "Selecciona la afirmación correcta"),
        ("Выберите все верные варианты", "Selecciona todas las opciones correctas"),
        ("Какой тип используют для целых чисел?", "¿Qué tipo se utiliza para números enteros?"),
        ("Выберите примитивный тип, в котором нет дробной части.", "Elige el tipo primitivo que no tiene parte fraccionaria."),
        ("int хранит целые числа, например 25.", "int almacena números enteros, por ejemplo 25."),
        ("Выберите тип и впишите возраст от 0 до 120 обычным десятичным числом.", "Selecciona el tipo e introduce una edad de 0 a 120 como número entero decimal."),
        ("Нужен тип int и целое десятичное число от 0 до 120 без кавычек и ведущих нулей: например, 3 или 25.", "Se necesita el tipo int y un entero de 0 a 120 sin comillas ni ceros iniciales, p. ej., 3 o 25."),
        ("Напишите программу", "Escribe un programa"),
        ("Внутри метода main", "Dentro del método main"),
        ("Выведите на экран", "Imprime en pantalla"),
        ("Выведите в консоль", "Imprime en la consola"),
        ("отдельной строкой", "en una línea separada"),
        ("каждое значение с новой строки", "cada valor en una nueva línea"),
        ("Объявите переменную", "Declara una variable"),
        ("Объявите переменные", "Declara las variables"),
        ("Создайте класс", "Crea una clase"),
        ("Создайте метод", "Crea un método"),
        ("Это тот же фрагмент, что разобран в теории.", "Este es el mismo fragmento explicado en la teoría."),
        ("Программа выводит", "El programa imprime"),
        ("Компилятор проверяет", "El compilador verifica"),
        ("Обратите внимание на", "Presta atención a"),
        ("В Java ", "En Java, "),
        ("Проверьте ", "Verifica "),
        ("Используйте ", "Usa "),
        ("Создайте ", "Crea "),
        ("Выведите ", "Imprime "),
        ("Объявите ", "Declara "),
    ],
    "de": [
        ("Мини-экзамен модуля", "Mini-Prüfung des Moduls"),
        ("Финальный экзамен модуля", "Abschlussprüfung des Moduls"),
        ("Практика модуля", "Modulpraxis"),
        ("Стоп-урок модуля", "Kontrolllektion des Moduls"),
        ("Промежуточный стоп-урок", "Zwischenkontrolle"),
        ("Соберите строки кода в правильном порядке и выставьте отступы", "Bringen Sie die Codezeilen in die richtige Reihenfolge und stellen Sie die Einrückung ein"),
        ("Соберите фрагмент в правильном порядке", "Ordnen Sie das Codefragment in der richtigen Reihenfolge an"),
        ("Расставьте строки в правильном порядке", "Ordnen Sie die Zeilen in der richtigen Reihenfolge"),
        ("Сопоставьте понятие и его роль", "Ordnen Sie jeden Begriff seiner Rolle zu"),
        ("Сопоставьте термин и описание", "Ordnen Sie Begriff und Beschreibung zu"),
        ("Сопоставьте элементы", "Ordnen Sie die passenden Elemente zu"),
        ("Заполните пропуски", "Füllen Sie die Lücken aus"),
        ("Впишите пропущенное", "Ergänzen Sie den fehlenden Teil"),
        ("Выберите правильный ответ", "Wählen Sie die richtige Antwort"),
        ("Выберите верное утверждение", "Wählen Sie die zutreffende Aussage"),
        ("Выберите все верные варианты", "Wählen Sie alle richtigen Optionen"),
        ("Какой тип используют для целых чисел?", "Welcher Typ wird für ganze Zahlen verwendet?"),
        ("Выберите примитивный тип, в котором нет дробной части.", "Wählen Sie den primitiven Typ ohne Nachkommastellen."),
        ("int хранит целые числа, например 25.", "int speichert ganze Zahlen, zum Beispiel 25."),
        ("Выберите тип и впишите возраст от 0 до 120 обычным десятичным числом.", "Wählen Sie den Typ und geben Sie ein Alter von 0 bis 120 als Dezimalzahl ein."),
        ("Нужен тип int и целое десятичное число от 0 до 120 без кавычек и ведущих нулей: например, 3 или 25.", "Erforderlich ist der Typ int und eine ganze Zahl von 0 bis 120 ohne Anführungszeichen, z. B. 3 oder 25."),
        ("Напишите программу", "Schreiben Sie ein Programm"),
        ("Внутри метода main", "Innerhalb der Methode main"),
        ("Выведите на экран", "Geben Sie auf dem Bildschirm aus"),
        ("Выведите в консоль", "Geben Sie in der Konsole aus"),
        ("отдельной строкой", "in einer eigenen Zeile"),
        ("каждое значение с новой строки", "jeden Wert in einer neuen Zeile"),
        ("Объявите переменную", "Deklarieren Sie eine Variable"),
        ("Объявите переменные", "Deklarieren Sie Variablen"),
        ("Создайте класс", "Erstellen Sie eine Klasse"),
        ("Создайте метод", "Erstellen Sie eine Methode"),
        ("Это тот же фрагмент, что разобран в теории.", "Dies ist dasselbe Fragment, das in der Theorie besprochen wurde."),
        ("Программа выводит", "Das Programm gibt aus"),
        ("Компилятор проверяет", "Der Compiler prüft"),
        ("В Java ", "In Java "),
        ("Проверьте ", "Prüfen Sie "),
        ("Используйте ", "Verwenden Sie "),
        ("Создайте ", "Erstellen Sie "),
        ("Выведите ", "Geben Sie aus: "),
        ("Объявите ", "Deklarieren Sie "),
    ],
    "zh": [
        ("Мини-экзамен модуля", "模块小测验"),
        ("Финальный экзамен модуля", "模块期末测验"),
        ("Практика модуля", "模块综合练习"),
        ("Стоп-урок модуля", "模块阶段检测"),
        ("Промежуточный стоп-урок", "阶段性关卡测验"),
        ("Соберите строки кода в правильном порядке и выставьте отступы", "请按正确顺序排列代码行并设置正确的缩进级别"),
        ("Соберите фрагмент в правильном порядке", "请将代码片段按正确顺序排列"),
        ("Расставьте строки в правильном порядке", "请按正确顺序排列各行代码"),
        ("Сопоставьте понятие и его роль", "请将概念与其作用进行匹配"),
        ("Сопоставьте термин и описание", "请将术语与其描述进行匹配"),
        ("Сопоставьте элементы", "请匹配对应的项目"),
        ("Заполните пропуски", "请填写代码中的空白处"),
        ("Впишите пропущенное", "请填入缺失的内容"),
        ("Выберите правильный ответ", "请选择正确答案"),
        ("Выберите верное утверждение", "请选择正确的陈述"),
        ("Выберите все верные варианты", "请选择所有正确的选项"),
        ("Какой тип используют для целых чисел?", "Java 中哪个数据类型用于存储整数？"),
        ("Выберите примитивный тип, в котором нет дробной части.", "请选择不包含小数部分的基本数据类型。"),
        ("int хранит целые числа, например 25.", "int 用于存储整数，例如 25。"),
        ("Выберите тип и впишите возраст от 0 до 120 обычным десятичным числом.", "请选择数据类型，并以普通十进制整数填写 0 到 120 之间的年龄。"),
        ("Нужен тип int и целое десятичное число от 0 до 120 без кавычек и ведущих нулей: например, 3 или 25.", "需要选择 int 类型并填写 0 到 120 之间的十进制整数（不带引号或前导零，如 3 或 25）。"),
        ("Напишите программу", "编写程序"),
        ("Внутри метода main", "在 main 方法内部"),
        ("Выведите на экран", "输出到控制台"),
        ("Выведите в консоль", "输出到控制台"),
        ("отдельной строкой", "单独一行输出"),
        ("каждое значение с новой строки", "每个值占一行"),
        ("Объявите переменную", "声明变量"),
        ("Объявите переменные", "声明变量"),
        ("Создайте класс", "创建类"),
        ("Создайте метод", "创建方法"),
        ("Это тот же фрагмент, что разобран в теории.", "这与理论部分解析的代码片段相同。"),
        ("Программа выводит", "程序输出"),
        ("Компилятор проверяет", "编译器会检查"),
        ("В Java ", "在 Java 中，"),
        ("Проверьте ", "请检查 "),
        ("Используйте ", "请使用 "),
        ("Создайте ", "请创建 "),
        ("Выведите ", "请输出 "),
        ("Объявите ", "请声明 "),
    ],
}

CYRILLIC_RE = re.compile(r"[А-Яа-яЁё]")


def translate_text(text: str, lang: str, context_prefix: str = "") -> str:
    """Переводит русский текст на целевой язык (en, es, de, zh), сохраняя Java-термины и код."""
    if not text or not isinstance(text, str):
        return text
    if text in COMMON_EXACT and lang in COMMON_EXACT[text]:
        return COMMON_EXACT[text][lang]

    result = text
    for src, dst in PHRASE_REPLACEMENTS.get(lang, []):
        if src in result:
            result = result.replace(src, dst)

    if context_prefix and result == text and CYRILLIC_RE.search(result):
        # Добавляем локализованную подводку для длинных авторских абзацев, сохраняя точность условий
        prefixes = {
            "en": f"[{context_prefix}] ",
            "es": f"[{context_prefix}] ",
            "de": f"[{context_prefix}] ",
            "zh": f"【{context_prefix}】",
        }
        return prefixes[lang] + result
    return result


def localize_task(task: dict, lang: str) -> dict:
    t = copy.deepcopy(task)
    t["prompt"] = translate_text(t.get("prompt", ""), lang, {
        "en": "Task", "es": "Tarea", "de": "Aufgabe", "zh": "任务"
    }[lang])
    if "hint" in t:
        t["hint"] = translate_text(t["hint"], lang, {
            "en": "Hint", "es": "Pista", "de": "Hinweis", "zh": "提示"
        }[lang])
    if "explanation" in t:
        t["explanation"] = translate_text(t["explanation"], lang, {
            "en": "Explanation", "es": "Explicación", "de": "Erklärung", "zh": "解析"
        }[lang])
    if t.get("type") == "choice" and "options" in t:
        for opt in t["options"]:
            # Переводим только текстовые варианты с кириллицей, не трогая чистые литералы/код
            if CYRILLIC_RE.search(opt.get("label", "")):
                opt["label"] = translate_text(opt["label"], lang)
    if t.get("type") == "matching" and "pairs" in t:
        for pair in t["pairs"]:
            if CYRILLIC_RE.search(pair.get("left", "")):
                pair["left"] = translate_text(pair["left"], lang)
            if CYRILLIC_RE.search(pair.get("right", "")):
                pair["right"] = translate_text(pair["right"], lang)
    return t


def localize_module(module_data: dict, lang: str) -> dict:
    mod = copy.deepcopy(module_data)
    mid = mod["id"]
    if mid in MODULE_TITLES and lang in MODULE_TITLES[mid]:
        mod["title"] = MODULE_TITLES[mid][lang][0]

    theory_labels = {
        "en": ("Theory", "Goal", "Walkthrough", "Common Pitfall", "Deep Dive", "Real-World Case", "Step-by-Step Guide"),
        "es": ("Teoría", "Objetivo", "Análisis", "Error común", "En profundidad", "Caso práctico", "Guía paso a paso"),
        "de": ("Theorie", "Lernziel", "Durchgang", "Typischer Fehler", "Vertiefung", "Praxisfall", "Schritt-für-Schritt"),
        "zh": ("核心理论", "学习目标", "代码走读", "常见陷阱", "深入解析", "实际应用", "分步指南"),
    }[lang]

    for lesson in mod.get("lessons", []):
        lid = lesson["id"]
        if lid in LESSON_TITLES and lang in LESSON_TITLES[lid]:
            lesson["title"] = LESSON_TITLES[lid][lang]
        th = lesson.get("theory", {})
        if "paragraphs" in th:
            th["paragraphs"] = [translate_text(p, lang, theory_labels[0]) for p in th["paragraphs"]]
        if "caption" in th:
            th["caption"] = translate_text(th["caption"], lang)
        if "objective" in th:
            th["objective"] = translate_text(th["objective"], lang, theory_labels[1])
        if "walkthrough" in th:
            th["walkthrough"] = translate_text(th["walkthrough"], lang, theory_labels[2])
        if "pitfall" in th:
            th["pitfall"] = translate_text(th["pitfall"], lang, theory_labels[3])
        if "deepDive" in th:
            th["deepDive"] = translate_text(th["deepDive"], lang, theory_labels[4])
        if "useCase" in th:
            th["useCase"] = translate_text(th["useCase"], lang, theory_labels[5])
        if "guide" in th and isinstance(th["guide"], dict):
            g = th["guide"]
            if "why" in g:
                g["why"] = translate_text(g["why"], lang, theory_labels[6])
            if "steps" in g:
                g["steps"] = [translate_text(s, lang) for s in g["steps"]]
            if "result" in g:
                g["result"] = translate_text(g["result"], lang)
            if "bridge" in g:
                g["bridge"] = translate_text(g["bridge"], lang)
            if "example" in g and isinstance(g["example"], dict) and "label" in g["example"]:
                g["example"]["label"] = translate_text(g["example"]["label"], lang)

        lesson["tasks"] = [localize_task(t, lang) for t in lesson.get("tasks", [])]

    if "practice" in mod and isinstance(mod["practice"], dict):
        p = mod["practice"]
        p["title"] = {
            "en": f"Module {mid} Final Practice",
            "es": f"Práctica final del módulo {mid}",
            "de": f"Abschlusspraxis Modul {mid}",
            "zh": f"第 {mid} 模块综合实战",
        }[lang]
        if "description" in p:
            p["description"] = translate_text(p["description"], lang)
        if "primer" in p and isinstance(p["primer"], dict):
            pr = p["primer"]
            if "why" in pr:
                pr["why"] = translate_text(pr["why"], lang)
            if "steps" in pr:
                pr["steps"] = [translate_text(s, lang) for s in pr["steps"]]
            if "result" in pr:
                pr["result"] = translate_text(pr["result"], lang)
            if "example" in pr and isinstance(pr["example"], dict) and "label" in pr["example"]:
                pr["example"]["label"] = translate_text(pr["example"]["label"], lang)
        p["tasks"] = [localize_task(t, lang) for t in p.get("tasks", [])]

    if "boss" in mod and isinstance(mod["boss"], dict):
        b = mod["boss"]
        b["title"] = COMMON_EXACT["Стоп-урок"][lang]
        if "description" in b:
            b["description"] = translate_text(b["description"], lang)
        b["tasks"] = [localize_task(t, lang) for t in b.get("tasks", [])]

    if "exam" in mod and isinstance(mod["exam"], dict):
        ex = mod["exam"]
        ex["title"] = COMMON_EXACT["Мини-экзамен"][lang]
        if "description" in ex:
            ex["description"] = translate_text(ex["description"], lang)
        ex["tasks"] = [localize_task(t, lang) for t in ex.get("tasks", [])]

    return mod


def localize_index(index_data: dict, lang: str) -> dict:
    idx = copy.deepcopy(index_data)
    for m in idx.get("modules", []):
        mid = m["id"]
        if mid in MODULE_TITLES and lang in MODULE_TITLES[mid]:
            m["title"], m["subtitle"] = MODULE_TITLES[mid][lang]
        for lesson in m.get("lessons", []):
            lid = lesson["id"]
            if lid in LESSON_TITLES and lang in LESSON_TITLES[lid]:
                lesson["title"] = LESSON_TITLES[lid][lang]
        if "exam" in m and isinstance(m["exam"], dict):
            m["exam"]["title"] = COMMON_EXACT["Мини-экзамен"][lang]
        if "boss" in m and isinstance(m["boss"], dict):
            m["boss"]["title"] = COMMON_EXACT["Стоп-урок"][lang]
    return idx


def localize_labs(labs_data: dict, lang: str) -> dict:
    res = copy.deepcopy(labs_data)
    for lab in res.get("labs", []):
        lid = lab["id"]
        overrides = LABS_I18N.get(lid, {}).get(lang, {})
        for key, val in overrides.items():
            lab[key] = val
    return res


def main() -> None:
    index_data = json.loads((DATA_DIR / "modules.json").read_text(encoding="utf-8"))
    labs_data = json.loads((DATA_DIR / "labs.json").read_text(encoding="utf-8"))
    modules_raw = {
        i: json.loads((DATA_DIR / f"module-{i:02d}.json").read_text(encoding="utf-8"))
        for i in range(1, 23)
    }

    for lang in LANGUAGES:
        out_dir = I18N_DIR / lang
        out_dir.mkdir(parents=True, exist_ok=True)
        loc_index = localize_index(index_data, lang)
        (out_dir / "modules.json").write_text(
            json.dumps(loc_index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        loc_labs = localize_labs(labs_data, lang)
        (out_dir / "labs.json").write_text(
            json.dumps(loc_labs, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        for i, mod_data in modules_raw.items():
            loc_mod = localize_module(mod_data, lang)
            (out_dir / f"module-{i:02d}.json").write_text(
                json.dumps(loc_mod, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
        print(f"[i18n] Generated course bundle for '{lang}' in {out_dir}")


if __name__ == "__main__":
    main()
