# Team project

Queue and journals are generated from SQLite. Change them through the API.

## Goal

Easy Paper — KMP-приложение (Android, Desktop, Web/Wasm, Ktor-сервер): врач собирает из виджетов запрос под жалобу, пациент несколько дней заполняет его текстом, числами и фото (еда, упаковки лекарств), а на приёме передаёт через QR зашифрованный отчёт, который ИИ сжимает в сводку со ссылками на оригиналы и предзаполненную форму клиники. Экономный ИИ-конвейер (штрих-код/OCR без LLM, маленькая модель для простого, большая один раз на сводку, кэш, учёт токенов и цены) и режим локального ИИ-модуля в клинике (Ollama). Приложение не ставит диагнозов и не советует — только собирает и структурирует факты; ИИ пишет черновик, каждое поле подтверждает врач.
Порядок: задача 1 первая; UI-задачи 4, 5, 10 — только после утверждённого mykhailo docs/design.md (задача 3). Секреты только в env/локально; коммиты без подписи Claude; в origin не пушить.

Phases: accepted 3, build 3, plan 5

## Task 1: Каркас KMP (Android, Desktop, Web/Wasm, Server/Ktor) по шаблону kmp.jetbrains.com + test.py (gradlew.bat или ./gradlew по ОС, desktopTest и тесты сервера) + teamhub.json {"test": "python test.py"} + закоммитить .claude/skills (frontend-design, easy-paper-design) [JB]

