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
