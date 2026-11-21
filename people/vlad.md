# vlad

- Event 18 · 2026-11-21T14:46:59+00:00 · vlad/BelarusLord-chat · start: Фиксирую формат схемы запроса врача (план до каркаса), код — после #1 в main
- Event 19 · 2026-11-21T14:46:59+00:00 · vlad/BelarusLord-chat · plan: ЧТО. Модуль shared, commonMain, пакет <корень каркаса>.model (kotlinx.serialization, JSON, поле schemaVersion=1).
ФОРМАТ СХЕМЫ (первый коммит, сразу deliver — нужен #4,#5,#10,#9):
- CareRequest{schemaVersion, id, createdAt, doctorName, complaint, startDate, days, locale, widgets: List<WidgetSpec>}
- WidgetSpec — sealed, дискриминатор "type": questionnaire{questions: List<Question(id,text,kind=yesno|scale|choice|text,options)>}, blood_pressure{timesPerDay}, water{targetMl?}, food{photo:Boolean}, medications{scan:Boolean}, diary{}, doctor_questions{}. У каждого id, title, required.
- Ответы: Entry{id, widgetId, at(Instant), payload: EntryPayload} — sealed: Answers(map qid->value), BloodPressure(sys,dia,pulse?), Water(ml), Food(text?, photoRef?), Medication(name, code?, dose?, source=scan|manual, confirmed), DiaryNote(text, photoRef?), DoctorQuestion(text).
- PatientRecord{request, entries} — то, что идёт в отчёт #9. Фото — только photoRef (id файла), байты отдельно.
ХРАНИЛИЩЕ. interface RecordStore (save/load/list) в commonMain; EncryptedRecordStore = AES-256-GCM поверх interface BlobStore (expect/actual: файл на JVM/Android, localStorage на wasmJs; InMemory для тестов). Ключ — из платформенного keystore (actual), в commonMain только интерфейс KeyProvider. Крипто: dev.whyoleg.cryptography (JVM/Android/wasm) — если не встанет в каркас, спрошу.
ФАЙЛЫ: только shared/src/commonMain/.../model/**, .../storage/**, commonTest, platform actual для BlobStore/KeyProvider; gradle — одна зависимость в shared (согласую с mykhailo).
ТЕСТЫ (commonTest): round-trip JSON всех WidgetSpec/EntryPayload; неизвестный type -> ошибка, лишние поля игнор; schemaVersion; шифрованное хранилище: save/load, другой ключ -> не расшифровать, порча байт -> ошибка. python test.py.
РИСКИ: wasm-крипто асинхронное (suspend API везде); формат меняю только с schemaVersion+1 и сообщением Павлу.
- Event 21 · 2026-11-21T14:47:16+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 23 · 2026-11-21T14:47:46+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 25 · 2026-11-21T14:48:16+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 27 · 2026-11-21T14:48:46+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 29 · 2026-11-21T14:49:17+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 32 · 2026-11-21T14:49:47+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 34 · 2026-11-21T14:50:17+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 36 · 2026-11-21T14:50:48+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 38 · 2026-11-21T14:51:18+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 40 · 2026-11-21T14:51:48+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 42 · 2026-11-21T14:52:19+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 44 · 2026-11-21T14:52:49+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 46 · 2026-11-21T14:53:19+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 48 · 2026-11-21T14:53:50+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 50 · 2026-11-21T14:54:21+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 52 · 2026-11-21T14:54:52+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 54 · 2026-11-21T14:55:22+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 56 · 2026-11-21T14:55:53+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 58 · 2026-11-21T14:56:23+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 60 · 2026-11-21T14:56:53+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 62 · 2026-11-21T14:57:23+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 64 · 2026-11-21T14:57:54+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 67 · 2026-11-21T14:58:24+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 69 · 2026-11-21T14:58:54+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 71 · 2026-11-21T14:59:24+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 73 · 2026-11-21T14:59:54+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 75 · 2026-11-21T15:00:25+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 77 · 2026-11-21T15:00:55+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 79 · 2026-11-21T15:01:25+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 81 · 2026-11-21T15:01:55+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 83 · 2026-11-21T15:02:25+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 85 · 2026-11-21T15:02:56+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 87 · 2026-11-21T15:03:26+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 89 · 2026-11-21T15:03:56+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 91 · 2026-11-21T15:04:26+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 93 · 2026-11-21T15:04:57+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 95 · 2026-11-21T15:05:27+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 97 · 2026-11-21T15:05:58+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 99 · 2026-11-21T15:06:28+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 100 · 2026-11-21T15:06:29+00:00 · vlad/BelarusLord-chat · plan: ПОПРАВКА к плану: модуль core (KMP без UI, по плану #1), пакет org.easypaper.model. Шаг 1 (сейчас): core/src/commonMain/kotlin/org/easypaper/model/Schema.kt + commonTest/SchemaTest.kt — формат схемы v1 (CareRequest, WidgetSpec x7, Entry/EntryPayload x7, PatientRecord, EasyPaperJson; время = epoch millis Long, startDate = ISO-строка, без kotlinx-datetime). Gradle-файлы #1 НЕ трогаю: core нужен плагин kotlin serialization + kotlinx-serialization-json в commonMain. Шаг 2 (после каркаса и согласования dev.whyoleg.cryptography с mykhailo): storage/ RecordStore + EncryptedRecordStore AES-256-GCM + BlobStore/KeyProvider actual. Тесты шага 1 прогнаны во временном JVM-проекте вне репо: 5/5 зелёные.
- Event 101 · 2026-11-21T15:06:32+00:00 · vlad/git · push: vlad pushed attempt/vlad/task-2-1: 1 commit(s), 2 file(s)
- Event 102 · 2026-11-21T15:06:40+00:00 · vlad/BelarusLord-chat · checkpoint: WIP: шаг 1 готов — схема v1 (core/.../model/Schema.kt) + 5 тестов commonTest, зелёные во временном JVM-проекте. Ждёт: каркас #1 в main (плагин serialization в core) и согласие на dev.whyoleg.cryptography для шага 2 (шифрованное хранилище). Павел может уже брать формат из ветки. · SHA `4871df134c5d272ae4381581e22a0eae06b48dbe`
- Event 104 · 2026-11-21T15:06:58+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
- Event 106 · 2026-11-21T15:07:29+00:00 · vlad/BelarusLord-chat · heartbeat: Lease renewed
