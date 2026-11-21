# Changes

Every push to the hub: who, which branch, which files. Newest first.

## Push 185 · 2026-11-21T15:29:47+00:00 · mykhailo · attempt/mykhailo/task-1-2

mykhailo pushed attempt/mykhailo/task-1-2: 1 commit(s), 2 file(s)
`0000000000` → `81198aa71f`

- 81198aa Mykhailo Titov: test.py: require the Android SDK only for --all (review of task 1 by vlad)

    M README.md
    M test.py

## Push 179 · 2026-11-21T15:28:03+00:00 · mykhailo · main

mykhailo pushed main: 6 commit(s), 10 file(s)
`36ac13425e` → `7b0491c5ee`

- 79648c2 UladzislauHrynevich: core: data schema v1 for requests, widgets and patient entries
- 50ed1dd UladzislauHrynevich: core: encrypted record store (AES-256-GCM via cryptography-kotlin)
- 4610fac UladzislauHrynevich: core: add kotlinx-serialization, cryptography-kotlin and coroutines-test
- ad5879b UladzislauHrynevich: core: report format v1, FHIR-like bundle, sealed transfer with key in QR
- 8a5e91b Mykhailo Titov: Merge task 9: report format v1, FHIR-like bundle, sealed transfer with key in QR (vlad)
- 7b0491c Mykhailo Titov: Merge task 2 review branch (vlad): code already in main; keep main's build files (serialization-json 1.13.0, api)

    M build.gradle.kts
    M core/build.gradle.kts
    A core/src/commonMain/kotlin/org/easypaper/model/Schema.kt
    A core/src/commonMain/kotlin/org/easypaper/report/Report.kt
    A core/src/commonMain/kotlin/org/easypaper/report/Transfer.kt
    A core/src/commonMain/kotlin/org/easypaper/storage/RecordStore.kt
    A core/src/commonTest/kotlin/org/easypaper/model/SchemaTest.kt
    A core/src/commonTest/kotlin/org/easypaper/report/ReportTest.kt
    A core/src/commonTest/kotlin/org/easypaper/storage/RecordStoreTest.kt
    M gradle/libs.versions.toml

## Push 167 · 2026-11-21T15:23:59+00:00 · vlad · attempt/vlad/task-9-1

vlad pushed attempt/vlad/task-9-1: 1 commit(s), 4 file(s)
`0000000000` → `ad5879b230`

- ad5879b UladzislauHrynevich: core: report format v1, FHIR-like bundle, sealed transfer with key in QR

    A core/src/commonMain/kotlin/org/easypaper/report/Report.kt
    A core/src/commonMain/kotlin/org/easypaper/report/Transfer.kt
    M core/src/commonMain/kotlin/org/easypaper/storage/RecordStore.kt
    A core/src/commonTest/kotlin/org/easypaper/report/ReportTest.kt

## Push 160 · 2026-11-21T15:21:45+00:00 · vlad · attempt/vlad/task-2-2

vlad pushed attempt/vlad/task-2-2: 3 commit(s), 7 file(s)
`0000000000` → `4610fac459`

- 79648c2 UladzislauHrynevich: core: data schema v1 for requests, widgets and patient entries
- 50ed1dd UladzislauHrynevich: core: encrypted record store (AES-256-GCM via cryptography-kotlin)
- 4610fac UladzislauHrynevich: core: add kotlinx-serialization, cryptography-kotlin and coroutines-test

    M build.gradle.kts
    M core/build.gradle.kts
    A core/src/commonMain/kotlin/org/easypaper/model/Schema.kt
    A core/src/commonMain/kotlin/org/easypaper/storage/RecordStore.kt
    A core/src/commonTest/kotlin/org/easypaper/model/SchemaTest.kt
    A core/src/commonTest/kotlin/org/easypaper/storage/RecordStoreTest.kt
    M gradle/libs.versions.toml

## Push 149 · 2026-11-21T15:19:21+00:00 · mykhailo · main

mykhailo pushed main: 4 commit(s), 7 file(s)
`616b398c1d` → `36ac13425e`

- 4871df1 UladzislauHrynevich: core: data schema v1 for requests, widgets and patient entries
- f09b561 UladzislauHrynevich: core: encrypted record store (AES-256-GCM via cryptography-kotlin)
- 3d93acc Mykhailo Titov: core: kotlinx-serialization, cryptography-kotlin and coroutines-test for the data model (task 2)
- 36ac134 Mykhailo Titov: Merge task 2 work so far (data schema v1, encrypted record store) into main

    M build.gradle.kts
    M core/build.gradle.kts
    A core/src/commonMain/kotlin/org/easypaper/model/Schema.kt
    A core/src/commonMain/kotlin/org/easypaper/storage/RecordStore.kt
    A core/src/commonTest/kotlin/org/easypaper/model/SchemaTest.kt
    A core/src/commonTest/kotlin/org/easypaper/storage/RecordStoreTest.kt
    M gradle/libs.versions.toml

