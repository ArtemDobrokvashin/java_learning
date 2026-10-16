import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = providers.gradleProperty("pluginGroup").getOrElse("io.javada")
version = providers.gradleProperty("pluginVersion").getOrElse("2.0.0")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Настройка Java 25 Toolchain согласно техническому заданию
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
}

val junitConsoleVersion = "1.11.4"

// Отдельная конфигурация для JUnit Platform Console Standalone,
// которая упаковывается в плагин и используется TaskChecker для запуска JUnit-тестов
val junitRunner: Configuration by configurations.creating

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").getOrElse("2025.3"))
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.intellij.plugins.markdown")
        instrumentationTools()
    }

    // Сериализация и работа с JSON (progress.json и JSON курса)
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // JUnit 5 Platform Console Standalone для нативной проверки JUnit-задач
    junitRunner("org.junit.platform:junit-platform-console-standalone:$junitConsoleVersion")
    testImplementation(kotlin("test"))
}

intellijPlatform {
    pluginConfiguration {
        id = "io.javada.trainer"
        name = "JavaДа — Интерактивный тренажёр Java"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild").getOrElse("253")
            untilBuild = providers.gradleProperty("pluginUntilBuild").getOrElse("253.*")
        }
    }
    buildSearchableOptions = false
}

// Включение preview features Java 25 для компиляции и запуска
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("--enable-preview", "-Xlint:unchecked", "-Xlint:deprecation"))
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21) // Байткод совместимый с рантаймом платформы IDEA при использовании JDK 25 Toolchain
        freeCompilerArgs.addAll(listOf("-Xjsr305=strict"))
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview", "-Dfile.encoding=UTF-8")
    useJUnitPlatform()
}

// Копируем junit-platform-console-standalone.jar в ресурсы плагина для автономного запуска JUnit-тестов
tasks.named<ProcessResources>("processResources") {
    from(junitRunner) {
        into("lib")
        rename { "junit-platform-console-standalone.jar" }
    }
}
