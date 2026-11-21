# Team project

Queue and journals are generated from SQLite. Change them through the API.

## Goal

Easy Paper — KMP-приложение (Android, Desktop, Web/Wasm, Ktor-сервер): врач собирает из виджетов запрос под жалобу, пациент несколько дней заполняет его текстом, числами и фото (еда, упаковки лекарств), а на приёме передаёт через QR зашифрованный отчёт, который ИИ сжимает в сводку со ссылками на оригиналы и предзаполненную форму клиники. Экономный ИИ-конвейер (штрих-код/OCR без LLM, маленькая модель для простого, большая один раз на сводку, кэш, учёт токенов и цены) и режим локального ИИ-модуля в клинике (Ollama). Приложение не ставит диагнозов и не советует — только собирает и структурирует факты; ИИ пишет черновик, каждое поле подтверждает врач.
Порядок: задача 1 первая; UI-задачи 4, 5, 10 — только после утверждённого mykhailo docs/design.md (задача 3). Секреты только в env/локально; коммиты без подписи Claude; в origin не пушить.

Phases: build 1, plan 9, review 1

## Task 1: Каркас KMP (Android, Desktop, Web/Wasm, Server/Ktor) по шаблону kmp.jetbrains.com + test.py (gradlew.bat или ./gradlew по ОС, desktopTest и тесты сервера) + teamhub.json {"test": "python test.py"} + закоммитить .claude/skills (frontend-design, easy-paper-design) [JB]

Latest intent: mykhailo/MyPc-chat · 2026-11-21T14:45:25+00:00: Каркас KMP: Android/Desktop/Web/Server + test.py + teamhub.json + skills
Phase: review; build 1; submitter: mykhailo; branch: attempt/mykhailo/task-1-1.
Status: ready; assignee: mykhailo; owner: -; client: -.
Lease expires: -.
Acceptance: python test.py зелёный на Windows и Linux, Desktop и Android запускаются
Checkpoint SHA: 616b398c1dbd3b80d75aca5d95d9336cbac2ac12
Review notes: -

## Task 2: Модель виджетов и данных в commonMain: схема запроса врача (список виджетов: опросник, давление, вода, еда, лекарства, дневник, вопросы врачу), ответы, зашифрованное локальное хранилище; формат схемы зафиксировать первым коммитом [JB][IMC]

Latest intent: vlad/BelarusLord-chat · 2026-11-21T14:46:59+00:00: Фиксирую формат схемы запроса врача (план до каркаса), код — после #1 в main
Phase: build; build 0; submitter: -; branch: attempt/vlad/task-2-1.
Status: running; assignee: vlad; owner: vlad; client: BelarusLord-chat.
Lease expires: 2026-11-21T15:15:02+00:00.
Acceptance: тесты commonTest
Checkpoint SHA: f09b5613854ebd5d0e07cba296f44b0b27dda4d2
Review notes: -

## Task 3: Дизайн-система: docs/design.md по навыкам frontend-design + easy-paper-design (палитра light/dark, шрифты, шкала, формы, принципы, ASCII-вайрфреймы «Сегодня» пациента и сводки врача) → показать mykhailo → EasyPaperTheme в commonMain [JB]

Latest intent: -
Phase: plan; build 0; submitter: -; branch: -.
Status: ready; assignee: mykhailo; owner: -; client: -.
Lease expires: -.
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

Latest intent: -
Phase: plan; build 0; submitter: -; branch: -.
Status: ready; assignee: pavlo; owner: -; client: -.
Lease expires: -.
Acceptance: пациент открывает и видит ровно эти виджеты
Checkpoint SHA: -
Review notes: -

## Task 6: Скан лекарств: штрих-код/DataMatrix + OCR → локальная база-подмножество → подтверждение [AXA]

Latest intent: -
Phase: plan; build 0; submitter: -; branch: -.
Status: ready; assignee: vlad; owner: -; client: -.
Lease expires: -.
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

Latest intent: -
Phase: plan; build 0; submitter: -; branch: -.
Status: ready; assignee: vlad; owner: -; client: -.
Lease expires: -.
Acceptance: без ключа не расшифровать
Checkpoint SHA: -
Review notes: -

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