## Push 122 · 2026-11-21T15:12:05+00:00 · mykhailo · main

mykhailo pushed main: 1 commit(s), 62 file(s)
`824112f792` → `616b398c1d`

- 616b398 Mykhailo Titov: Task 1: KMP skeleton from kmp.jetbrains.com, test.py, teamhub.json, design skills

    A .claude/skills/easy-paper-design/SKILL.md
    A .claude/skills/frontend-design/LICENSE.txt
    A .claude/skills/frontend-design/SKILL.md
    A .gitattributes
    A .gitignore
    A README.md
    A app/androidApp/build.gradle.kts
    A app/androidApp/proguard-rules.pro
    A app/androidApp/src/main/AndroidManifest.xml
    A app/androidApp/src/main/kotlin/org/easypaper/MainActivity.kt
    A app/androidApp/src/main/res/drawable-v24/ic_launcher_foreground.xml
    A app/androidApp/src/main/res/drawable/ic_launcher_background.xml
    A app/androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
    A app/androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
    A app/androidApp/src/main/res/mipmap-hdpi/ic_launcher.png
    A app/androidApp/src/main/res/mipmap-hdpi/ic_launcher_round.png
    A app/androidApp/src/main/res/mipmap-mdpi/ic_launcher.png
    A app/androidApp/src/main/res/mipmap-mdpi/ic_launcher_round.png
    A app/androidApp/src/main/res/mipmap-xhdpi/ic_launcher.png
    A app/androidApp/src/main/res/mipmap-xhdpi/ic_launcher_round.png
    A app/androidApp/src/main/res/mipmap-xxhdpi/ic_launcher.png
    A app/androidApp/src/main/res/mipmap-xxhdpi/ic_launcher_round.png
    A app/androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png
    A app/androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png
    A app/androidApp/src/main/res/values/strings.xml
    A app/desktopApp/build.gradle.kts
    A app/desktopApp/src/main/kotlin/org/easypaper/main.kt
    A app/shared/build.gradle.kts
    A app/shared/src/androidHostTest/kotlin/org/easypaper/SharedLogicAndroidHostTest.kt
    A app/shared/src/androidMain/kotlin/org/easypaper/Platform.android.kt
    A app/shared/src/commonMain/composeResources/drawable/compose-multiplatform.xml
    A app/shared/src/commonMain/kotlin/org/easypaper/App.kt
    A app/shared/src/commonMain/kotlin/org/easypaper/Greeting.kt
    A app/shared/src/commonMain/kotlin/org/easypaper/Platform.kt
    A app/shared/src/commonTest/kotlin/org/easypaper/SharedCommonTest.kt
    A app/shared/src/jsMain/kotlin/org/easypaper/Platform.js.kt
    A app/shared/src/jvmMain/kotlin/org/easypaper/Platform.jvm.kt
    A app/shared/src/jvmTest/kotlin/org/easypaper/SharedLogicDesktopTest.kt
    A app/shared/src/wasmJsMain/kotlin/org/easypaper/Platform.wasmJs.kt
    A app/shared/src/webTest/kotlin/org/easypaper/SharedLogicWebTest.kt
    A app/webApp/build.gradle.kts
    A app/webApp/src/webMain/kotlin/org/easypaper/main.kt
    A app/webApp/src/webMain/resources/index.html
    A app/webApp/src/webMain/resources/styles.css
    A build.gradle.kts
    A core/build.gradle.kts
    A core/src/commonMain/kotlin/org/easypaper/GreetingUtil.kt
    A gradle.properties
    A gradle/gradle-daemon-jvm.properties
    A gradle/libs.versions.toml
    A gradle/wrapper/gradle-wrapper.jar
    A gradle/wrapper/gradle-wrapper.properties
    A gradlew
    A gradlew.bat
    A kotlin-js-store/wasm/yarn.lock
    A server/build.gradle.kts
    A server/src/main/kotlin/org/easypaper/Application.kt
    A server/src/main/resources/logback.xml
    A server/src/test/kotlin/org/easypaper/ApplicationTest.kt
    A settings.gradle.kts
    ... 2 more

## Push 114 · 2026-11-21T15:09:10+00:00 · vlad · attempt/vlad/task-2-1

vlad pushed attempt/vlad/task-2-1: 1 commit(s), 2 file(s)
`4871df134c` → `f09b561385`

- f09b561 UladzislauHrynevich: core: encrypted record store (AES-256-GCM via cryptography-kotlin)

    A core/src/commonMain/kotlin/org/easypaper/storage/RecordStore.kt
    A core/src/commonTest/kotlin/org/easypaper/storage/RecordStoreTest.kt

## Push 110 · 2026-11-21T15:08:27+00:00 · mykhailo · attempt/mykhailo/task-1-1

