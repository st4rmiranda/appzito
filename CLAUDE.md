# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Stuble (`app/`, applicationId `com.company.stuble`) is a native Android app (Kotlin) that helps
students prepare for Brazilian college entrance exams (vestibular) by generating an infinite stream
of AI-written multiple-choice questions via the Gemini API, tracking daily progress/streaks, and
gamifying study habits (XP, levels, achievements, daily challenges).

## Commands

Build and install use the Gradle wrapper from the repo root.

```
./gradlew assembleDebug          # build debug APK
./gradlew installDebug            # build and install on a connected device/emulator
./gradlew test                    # run local JVM unit tests (app/src/test)
./gradlew connectedAndroidTest     # run instrumented tests on a device/emulator (app/src/androidTest)
./gradlew test --tests "com.company.stuble.SomeTest"   # run a single unit test class
./gradlew lint                    # run Android lint
```

There is currently no dedicated ktlint/detekt setup — `lint` is the only static check wired into Gradle.

### Required local config

`GEMINI_API_KEY` is read in `app/build.gradle.kts` via `project.findProperty("GEMINI_API_KEY")` and
injected into `BuildConfig.GEMINI_API_KEY`. It is **not** set in `local.properties` or a checked-in
`secrets.properties` — it must be supplied as a Gradle project property (e.g. a
`GEMINI_API_KEY=...` line in a local, untracked `gradle.properties`/`local.properties`, or `-P` on
the command line) or the build will bake the literal string `"null"` into `BuildConfig`. Firebase
config comes from `app/google-services.json` (already present).

## Architecture

### Screen flow

`LoginActivity` (launcher, Firebase Auth + Google Sign-In) → `CadastroActivity` (email signup) →
`PersonalizacaoActivity` (onboarding, writes a `PerfilEstudante` via `PerfilManager`) →
`MainActivity` (hosts a `BottomNavigationView` swapping `HomeFragment` / `SearchFragment` /
`ProfileFragment`) → `LoadingActivity` (brief splash, forwards an optional area filter /
"treino livre" flag) → `QuizActivity` (the question loop) → `ExplanationActivity` /
`MissionCompleteActivity`.

Note: `CadastroActivity.kt` lives at `app/src/main/java/CadastroActivity.kt` (outside the
`com/company/stuble` package directory) even though its package declaration is `com.company.stuble` —
Gradle's default sourceSet is not package-path-restricted, so this compiles, but new files should
still go under `app/src/main/java/com/company/stuble/...` to match the package.

### Question generation pipeline (`data/`)

`QuizActivity` never calls the network directly — it goes through `QuizRepository`:

1. `QuizRepository.obterProximaPergunta()` first checks `QuestionCacheManager` (a local pre-fetch
   cache keyed by area/date, used to avoid a loading spinner on every question and to survive
   transient API failures).
2. On a cache miss it calls `GeminiQuestionService.gerarPergunta()`, which POSTs to the
   `gemini-2.5-flash` `generateContent` endpoint with a strict JSON `responseSchema` (forces
   exactly 4 options, a 0-3 correct index, Portuguese prompt/explanation text).
3. `QuestionParser` defensively parses the model's JSON response — it tolerates markdown code
   fences, alternate field names (`opcoes`/`opções`/`alternativas`, `correta`/`resposta`/etc.),
   letter-coded answers ("A"/"B"/...), and truncated/malformed JSON before falling back to
   throwing. Any change to the Gemini prompt/schema in `GeminiQuestionService` should be mirrored
   here.
4. `QuizRepository.gerarComRetry()` retries once on transient errors (`IOException`, or
   `GeminiHttpException` with code 500/503) and rejects a question if `QuestionCacheManager` says
   the same question was already used today. On repository failure it falls back to any cached
   question before surfacing an error message to the UI.
5. `precarregarUmaPergunta()` is called opportunistically to keep the cache topped up (≥2 questions
   per area) in the background.

`QuizStateManager` persists the in-progress question (per day/filter/treino-livre mode) so
`QuizActivity` survives process death without re-spending an API call.

### Per-user local storage convention

There is no backend database for app state — everything besides auth is `SharedPreferences`,
namespaced per Firebase user. Every manager in `data/` (`ProgressManager`, `PerfilManager`,
`GamificacaoManager`, `DificuldadeAdaptativaManager`, `QuizStateManager`) follows the same pattern:
a `object` with a `PREFIXO_PREFS` constant, a private `identificadorUsuario()`/`uid()` that reads
`FirebaseAuth.getInstance().currentUser?.uid ?: "usuario_local"`, and a `prefs(context)` helper that
builds `PREFIXO_PREFS + uid` as the preferences file name. Follow this same pattern for any new
per-user persisted state rather than introducing a new storage mechanism.

- `ProgressManager` — daily question count (target 20/day) and study-streak ("ofensiva") logic,
  rolling over at local-date boundaries.
- `GamificacaoManager` — XP, levels, per-area stats, weekly calendar, achievements, and a
  day-of-year-rotated daily challenge (`RESPONDER_10` / `ACERTAR_7` / `SEQUENCIA_3`).
- `DificuldadeAdaptativaManager` — when the user's preferred difficulty is `"Adaptativo"`, tracks
  consecutive right/wrong answers to step the effective difficulty up (3 correct in a row) or down
  (2 wrong in a row) across `Básico`/`Intermediário`/`Avançado`.
- `PerfilManager` — onboarding profile (`PerfilEstudante`): school year, area of interest, target
  course, preferred difficulty, weak subjects.

### Notifications

`NotificationScheduler.scheduleDaily()` (called from `MainActivity` after the `POST_NOTIFICATIONS`
permission is granted) enqueues a WorkManager `PeriodicWorkRequest` (unique work, `KEEP` policy)
that fires `DailyNotificationWorker` once a day at 20:00 local time to nudge the user to keep their
streak going.
