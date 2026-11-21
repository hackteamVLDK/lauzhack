# Plans

Each AI publishes its plan here before it changes code.

## Task 1: Каркас KMP (Android, Desktop, Web/Wasm, Server/Ktor) по шаблону kmp.jetbrains.com + test.py (gradlew.bat или ./gradlew по ОС, desktopTest и тесты сервера) + teamhub.json {"test": "python test.py"} + закоммитить .claude/skills (frontend-design, easy-paper-design) [JB] (accepted)

Попытка 2 по ревью vlad: test.py требует Android SDK только с --all; без SDK быстрые задачи (:core:jvmTest :app:shared:jvmTest :server:test :app:desktopApp:compileKotlin) идут без local.properties, с предупреждением. Файл: test.py. Ветка attempt/mykhailo/task-1-2 от main 7b0491c. Проверка: python test.py на Windows; в WSL Ubuntu без ANDROID_HOME и без local.properties (как у vlad); --all без SDK -> понятная ошибка, код 2.

## Task 2: Модель виджетов и данных в commonMain: схема запроса врача (список виджетов: опросник, давление, вода, еда, лекарства, дневник, вопросы врачу), ответы, зашифрованное локальное хранилище; формат схемы зафиксировать первым коммитом [JB][IMC] (accepted)

ПОПРАВКА к плану: модуль core (KMP без UI, по плану #1), пакет org.easypaper.model. Шаг 1 (сейчас): core/src/commonMain/kotlin/org/easypaper/model/Schema.kt + commonTest/SchemaTest.kt — формат схемы v1 (CareRequest, WidgetSpec x7, Entry/EntryPayload x7, PatientRecord, EasyPaperJson; время = epoch millis Long, startDate = ISO-строка, без kotlinx-datetime). Gradle-файлы #1 НЕ трогаю: core нужен плагин kotlin serialization + kotlinx-serialization-json в commonMain. Шаг 2 (после каркаса и согласования dev.whyoleg.cryptography с mykhailo): storage/ RecordStore + EncryptedRecordStore AES-256-GCM + BlobStore/KeyProvider actual. Тесты шага 1 прогнаны во временном JVM-проекте вне репо: 5/5 зелёные.

## Task 9: Отчёт и передача: сводка + FHIR-подобный JSON, шифрование, QR с ключом; формат отчёта зафиксировать первым коммитом [IMC][JB] (accepted)

ЧТО (core, пакет org.easypaper.report, без новых зависимостей). ФОРМАТ (первый коммит, сразу deliver — нужен #10 Павла и #7/#8 Михаила): Report{schemaVersion=1, id, createdAt(epoch ms), record: PatientRecord, summary: String? (заполняет ИИ #7, со ссылками entryId), fhir: JsonObject (FHIR-подобный Bundle)}. FHIR: PatientRecord.toFhirBundle() — Bundle(collection): давление -> Observation LOINC 85354-9 (8480-6 sys, 8462-4 dia, 8867-4 pulse), вода -> Observation, лекарства -> MedicationStatement (код GTIN, source, confirmed), опросник -> QuestionnaireResponse, дневник/еда/вопросы -> Observation с valueString; у каждого ресурса id = entryId (ссылки сводки на оригиналы). ПЕРЕДАЧА: sealReport(report) -> SealedReport(bytes, key) — AES-256-GCM, новый случайный ключ на каждый отчёт, reportId как associated data; QR-строка 'ep1:<reportId>:<base64url key>' (+ опц. адрес загрузки, когда будет #8); openReport(bytes, qr) -> Report. Шифрованные байты уходят через сервер #8 или файл, ключ — только в QR. ТЕСТЫ commonTest: round-trip Report JSON; без ключа/чужой ключ/порча -> ошибка (критерий); QR парсится туда-обратно, мусор -> ошибка; FHIR-бандл содержит нужные LOINC и id записей. РИСКИ: размер QR — в нём только ключ+id, не данные; сам QR-рисунок (картинка) — в UI #10.