mykhailo pushed attempt/mykhailo/task-1-1: 1 commit(s), 62 file(s)
`0000000000` → `616b398c1d`

- 616b398 Mykhailo Titov: Task 1: KMP skeleton from kmp.jetbrains.com, test.py, teamhub.json, design skills

    A .claude/skills/easy-paper-design/SKILL.md
    A .claude/skills/frontend-design/LICENSE.txt
    A .claude/skills/frontend-design/SKILL.md
    A .gitattributes
    A .gitignore
    A README.md
    A app/androidApp/build.gradle.kts
    A app/androidApp/proguard-rules.pro
    A app/androidApp/src/main/AndroidManifest.xml
    A app/androidApp/src/main/kotlin/org/easypaper/MainActivity.kt
    A app/androidApp/src/main/res/drawable-v24/ic_launcher_foreground.xml
    A app/androidApp/src/main/res/drawable/ic_launcher_background.xml
    A app/androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
    A app/androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
    A app/androidApp/src/main/res/mipmap-hdpi/ic_launcher.png
    A app/androidApp/src/main/res/mipmap-hdpi/ic_launcher_round.png
    A app/androidApp/src/main/res/mipmap-mdpi/ic_launcher.png
    A app/androidApp/src/main/res/mipmap-mdpi/ic_launcher_round.png
    A app/androidApp/src/main/res/mipmap-xhdpi/ic_launcher.png
    A app/androidApp/src/main/res/mipmap-xhdpi/ic_launcher_round.png
    A app/androidApp/src/main/res/mipmap-xxhdpi/ic_launcher.png
    A app/androidApp/src/main/res/mipmap-xxhdpi/ic_launcher_round.png
    A app/androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png
    A app/androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png
    A app/androidApp/src/main/res/values/strings.xml
    A app/desktopApp/build.gradle.kts
    A app/desktopApp/src/main/kotlin/org/easypaper/main.kt
    A app/shared/build.gradle.kts
    A app/shared/src/androidHostTest/kotlin/org/easypaper/SharedLogicAndroidHostTest.kt
    A app/shared/src/androidMain/kotlin/org/easypaper/Platform.android.kt
    A app/shared/src/commonMain/composeResources/drawable/compose-multiplatform.xml
    A app/shared/src/commonMain/kotlin/org/easypaper/App.kt
    A app/shared/src/commonMain/kotlin/org/easypaper/Greeting.kt
    A app/shared/src/commonMain/kotlin/org/easypaper/Platform.kt
    A app/shared/src/commonTest/kotlin/org/easypaper/SharedCommonTest.kt
    A app/shared/src/jsMain/kotlin/org/easypaper/Platform.js.kt
    A app/shared/src/jvmMain/kotlin/org/easypaper/Platform.jvm.kt
    A app/shared/src/jvmTest/kotlin/org/easypaper/SharedLogicDesktopTest.kt
    A app/shared/src/wasmJsMain/kotlin/org/easypaper/Platform.wasmJs.kt
    A app/shared/src/webTest/kotlin/org/easypaper/SharedLogicWebTest.kt
    A app/webApp/build.gradle.kts
    A app/webApp/src/webMain/kotlin/org/easypaper/main.kt
    A app/webApp/src/webMain/resources/index.html
    A app/webApp/src/webMain/resources/styles.css
    A build.gradle.kts
    A core/build.gradle.kts
    A core/src/commonMain/kotlin/org/easypaper/GreetingUtil.kt
    A gradle.properties
    A gradle/gradle-daemon-jvm.properties
    A gradle/libs.versions.toml
    A gradle/wrapper/gradle-wrapper.jar
    A gradle/wrapper/gradle-wrapper.properties
    A gradlew
    A gradlew.bat
    A kotlin-js-store/wasm/yarn.lock
    A server/build.gradle.kts
    A server/src/main/kotlin/org/easypaper/Application.kt
    A server/src/main/resources/logback.xml
    A server/src/test/kotlin/org/easypaper/ApplicationTest.kt
    A settings.gradle.kts
    ... 2 more

## Push 101 · 2026-11-21T15:06:32+00:00 · vlad · attempt/vlad/task-2-1

vlad pushed attempt/vlad/task-2-1: 1 commit(s), 2 file(s)
`0000000000` → `4871df134c`

- 4871df1 UladzislauHrynevich: core: data schema v1 for requests, widgets and patient entries

    A core/src/commonMain/kotlin/org/easypaper/model/Schema.kt
    A core/src/commonTest/kotlin/org/easypaper/model/SchemaTest.kt

## Push 66 · 2026-11-21T14:58:22+00:00 · mykhailo · main

mykhailo pushed main: 1 commit(s), 0 file(s)
`0000000000` → `824112f792`

- 824112f Mykhailo Titov: Initial commit