Latest intent: vlad/BelarusLord-chat · 2026-11-21T15:32:13+00:00: review
Phase: accepted; build 2; submitter: mykhailo; branch: attempt/mykhailo/task-1-2.
Status: done; assignee: mykhailo; owner: vlad; client: BelarusLord-chat.
Lease expires: -.
Acceptance: python test.py зелёный на Windows и Linux, Desktop и Android запускаются
Checkpoint SHA: 81198aa71f84701fbcb36f08746d2689edfeae44
Review notes: Попытка 2, sha 81198aa, Linux без Android SDK. Diff 7b0491c..81198aa: только test.py (SDK обязателен лишь с --all) и README — исправляет моё замечание из попытки 1. python test.py -> exit 0: PASSED, 18 тестов (:core:jvmTest :app:shared:jvmTest :server:test + компиляция desktop), local.properties не создаётся. python test.py --all -> exit 2 с понятным 'Android SDK not found: set ANDROID_HOME or add sdk.dir=<path> to local.properties'. Desktop-окно стартовало в попытке 1 (код UI с тех пор не менял

## Task 2: Модель виджетов и данных в commonMain: схема запроса врача (список виджетов: опросник, давление, вода, еда, лекарства, дневник, вопросы врачу), ответы, зашифрованное локальное хранилище; формат схемы зафиксировать первым коммитом [JB][IMC]

Latest intent: mykhailo/MyPc-chat · 2026-11-21T15:25:27+00:00: review
Phase: accepted; build 1; submitter: vlad; branch: attempt/vlad/task-2-2.
Status: done; assignee: vlad; owner: mykhailo; client: MyPc-chat.
Lease expires: -.
Acceptance: тесты commonTest
Checkpoint SHA: 4610fac45908b23aa747ce5838f777947e3375d3
Review notes: PASS на 4610fac: python test.py зелёный (Windows), 11 тестов core/commonTest (схема v1 round-trip, неизвестный виджет, AES-GCM: чужой ключ, порча байт, перенос блоба под другой id). Код Schema.kt/RecordStore.kt совпадает с тем, что уже в main (36ac134). Сборка: в main остаются kotlinx-serialization-json 1.13.0 (свежая, вместо 1.9.0) и api(...) вместо implementation — EasyPaperJson публичный и нужен app/shared и server; ветку вливаю в main стратегией ours для build-файлов. Дальше: платформенные B

## Task 3: Дизайн-система: docs/design.md по навыкам frontend-design + easy-paper-design (палитра light/dark, шрифты, шкала, формы, принципы, ASCII-вайрфреймы «Сегодня» пациента и сводки врача) → показать mykhailo → EasyPaperTheme в commonMain [JB]

Latest intent: mykhailo/MyPc-chat · 2026-11-21T15:34:33+00:00: docs/design.md, затем EasyPaperTheme
Phase: build; build 0; submitter: -; branch: -.
Status: running; assignee: mykhailo; owner: mykhailo; client: MyPc-chat.
Lease expires: 2026-11-21T15:39:00+00:00.
Acceptance: mykhailo утвердил docs/design.md, тема работает в light/dark
Checkpoint SHA: -
Review notes: -

## Task 4: UI пациента (Compose): все виджеты, фото по желанию, дневник, вопросы врачу, EN/DE/UK [JB][IMC][AXA] [старт после утверждённого docs/design.md]

Latest intent: -
Phase: plan; build 0; submitter: -; branch: -.
Status: ready; assignee: pavlo; owner: -; client: -.
Lease expires: -.
Acceptance: работает на Android и Desktop, только токены темы
Checkpoint SHA: -
Review notes: -

## Task 5: Конструктор запроса врача (Web + Desktop): виджеты + срок → приглашение (ссылка/QR) [JB][IMC] [старт после утверждённого docs/design.md]

Latest intent: pavlo/Paul-chat · 2026-11-21T15:34:05+00:00: non-UI part first: CareRequest <-> invite link/QR string in core; UI waits for design.md
Phase: build; build 0; submitter: -; branch: -.
Status: running; assignee: pavlo; owner: pavlo; client: Paul-chat.
Lease expires: 2026-11-21T15:38:30+00:00.
Acceptance: пациент открывает и видит ровно эти виджеты
Checkpoint SHA: -
Review notes: -

## Task 6: Скан лекарств: штрих-код/DataMatrix + OCR → локальная база-подмножество → подтверждение [AXA]

Latest intent: vlad/BelarusLord-chat · 2026-11-21T15:33:18+00:00: core: разбор штрих-кода/DataMatrix, локальная база, поиск по OCR-тексту
Phase: build; build 0; submitter: -; branch: -.
Status: running; assignee: vlad; owner: vlad; client: BelarusLord-chat.
Lease expires: 2026-11-21T15:38:46+00:00.
Acceptance: 10+ препаратов распознаются
Checkpoint SHA: -
Review notes: -

## Task 7: ИИ-конвейер в commonMain: общий интерфейс, провайдеры OpenRouter и Ollama, сжатие фото, маленькая модель для простого, большая один раз для сводки со ссылками на оригиналы, кэш, учёт токенов и цены [AXA][JB]

Latest intent: -
Phase: plan; build 0; submitter: -; branch: -.
Status: ready; assignee: mykhailo; owner: -; client: -.
Lease expires: -.
Acceptance: тесты на моках
Checkpoint SHA: -
Review notes: -

## Task 8: Ktor-сервер в двух режимах: облачный (прокси, ключ только на сервере, лимит бюджета, слепой релей отчётов) и «клиника» (Ollama, загрузка документов, предзаполнение формы) [JB][AXA]

Latest intent: -
Phase: plan; build 0; submitter: -; branch: -.
Status: ready; assignee: mykhailo; owner: -; client: -.
Lease expires: -.
Acceptance: тесты сервера
Checkpoint SHA: -
Review notes: -

## Task 9: Отчёт и передача: сводка + FHIR-подобный JSON, шифрование, QR с ключом; формат отчёта зафиксировать первым коммитом [IMC][JB]

Latest intent: mykhailo/MyPc-chat · 2026-11-21T15:25:28+00:00: review
Phase: accepted; build 1; submitter: vlad; branch: attempt/vlad/task-9-1.
Status: done; assignee: vlad; owner: mykhailo; client: MyPc-chat.
Lease expires: -.
Acceptance: без ключа не расшифровать
Checkpoint SHA: ad5879b230ebcc719aa0b632c2b15ca507195bab
Review notes: PASS на ad5879b: python test.py зелёный (Windows), 18 тестов, из них 4 ReportTest: JSON round-trip, открывается только своим QR (чужой ключ и порча байт -> ошибка, нет открытого текста в байтах), мусорные QR отклоняются, FHIR id/типы/LOINC/draft. Критерий «без ключа не расшифровать» выполнен: новый AES-256-GCM ключ на отчёт, reportId как AAD, ключ только в QR ep1:<id>:<base64url>. Вынос aesGcm в storage — хорошо, RecordStore и Transfer используют один код. Замечаний, блокирующих слияние, нет; вл

## Task 10: Экран врача на приёме: сводка → оригиналы; предзаполненная форма с подтверждением каждого поля [IMC][JB] [старт после утверждённого docs/design.md]

Latest intent: -
Phase: plan; build 0; submitter: -; branch: -.
Status: ready; assignee: pavlo; owner: -; client: -.
Lease expires: -.
Acceptance: демо-отчёт открывается, без подтверждения ничего не сохраняется
Checkpoint SHA: -
Review notes: -

## Task 11: Демо и презентация: облако против локального модуля по цене, общий/платформенный код, скриншоты агентов в Air [JB][IMC][AXA]

Latest intent: -
Phase: plan; build 0; submitter: -; branch: -.
Status: ready; assignee: mykhailo; owner: -; client: -.
Lease expires: -.
Acceptance: сценарий на 3 минуты
Checkpoint SHA: -
Review notes: -

