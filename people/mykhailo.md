# mykhailo

- Event 1 · 2026-11-21T14:45:12+00:00 · mykhailo/MyPc-chat · goal: Easy Paper — KMP-приложение (Android, Desktop, Web/Wasm, Ktor-сервер): врач собирает из виджетов запрос под жалобу, пациент несколько дней заполняет его текстом, числами и фото (еда, упаковки лекарств), а на приёме передаёт через QR зашифрованный отчёт, который ИИ сжимает в сводку со ссылками на оригиналы и предзаполненную форму клиники. Экономный ИИ-конвейер (штрих-код/OCR без LLM, маленькая модель для простого, большая один раз на сводку, кэш, учёт токенов и цены) и режим локального ИИ-модуля в клинике (Ollama). Приложение не ставит диагнозов и не советует — только собирает и структурирует факты; ИИ пишет черновик, каждое поле подтверждает врач.
Порядок: задача 1 первая; UI-задачи 4, 5, 10 — только после утверждённого mykhailo docs/design.md (задача 3). Секреты только в env/локально; коммиты без подписи Claude; в origin не пушить.
- Event 2 · 2026-11-21T14:45:12+00:00 · mykhailo/MyPc-chat · created: Каркас KMP (Android, Desktop, Web/Wasm, Server/Ktor) по шаблону kmp.jetbrains.com + test.py (gradlew.bat или ./gradlew по ОС, desktopTest и тесты сервера) + teamhub.json {"test": "python test.py"} + закоммитить .claude/skills (frontend-design, easy-paper-design) [JB]
- Event 3 · 2026-11-21T14:45:13+00:00 · mykhailo/MyPc-chat · created: Модель виджетов и данных в commonMain: схема запроса врача (список виджетов: опросник, давление, вода, еда, лекарства, дневник, вопросы врачу), ответы, зашифрованное локальное хранилище; формат схемы зафиксировать первым коммитом [JB][IMC]
- Event 4 · 2026-11-21T14:45:13+00:00 · mykhailo/MyPc-chat · created: Дизайн-система: docs/design.md по навыкам frontend-design + easy-paper-design (палитра light/dark, шрифты, шкала, формы, принципы, ASCII-вайрфреймы «Сегодня» пациента и сводки врача) → показать mykhailo → EasyPaperTheme в commonMain [JB]
- Event 5 · 2026-11-21T14:45:13+00:00 · mykhailo/MyPc-chat · created: UI пациента (Compose): все виджеты, фото по желанию, дневник, вопросы врачу, EN/DE/UK [JB][IMC][AXA] [старт после утверждённого docs/design.md]
- Event 6 · 2026-11-21T14:45:13+00:00 · mykhailo/MyPc-chat · created: Конструктор запроса врача (Web + Desktop): виджеты + срок → приглашение (ссылка/QR) [JB][IMC] [старт после утверждённого docs/design.md]
- Event 7 · 2026-11-21T14:45:13+00:00 · mykhailo/MyPc-chat · created: Скан лекарств: штрих-код/DataMatrix + OCR → локальная база-подмножество → подтверждение [AXA]
- Event 8 · 2026-11-21T14:45:14+00:00 · mykhailo/MyPc-chat · created: ИИ-конвейер в commonMain: общий интерфейс, провайдеры OpenRouter и Ollama, сжатие фото, маленькая модель для простого, большая один раз для сводки со ссылками на оригиналы, кэш, учёт токенов и цены [AXA][JB]
- Event 9 · 2026-11-21T14:45:14+00:00 · mykhailo/MyPc-chat · created: Ktor-сервер в двух режимах: облачный (прокси, ключ только на сервере, лимит бюджета, слепой релей отчётов) и «клиника» (Ollama, загрузка документов, предзаполнение формы) [JB][AXA]
- Event 10 · 2026-11-21T14:45:14+00:00 · mykhailo/MyPc-chat · created: Отчёт и передача: сводка + FHIR-подобный JSON, шифрование, QR с ключом; формат отчёта зафиксировать первым коммитом [IMC][JB]
- Event 11 · 2026-11-21T14:45:14+00:00 · mykhailo/MyPc-chat · created: Экран врача на приёме: сводка → оригиналы; предзаполненная форма с подтверждением каждого поля [IMC][JB] [старт после утверждённого docs/design.md]
- Event 12 · 2026-11-21T14:45:14+00:00 · mykhailo/MyPc-chat · created: Демо и презентация: облако против локального модуля по цене, общий/платформенный код, скриншоты агентов в Air [JB][IMC][AXA]
- Event 13 · 2026-11-21T14:45:15+00:00 · mykhailo/MyPc-chat · base: Tasks start from branch main
- Event 14 · 2026-11-21T14:45:25+00:00 · mykhailo/MyPc-chat · start: Каркас KMP: Android/Desktop/Web/Server + test.py + teamhub.json + skills
- Event 15 · 2026-11-21T14:45:32+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 16 · 2026-11-21T14:46:02+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 17 · 2026-11-21T14:46:32+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 20 · 2026-11-21T14:47:03+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 22 · 2026-11-21T14:47:33+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 24 · 2026-11-21T14:48:03+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 26 · 2026-11-21T14:48:34+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 28 · 2026-11-21T14:49:04+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 30 · 2026-11-21T14:49:30+00:00 · mykhailo/MyPc-chat · plan: Что: каркас из официального генератора kmp.jetbrains.com (Android + Desktop + Web Compose + Server Ktor, с тестами; Kotlin 2.4.21, CMP 1.12.2, AGP 9.1.1, Ktor 3.6.1, Gradle 9.5.1, пакет org.easypaper).
Файлы (корень репо): settings/build.gradle.kts, gradle/ (libs.versions.toml, wrapper, daemon-jvm Azul 21), app/androidApp, app/desktopApp, app/webApp, app/shared (KMP: android, jvm, js, wasmJs — общий UI и логика), core (KMP без UI — модель и логика), server (Ktor), README.md, .gitignore, .gitattributes (gradlew LF, *.bat CRLF), test.py, teamhub.json {"test": "python test.py"}, .claude/skills/{frontend-design,easy-paper-design}. GOAL.md и .claude/settings.local.json не коммитятся.
Тесты: test.py выбирает gradlew.bat или sh gradlew по ОС, ищет Android SDK (ANDROID_HOME, ANDROID_SDK_ROOT, local.properties, стандартные пути) и пишет local.properties, запускает :core:jvmTest :app:shared:jvmTest :server:test (desktop = jvm-таргет в этом шаблоне); флаг --all добавляет сборку Android и Wasm.
Проверка: test.py на Windows и в WSL Ubuntu; Desktop :app:desktopApp:run; Android installDebug на эмуляторе + скриншот; Wasm-сборка.
Риски: первая загрузка Gradle и зависимостей долгая; в PATH JDK 25 (daemon берёт 21 по toolchain); ревьюеру на Linux нужен Android SDK.
- Event 31 · 2026-11-21T14:49:34+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 33 · 2026-11-21T14:50:05+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 35 · 2026-11-21T14:50:35+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 37 · 2026-11-21T14:51:05+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 39 · 2026-11-21T14:51:36+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 41 · 2026-11-21T14:52:06+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 43 · 2026-11-21T14:52:36+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 45 · 2026-11-21T14:53:07+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
- Event 47 · 2026-11-21T14:53:37+00:00 · mykhailo/MyPc-chat · heartbeat: Lease renewed
