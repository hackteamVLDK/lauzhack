# Plans

Each AI publishes its plan here before it changes code.

## Task 1: Каркас KMP (Android, Desktop, Web/Wasm, Server/Ktor) по шаблону kmp.jetbrains.com + test.py (gradlew.bat или ./gradlew по ОС, desktopTest и тесты сервера) + teamhub.json {"test": "python test.py"} + закоммитить .claude/skills (frontend-design, easy-paper-design) [JB] (build)

Что: каркас из официального генератора kmp.jetbrains.com (Android + Desktop + Web Compose + Server Ktor, с тестами; Kotlin 2.4.21, CMP 1.12.2, AGP 9.1.1, Ktor 3.6.1, Gradle 9.5.1, пакет org.easypaper).
Файлы (корень репо): settings/build.gradle.kts, gradle/ (libs.versions.toml, wrapper, daemon-jvm Azul 21), app/androidApp, app/desktopApp, app/webApp, app/shared (KMP: android, jvm, js, wasmJs — общий UI и логика), core (KMP без UI — модель и логика), server (Ktor), README.md, .gitignore, .gitattributes (gradlew LF, *.bat CRLF), test.py, teamhub.json {"test": "python test.py"}, .claude/skills/{frontend-design,easy-paper-design}. GOAL.md и .claude/settings.local.json не коммитятся.
Тесты: test.py выбирает gradlew.bat или sh gradlew по ОС, ищет Android SDK (ANDROID_HOME, ANDROID_SDK_ROOT, local.properties, стандартные пути) и пишет local.properties, запускает :core:jvmTest :app:shared:jvmTest :server:test (desktop = jvm-таргет в этом шаблоне); флаг --all добавляет сборку Android и Wasm.
Проверка: test.py на Windows и в WSL Ubuntu; Desktop :app:desktopApp:run; Android installDebug на эмуляторе + скриншот; Wasm-сборка.
Риски: первая загрузка Gradle и зависимостей долгая; в PATH JDK 25 (daemon берёт 21 по toolchain); ревьюеру на Linux нужен Android SDK.

## Task 2: Модель виджетов и данных в commonMain: схема запроса врача (список виджетов: опросник, давление, вода, еда, лекарства, дневник, вопросы врачу), ответы, зашифрованное локальное хранилище; формат схемы зафиксировать первым коммитом [JB][IMC] (build)

ЧТО. Модуль shared, commonMain, пакет <корень каркаса>.model (kotlinx.serialization, JSON, поле schemaVersion=1).
ФОРМАТ СХЕМЫ (первый коммит, сразу deliver — нужен #4,#5,#10,#9):
- CareRequest{schemaVersion, id, createdAt, doctorName, complaint, startDate, days, locale, widgets: List<WidgetSpec>}
- WidgetSpec — sealed, дискриминатор "type": questionnaire{questions: List<Question(id,text,kind=yesno|scale|choice|text,options)>}, blood_pressure{timesPerDay}, water{targetMl?}, food{photo:Boolean}, medications{scan:Boolean}, diary{}, doctor_questions{}. У каждого id, title, required.
- Ответы: Entry{id, widgetId, at(Instant), payload: EntryPayload} — sealed: Answers(map qid->value), BloodPressure(sys,dia,pulse?), Water(ml), Food(text?, photoRef?), Medication(name, code?, dose?, source=scan|manual, confirmed), DiaryNote(text, photoRef?), DoctorQuestion(text).
- PatientRecord{request, entries} — то, что идёт в отчёт #9. Фото — только photoRef (id файла), байты отдельно.
ХРАНИЛИЩЕ. interface RecordStore (save/load/list) в commonMain; EncryptedRecordStore = AES-256-GCM поверх interface BlobStore (expect/actual: файл на JVM/Android, localStorage на wasmJs; InMemory для тестов). Ключ — из платформенного keystore (actual), в commonMain только интерфейс KeyProvider. Крипто: dev.whyoleg.cryptography (JVM/Android/wasm) — если не встанет в каркас, спрошу.
ФАЙЛЫ: только shared/src/commonMain/.../model/**, .../storage/**, commonTest, platform actual для BlobStore/KeyProvider; gradle — одна зависимость в shared (согласую с mykhailo).
ТЕСТЫ (commonTest): round-trip JSON всех WidgetSpec/EntryPayload; неизвестный type -> ошибка, лишние поля игнор; schemaVersion; шифрованное хранилище: save/load, другой ключ -> не расшифровать, порча байт -> ошибка. python test.py.
РИСКИ: wasm-крипто асинхронное (suspend API везде); формат меняю только с schemaVersion+1 и сообщением Павлу.

