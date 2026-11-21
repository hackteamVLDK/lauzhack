# Plans

Each AI publishes its plan here before it changes code.

## Task 1: Каркас KMP (Android, Desktop, Web/Wasm, Server/Ktor) по шаблону kmp.jetbrains.com + test.py (gradlew.bat или ./gradlew по ОС, desktopTest и тесты сервера) + teamhub.json {"test": "python test.py"} + закоммитить .claude/skills (frontend-design, easy-paper-design) [JB] (review)

Что: каркас из официального генератора kmp.jetbrains.com (Android + Desktop + Web Compose + Server Ktor, с тестами; Kotlin 2.4.21, CMP 1.12.2, AGP 9.1.1, Ktor 3.6.1, Gradle 9.5.1, пакет org.easypaper).
Файлы (корень репо): settings/build.gradle.kts, gradle/ (libs.versions.toml, wrapper, daemon-jvm Azul 21), app/androidApp, app/desktopApp, app/webApp, app/shared (KMP: android, jvm, js, wasmJs — общий UI и логика), core (KMP без UI — модель и логика), server (Ktor), README.md, .gitignore, .gitattributes (gradlew LF, *.bat CRLF), test.py, teamhub.json {"test": "python test.py"}, .claude/skills/{frontend-design,easy-paper-design}. GOAL.md и .claude/settings.local.json не коммитятся.
Тесты: test.py выбирает gradlew.bat или sh gradlew по ОС, ищет Android SDK (ANDROID_HOME, ANDROID_SDK_ROOT, local.properties, стандартные пути) и пишет local.properties, запускает :core:jvmTest :app:shared:jvmTest :server:test (desktop = jvm-таргет в этом шаблоне); флаг --all добавляет сборку Android и Wasm.
Проверка: test.py на Windows и в WSL Ubuntu; Desktop :app:desktopApp:run; Android installDebug на эмуляторе + скриншот; Wasm-сборка.
Риски: первая загрузка Gradle и зависимостей долгая; в PATH JDK 25 (daemon берёт 21 по toolchain); ревьюеру на Linux нужен Android SDK.

## Task 2: Модель виджетов и данных в commonMain: схема запроса врача (список виджетов: опросник, давление, вода, еда, лекарства, дневник, вопросы врачу), ответы, зашифрованное локальное хранилище; формат схемы зафиксировать первым коммитом [JB][IMC] (build)

ПОПРАВКА к плану: модуль core (KMP без UI, по плану #1), пакет org.easypaper.model. Шаг 1 (сейчас): core/src/commonMain/kotlin/org/easypaper/model/Schema.kt + commonTest/SchemaTest.kt — формат схемы v1 (CareRequest, WidgetSpec x7, Entry/EntryPayload x7, PatientRecord, EasyPaperJson; время = epoch millis Long, startDate = ISO-строка, без kotlinx-datetime). Gradle-файлы #1 НЕ трогаю: core нужен плагин kotlin serialization + kotlinx-serialization-json в commonMain. Шаг 2 (после каркаса и согласования dev.whyoleg.cryptography с mykhailo): storage/ RecordStore + EncryptedRecordStore AES-256-GCM + BlobStore/KeyProvider actual. Тесты шага 1 прогнаны во временном JVM-проекте вне репо: 5/5 зелёные.

