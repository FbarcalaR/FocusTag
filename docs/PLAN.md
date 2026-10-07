# FocusTag — Implementation Plan

Status legend: `todo` / `in-progress` / `review` / `done`. Decision references (`D-xx`) point to `docs/DECISIONS.md`.

## 1. Architecture overview

```
                 ┌──────────────── ui (Compose, single Activity) ────────────────┐
                 │  StatusScreen/VM          SetupScreen/VM        BlockingScreen│
                 └───────┬──────────────────────┬──────────────────────┬────────┘
                         │ contracts only        │                      │
   NFC tag ──► NfcTriggerActivity ──┐            │                      │
   (manifest NDEF filter)           ▼            ▼                      │
                ┌─────── nfc ───────────┐  ┌──── focus ─────────────────────────┐
                │ NfcGateway (fakeable) │  │ FocusStateMachine (pure)            │
                │ TagValidator (pure)   │─►│ FocusStateStore (DataStore) = SSOT  │
                │ TagPairingStore       │  │ FocusEngine.onTag / reconcile()     │
                │ NdefCodec / writer    │  │ BootReceiver, AppStartHook, notif.  │
                └───────────────────────┘  └──────┬───────────────────┬─────────┘
                                                   │ FocusEffects      │ FocusStateReader
                                     ┌──── system ─▼──────────┐  ┌─────▼──── blocker ──────────┐
                                     │ ZenRuleController       │  │ BlockDecider (pure)         │
                                     │ (AutomaticZenRule +     │  │ BlockListStore (DataStore)  │
                                     │  ZenDeviceEffects)      │  │ FocusAccessibilityService   │
                                     │ SecureSettingsGrayscale │  │ BlockingActivity            │
                                     │ PermissionChecker       │  │ InstalledAppsRepository     │
                                     └─────────────────────────┘  └─────────────────────────────┘
```

* **Single source of truth:** `FocusStateStore`. `FocusEngine.reconcile()` derives every side effect (zen rule, fallback grayscale, notification, blocker immediate check) from the stored state. It is called after every transition, on app start, on boot, and when the zen rule changes externally (D-34, D-41, D-43).
* **Flow of a scan:** `NfcTriggerActivity` → `TagValidator` (with `TagPairingStore`) → `FocusController.onTagScanned(role)` → `FocusStateMachine.transition` → persist → `reconcile()`.
* **Blocker:** reads `FocusStateReader.state` (Flow) and `BlockListStore`. It does not call the engine.

### Package / file layout (`:app`, package `io.github.fbarcalar.focustag`)

```
app/src/main/java/io/github/fbarcalar/focustag/
  FocusTagApp.kt                 (T1)  @HiltAndroidApp, runs Set<AppStartHook>
  MainActivity.kt                (T1)  single activity, NavHost
  di/AppModule.kt                (T1)  Clock, ZoneId, CoroutineScopes/dispatchers, @Multibinds
  ui/theme/*                     (T1)
  ui/nav/FocusTagNavHost.kt      (T1)  routes "status", "setup"; start dest logic
  ui/common/*                    (T1)  shared composables (PermissionBanner shell)
  ui/status/*                    (T6)
  ui/setup/*                     (T7)
  focus/Contracts.kt             (T1, frozen) FocusMode, FocusState, TagRole, FocusController,
                                            FocusStateReader, FocusEffects, AppStartHook
  focus/**  (everything else)    (T2)
  nfc/Contracts.kt               (T1, frozen) NfcGateway, ScannedTag, NfcTagHandle, WriteResult,
                                            PairingRepository, TagPairing, PairingResult, TagWriter
  nfc/**                         (T3)
  system/Contracts.kt            (T1, frozen) PermissionChecker, PermissionItem, PermissionId,
                                            PermissionStatus, GrayscaleFallbackSettings
  system/**                      (T4)
  blocker/Contracts.kt           (T1, frozen) BlockListRepository, InstalledApp, InstalledAppsSource
  blocker/**                     (T5)
app/src/main/AndroidManifest.xml (T1)  all components declared up front (D-51)
app/src/main/res/xml/*           (T1)  accessibility_service_config.xml, nfc tech list (unused)
app/src/main/res/values/strings_<layer>.xml   owned by the layer's task
app/src/test/java/.../<layer>/** owned by the layer's task;  .../testing/** fakes (T1)
README.md, docs/MANUAL_CHECKS.md (T8)
```

## 2. Workflow rules (all tasks)

1. Each task runs on a local worktree branch `task/Tn-<slug>` cut from the integration branch (D-52).
2. A task edits **only** the files it owns. If it needs a change in a frozen `Contracts.kt` or a T1-owned file, it stops and asks the orchestrator. The orchestrator makes the change on the integration branch and rebases the open worktrees.
3. Loop: **Plan** (the implementer refines subtasks under "Refinement notes") → **Plan review** (a separate subagent; it may tighten the plan but must not change the goal or intent) → **Implement** (small commits, each with its tests) → **Test** (`./gradlew assembleDebug lint test` green) → **Review** (a separate subagent, at most 3 cycles, then escalate to the user) → **Integrate** (rebase on the integration head, add the cross-layer E2E scenarios this merge unlocks — see §2.2 — green) → **Done** (merge, full build on the integration branch, set status).
4. After each parallel group: run a full `./gradlew assembleDebug lint test` on the integration branch, then push.
5. Placeholders shipped by T1 (`Placeholder*` classes) must be **deleted** by the owning task. T8 checks that none remain.
6. Gradle runs with `-Xmx2g` and in-process Kotlin compilation (set in `gradle.properties`), so four worktrees can build at once on the 4-core / 15 GB container.

### 2.1 Engineering standards (all code)

* **KISS / YAGNI:** the simplest thing that meets the acceptance criteria. No speculative abstractions, no frameworks beyond the agreed stack, and no "manager"/"helper" grab-bag classes.
* **SOLID:** one reason to change per class. Depend on the `Contracts.kt` interfaces, not on implementations. Android framework calls are wrapped in thin adapters so decision logic stays pure and JVM-testable.
* **Small units:** functions ≲ 20 lines with a single level of abstraction, and files ≲ 200 lines. Prefer `when` over nested `if`s. Keep cyclomatic complexity low; if a function needs a comment to explain its branches, split it.
* **Naming over comments:** intention-revealing names. KDoc on public contracts only. Comments explain *why*, never *what*.
* **Immutability & explicitness:** `val`, immutable data classes, sealed types for results and states. No nullable "maybe" flags where a sealed type fits. No `!!`.
* **Errors:** expected failures are typed results (sealed classes). Exceptions only for programmer errors. Framework `SecurityException`s are caught at the adapter boundary.
* **Coroutines:** structured concurrency only (injected scopes and dispatchers, no `GlobalScope`, no `runBlocking` in production code).
* **Compose:** stateless screen composables (`XxxScreen(state, onEvent)`) plus a thin stateful wrapper that wires the ViewModel. Previews for each screen state.
* **Tests:** Arrange-Act-Assert, one behaviour per test, names that read as sentences (`` `scanning desk tag while free starts focus` ``). Use fakes rather than mocks where a fake is simple.

### 2.2 End-to-end tests as features land

E2E tests are **not** a final phase. T1 ships an E2E harness: `test/.../e2e/FocusTagE2E.kt`, a Robolectric + Hilt test base with a small DSL (`scanTag(role)`, `scanUnknownTag()`, `openApp(pkg)`, `reboot()`, `seedPreferences(file) { }` for cold starts, `revoke(permission)`, `assertMode(...)`, `assertZenRuleActive(...)`, `assertBlockingShown(...)`). It fakes only the hardware/OS edges (`NfcGateway`, system manager shadows). Every task adds the scenarios it can drive **in the same commit series as the feature**:

* **Slice E2E** (written during Implement, owned by the task): the task's layer driven from its Android entry point (intent, broadcast, accessibility event, screen) with the other layers' placeholders/fakes.
* **Cross-layer E2E** (written during Integrate): each scenario below lists the tasks it needs. **The task whose merge completes that set writes it**, after rebasing on the integration head. The files live in `test/.../e2e/scenarios/<Scenario>Test.kt`, one file per scenario, so ownership never overlaps. Slice E2E files live in the task's own test dir (`test/.../<layer>/<Layer>SliceE2ETest.kt`).

| Scenario | Needs | Written by |
|----------|-------|------------|
| E2E-1 Desk tag while FREE → FOCUS persisted, zen rule active, notification shown | T2, T3, T4 | **T4** · `E2E1DeskTagStartsFocusTest` |
| E2E-2 Double desk scan / living-room tag while FREE → no change | T2, T3 | **T3** · `E2E2NoChangeScansTest` |
| E2E-3 Unknown tag, UID mismatch, foreign URI → ignored | T2, T3 | **T3** · `E2E3IgnoredTagsTest` |
| E2E-4 Living-room tag while FOCUS → FREE, zen rule off, today's total updated | T2, T3, T4 | **T4** · `E2E4LivingRoomTagEndsFocusTest` |
| E2E-5 FOCUS + open blocked app → blocking screen; allowed app → nothing | T2, T5 | T5 (T2 merged) |
| E2E-6 Blocked app already in foreground when FOCUS starts → blocked | T2, T3, T5 | T5 (T2, T3 merged) |
| E2E-7 Process death in FOCUS (cold start from a seeded FOCUS store) → reconcile re-applies effects, exactly one zen rule | T2, T4 | **T4** · `E2E7ProcessDeathInFocusTest` |
| E2E-8 Reboot broadcast in FOCUS → effects re-applied | T2, T4 | **T4** · `E2E8RebootInFocusTest` |
| E2E-9 DND access revoked mid-session → no crash, degraded status, banner on Status | T2, T4, T6 | T6 (T2, T4 merged) |
| E2E-10 Zen rule turned off externally while FOCUS → re-asserted | T2, T4 | **T4** · `E2E10ZenRuleExternallyOffTest` |
| E2E-11 Status screen reflects scans live (FREE → FOCUS → FREE), no exit control | T2, T3, T6 | T6 (T2, T3 merged) |
| E2E-12 Setup: pair A and B via fake gateway → app routes to Status; reset blocked in FOCUS | T2, T3, T7 | T7 (T2, T3 merged) |
| E2E-13 Setup: remove app from block list blocked in FOCUS, allowed in FREE | T2, T5, T7 | last of T5/T7 |

Merges now follow readiness, not the G2 order in §4 (T2, then T3). "Written by" names the task that wrote the scenario (in bold), or the task expected to complete its set.

## 3. Tasks

### T1 — Project skeleton, DI, navigation, contracts, CI · status: `done`
**Goal:** A buildable Compose app where all three Gradle commands pass, with frozen cross-layer contracts so later tasks can run in parallel.
**Owns:** root Gradle files, `gradle/`, `gradlew*`, `settings.gradle.kts`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/lint.xml`, `.gitignore`, `.github/workflows/ci.yml`, `scripts/setup-android-sdk.sh`, `AndroidManifest.xml`, `res/xml/*`, `res/values/{strings,themes,colors}.xml`, launcher icons, `FocusTagApp.kt`, `MainActivity.kt`, `di/`, `ui/theme/`, `ui/nav/`, `ui/common/`, every `*/Contracts.kt`, every `*/di/<Layer>Module.kt` + `Placeholder*.kt` (handed over to the layer task once T1 is done), `app/src/test/.../testing/` fakes, `app/src/test/.../e2e/FocusTagE2E.kt`.
**Depends on:** —
**Subtasks:**
1. SDK bootstrap script (cmdline-tools, `platforms;android-37`, matching build-tools, `local.properties`), and `.gitignore`.
2. Gradle wrapper (latest stable 9.x compatible with AGP 9.4), version catalog: Compose BOM, Material 3, Navigation-Compose, Lifecycle, DataStore Preferences, Hilt + KSP, Coroutines, kotlinx-serialization (nav routes), JUnit4, Robolectric, Compose ui-test-junit4, Turbine, Truth/AssertJ. Confirm Hilt works with AGP 9 (otherwise use manual DI, D-04).
3. `app` config: minSdk 33, compile/target 37, Java/Kotlin 17+ toolchain, `testOptions.unitTests.isIncludeAndroidResources = true`, lint `warningsAsErrors=false`, `abortOnError=true`, baseline none. Record any Android 17 target behaviour change in DECISIONS.md.
4. `FocusTagApp` (@HiltAndroidApp, runs `Set<AppStartHook>` on an app scope), `di/AppModule` (Clock, ZoneId provider, `@ApplicationScope CoroutineScope`, dispatchers, `@Multibinds Set<AppStartHook>`).
5. `MainActivity` + `FocusTagNavHost` with placeholder Status and Setup composables. Start destination: Setup if not both tags are paired, otherwise Status. Status has a "Setup" entry (top-bar icon) and Setup has back navigation.
6. Write all `Contracts.kt` (KDoc on every member) + `Placeholder*` implementations + per-layer Hilt modules, so the graph is complete.
7. Manifest declares: permissions (`NFC`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `ACCESS_NOTIFICATION_POLICY`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `WRITE_SECURE_SETTINGS` with `tools:ignore="ProtectedPermissions"`), `uses-feature nfc required=true`, `<queries>` launcher intent, `MainActivity`, `NfcTriggerActivity` (NDEF_DISCOVERED `focustag://toggle`, translucent theme), `BlockingActivity`, `FocusAccessibilityService` + `accessibility_service_config.xml`, `BootReceiver` — all pointing at stub classes in their layer packages (stubs owned by the layer task).
8. Shared test fakes in `testing/`: `FakeClock`, `FakeNfcGateway`, `FakeFocusEffects`, `FakePermissionChecker`, and a temp-dir DataStore helper. E2E harness `e2e/FocusTagE2E.kt` + DSL (§2.2), with one harness self-test.
9. CI workflow running `./gradlew assembleDebug lint test` with the SDK script. Smoke tests: one JVM test plus one Robolectric Compose test that navigates Status ↔ Setup.

**Acceptance criteria:** the three commands pass from a clean clone after `scripts/setup-android-sdk.sh`; the app launches to the Setup placeholder (asserted by `HiltGraphTest`); navigation works in the Robolectric test; the E2E harness self-test passes; every contract has KDoc; the manifest contains every component listed above.
**Test strategy:** Robolectric navigation test, a Hilt graph test (`@HiltAndroidTest` launching `MainActivity`), and the build commands.

#### Refinement notes (T1)

##### R1. Toolchain and versions (checked against maven metadata on 2026-10-06)

| Item | Version | Why / check |
|------|---------|-------------|
| JDK (Gradle and test JVM) | 21 | Robolectric needs Java 21 for SDK ≥ 35. Bytecode target is 17 (`compileOptions` plus `kotlin.compilerOptions.jvmTarget`). |
| AGP | **9.4.1** | Latest stable. Its `VersionCheckPlugin` requires Gradle ≥ 9.6.0. It bundles KGP 2.2.10, which is raised by the Kotlin plugins below. |
| Gradle wrapper | **9.8.0** | Latest stable (≥ 9.6.0). Fallback: 9.6.1 if AGP warns or fails. |
| Kotlin (built-in Kotlin, plus the `plugin.compose` and `plugin.serialization` plugins) | **2.3.21** | 2.4.20 is the newest stable release, but KSP 2.3.12 is built on 2.3.20, Hilt 2.60.1 on kotlin-bom 2.3.21 and serialization 1.11 on stdlib 2.3.20. Staying on 2.3.x keeps the whole chain on one Kotlin line. A bump to 2.4.x waits until KSP/Dagger move. |
| KSP | **2.3.12** | Its plugin detects AGP built-in Kotlin (`isAgpBuiltInKotlinUsed`, which needs AGP ≥ 9.0.0-alpha14). We do **not** apply `kotlin-android`. |
| Hilt / Dagger (+ `hilt-android-testing`) | **2.60.1** | Its Gradle plugin *requires* AGP ≥ 9.0.0 (checked in `HiltPluginEnvironment`). **D-04 holds: Hilt works and manual DI is not needed.** |
| `androidx.hilt:hilt-lifecycle-viewmodel-compose` | 1.4.0 | `hiltViewModel()` (it replaces `hilt-navigation-compose`). |
| Compose BOM | **2026.09.00** | ui 1.12.1, material3 1.4.0, material-icons-core 1.7.8 (Settings and ArrowBack only), ui-test-junit4 / ui-test-manifest. |
| activity-compose | 1.13.0 | |
| core-ktx | 1.19.1 | AAR metadata: `minCompileSdk=37`, `minAndroidGradlePluginVersion=9.1.0` (both met). |
| lifecycle (runtime-compose, viewmodel-compose) | 2.11.0 | |
| navigation-compose | 2.10.2 | Type-safe `@Serializable` routes. |
| datastore-preferences | 1.2.1 | |
| kotlinx-coroutines (android, test) | 1.11.0 | |
| kotlinx-serialization-core | 1.11.0 | Nav routes only. |
| JUnit 4 / Truth / Turbine | 4.13.2 / 1.4.5 / 1.2.1 | |
| Robolectric | **4.17** | `DefaultSdkProvider` includes android-all `17-robolectric-15733970`, so **SDK 37 is supported**. Set `sdk=37` in `app/src/test/resources/robolectric.properties`; fallback `sdk=36` if SDK 37 has a blocking bug. |
| androidx.test core / ext-junit | 1.7.0 / 1.3.0 | |
| SDK packages | `platforms;android-37.0`, `build-tools;37.0.0`, `platform-tools`, cmdline-tools `latest` (16111833) | No plain `android-37` package exists, only `android-37.0/.1/.2`. **Correction to D-01/D-06, recorded in DECISIONS during Implement.** |

* `compileSdk { version = release(37) }`, `targetSdk { version = release(37) }`, `minSdk { version = release(33) }` (AGP 9 DSL; it must resolve to `platforms/android-37.0`). Fallbacks, in order: `release(37) { minorApiLevel = 0 }`, then `compileSdkVersion = "android-37.0"`. Not `compileSdk = 37`: it looks for `android-37`, which doesn't exist.
* `gradle.properties`: `org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8`, `kotlin.compiler.execution.strategy=in-process`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true` (disable if KSP/Hilt break it), `android.useAndroidX=true`. Unit tests: `maxHeapSize = "1g"`, `isIncludeAndroidResources = true`.
* Root `build.gradle.kts` declares the plugins with `apply false`: `com.android.application`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`, `com.google.devtools.ksp`, `com.google.dagger.hilt.android`. Putting KGP 2.3.21 on the classpath is what upgrades AGP's built-in Kotlin.
* **Android 17 target changes to record in DECISIONS (D-07):** static final fields can't be modified via reflection (no impact); lock-free `MessageQueue` (no reflection on it; Robolectric is unaffected); BAL hardening for `IntentSender`/`PendingIntent` (our only `PendingIntent` is the notification's content intent, which is user-initiated, and the blocker starts activities from an accessibility service, so there's no impact; T2 must not use `MODE_BACKGROUND_ACTIVITY_START_ALLOWED`); the large-screen orientation/resizability opt-out is removed (phone only, no impact). All of these apply only on Android 17 devices. The Pixel 10a runs 16.
* `scripts/setup-android-sdk.sh`: `ANDROID_HOME` defaults to `/opt/android-sdk` if it exists, else `$HOME/android-sdk`. The script is idempotent and installs only what is missing (cmdline-tools zip, the three packages, licences). It writes `local.properties` (`sdk.dir=…`). `/opt/android-sdk` is already provisioned in this container.
* CI (`.github/workflows/ci.yml`): ubuntu-latest, `actions/setup-java` (temurin 21), `gradle/actions/setup-gradle`, the SDK script, then `./gradlew assembleDebug lint test`. It caches `~/.m2/repository/org/robolectric` (Robolectric downloads android-all there).
* Lint (`app/lint.xml`): `abortOnError=true`, `warningsAsErrors=false`, no baseline. `GradleDependency`/`NewerVersionAvailable` stay as warnings. The manifest uses `tools:ignore="ProtectedPermissions"` on `WRITE_SECURE_SETTINGS`.

##### R2. Cross-cutting rules the contracts rely on

1. **Time:** inject only `java.time.Clock`. Production binds `DeviceClock`, a `Clock` whose `getZone()` returns `ZoneId.systemDefault()` on every call, so a timezone change is picked up. `FakeClock` is a `Clock` with a mutable instant and zone. This one dependency replaces the "Clock + ZoneId provider" pair in subtask 4.
2. **Process lifetime:** everything that lives as long as the process (DataStore instances, dynamically registered receivers, collectors) is bound to the `@ApplicationScope CoroutineScope` (`SupervisorJob() + Dispatchers.Default`) and releases on cancellation (`awaitClose` / `finally { unregister }`). Each DataStore is created in its layer's Hilt module with `PreferenceDataStoreFactory.create(scope = CoroutineScope(appScope.coroutineContext + ioDispatcher)) { context.preferencesDataStoreFile("<layer>") }`. **The `by preferencesDataStore` delegate is forbidden**: it's process-static, so under Robolectric it leaks across tests that share a sandbox, and it can't be closed, but the harness's cold-start seeding (R6) needs a DataStore it can close. File names: `focus_state`, `tag_pairings`, `block_list`, `system_settings`.
3. **Qualifiers** (`di/Qualifiers.kt`): `@ApplicationScope`, `@IoDispatcher`, `@DefaultDispatcher`. There's no main-dispatcher qualifier (ViewModels use `viewModelScope`).
4. **Dependency direction:** a layer depends only on other layers' `Contracts.kt`. `focus` defines `FocusEffects`, which `system` implements. `android.nfc.*` (including `Tag`/`NdefMessage`) may be imported only in `nfc/AndroidNfcGateway*` and `nfc/NfcTriggerActivity` (checked by T8).
5. **Bindings:** each layer binds its contracts in `<layer>/di/<Layer>Module.kt` (`@InstallIn(SingletonComponent)`). The NFC hardware binding lives in its **own** module, `nfc/di/NfcGatewayModule.kt`, so tests can replace just the gateway. T3 keeps that module's name and package (`TestNfcGatewayModule` replaces it by class).

##### R3. Contracts (frozen after T1; KDoc on every member in the real files)

Types: `java.time.Instant`, `kotlin.time.Duration` (also in `FakeClock.advanceBy`).

```kotlin
// focus/Contracts.kt
enum class TagRole { ACTIVATE /* Tag A, desk */, DEACTIVATE /* Tag B, living room */ }
enum class FocusMode { FREE, FOCUS }

sealed interface FocusState {
    val mode: FocusMode
    data object Free : FocusState { override val mode = FocusMode.FREE }
    data class Focus(val since: Instant) : FocusState { override val mode = FocusMode.FOCUS }
}

/** currentSession is ZERO while FREE; todayTotal includes the live session (D-42). */
data class FocusStats(val currentSession: Duration, val todayTotal: Duration) // kotlin.time.Duration

enum class ScanOutcome { ACTIVATED, DEACTIVATED, NO_CHANGE }

/** Mode-changing entry point. Only nfc calls it (and the test harness). */
interface FocusController {
    /** Serialised; persists, then reconciles effects. Never throws for permission problems. */
    suspend fun onTagScanned(role: TagRole): ScanOutcome
}

/** Read-only view for blocker and UI. Cold flows backed by the store; distinctUntilChanged. */
interface FocusStateReader {
    val state: Flow<FocusState>
    /** Ticks every second while FOCUS. */
    val stats: Flow<FocusStats>
    /** Result of the last reconcile (D-35). */
    val effectsStatus: StateFlow<EffectsStatus>
}

enum class Effect { ZEN_RULE, GRAYSCALE_FALLBACK }
data class EffectsStatus(val failed: Set<Effect> = emptySet()) { val isDegraded get() = failed.isNotEmpty() }

/** System side effects of FOCUS. Implemented by system. Idempotent; enable() re-asserts an externally disabled rule (D-34). */
interface FocusEffects {
    suspend fun enable(): EffectsStatus
    suspend fun disable(): EffectsStatus
}

/** Contributed via @IntoSet; run once per process from FocusTagApp.onCreate on the app scope (D-43). */
fun interface AppStartHook { suspend fun onAppStart() }
```

```kotlin
// nfc/Contracts.kt
/** Hardware-free view of a scanned tag (D-15). uidHex is upper-case hex, no separators. */
data class ScannedTag(val uidHex: String, val ndefUris: List<String>)

/** A tag currently in the field (reader mode), opaque outside nfc. */
interface NfcTagHandle { val scanned: ScannedTag }

enum class NfcAvailability { UNAVAILABLE, DISABLED, ENABLED }
enum class WriteFailure { READ_ONLY, TOO_SMALL, NOT_NDEF, IO_ERROR, VERIFY_FAILED }
sealed interface WriteResult {
    data object Written : WriteResult
    data class Failed(val reason: WriteFailure) : WriteResult
}

/** The only door to android.nfc. Faked in every Hilt test (FakeNfcGateway). */
interface NfcGateway {
    val availability: Flow<NfcAvailability>
    /** Parses an NDEF_DISCOVERED intent; null if it carries no tag. */
    fun readTag(intent: Intent): ScannedTag?
    /** onTag runs on a binder thread; the caller hops to its own scope. */
    fun enableReaderMode(activity: Activity, onTag: (NfcTagHandle) -> Unit)
    fun disableReaderMode(activity: Activity)
    /** Writes URI record + AAR, then reads back and verifies (D-10). */
    suspend fun writeFocusTag(tag: NfcTagHandle, uri: String): WriteResult
}

data class TagPairing(val role: TagRole, val tagId: String, val uidHex: String)
fun Map<TagRole, TagPairing>.isComplete(): Boolean = TagRole.entries.all { it in this }

sealed interface PairingResult {
    data class Paired(val pairing: TagPairing) : PairingResult
    data object UidUsedByOtherRole : PairingResult
    data class WriteFailed(val reason: WriteFailure) : PairingResult
}

interface PairingRepository {
    val pairings: Flow<Map<TagRole, TagPairing>>
    /** Returns Paired or UidUsedByOtherRole (never WriteFailed). */
    suspend fun save(pairing: TagPairing): PairingResult
    suspend fun reset(role: TagRole)
    suspend fun resetAll()
}

/**
 * Pairing use case: UID check → new UUID → write → verify → save; old pairing untouched on failure.
 * The UID check comes first: writing a tag already paired to the other role would invalidate that role.
 */
interface TagWriter { suspend fun pair(tag: NfcTagHandle, role: TagRole): PairingResult }
```

`TagScanResult` (Valid/Unknown/UidMismatch/Malformed) is **not** a contract: only `NfcTriggerActivity` consumes it, so T3 defines it next to `TagValidator`.

```kotlin
// system/Contracts.kt
enum class PermissionId {
    NFC_ENABLED, ACCESSIBILITY_SERVICE, NOTIFICATION_POLICY_ACCESS, POST_NOTIFICATIONS,
    BATTERY_OPTIMIZATION_EXEMPTION, GRAYSCALE_CAPABILITY, WRITE_SECURE_SETTINGS,
}
enum class PermissionStatus { GRANTED, MISSING, UNSUPPORTED }

sealed interface PermissionAction {
    /** First intent is primary; accessibility adds App info for "Allow restricted settings" (D-25). */
    data class OpenSettings(val intents: List<Intent>) : PermissionAction
    data class RequestRuntime(val permission: String, val settingsIntent: Intent) : PermissionAction
    data class AdbGrant(val command: String) : PermissionAction
}

data class PermissionItem(
    val id: PermissionId,
    val status: PermissionStatus,
    val required: Boolean,
    val action: PermissionAction,
)
val List<PermissionItem>.missingRequired get() = filter { it.required && it.status == PermissionStatus.MISSING }

interface PermissionChecker {
    val items: StateFlow<List<PermissionItem>>
    /** Re-evaluates now (called on ON_RESUME). */
    fun refresh()
}

interface GrayscaleFallbackSettings {
    val enabled: Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
}
```

```kotlin
// blocker/Contracts.kt
const val EXTRA_BLOCKED_PACKAGE = "io.github.fbarcalar.focustag.extra.BLOCKED_PACKAGE"

sealed interface RemoveResult {
    data object Removed : RemoveResult
    data object NotAllowedDuringFocus : RemoveResult
}

interface BlockListRepository {
    val blockedPackages: Flow<Set<String>>
    suspend fun add(packageName: String)
    /** Refused while FOCUS (D-45). */
    suspend fun remove(packageName: String): RemoveResult
}

data class InstalledApp(val packageName: String, val label: String)

interface InstalledAppsSource {
    /** Launchable apps except ours, sorted by label, IO dispatcher. */
    suspend fun launchableApps(): List<InstalledApp>
    /** Null when the package has no loadable icon. */
    suspend fun icon(packageName: String): Bitmap?
}
```

**UI entry points (frozen signatures, bodies owned by T6/T7):** `ui/status/StatusDestination.kt` → `@Composable fun StatusDestination(onOpenSetup: () -> Unit)`. `ui/setup/SetupDestination.kt` → `@Composable fun SetupDestination(onBack: (() -> Unit)?, onPairingComplete: () -> Unit)`, where `onBack` is null when Setup is the start destination. T1 ships placeholder bodies marked `// PLACEHOLDER(T6|T7)`, which T8 greps for.

##### R4. Placeholders, modules, stubs (T1 writes them; each layer task deletes or replaces its own)

| Layer | Module | Placeholder bindings | Manifest stubs |
|-------|--------|----------------------|----------------|
| focus | `focus/di/FocusModule.kt` | `PlaceholderFocusEngine` (in-memory `MutableStateFlow`, D-40 transitions, calls `FocusEffects`; stats are zeros) bound to both `FocusController` and `FocusStateReader` (same `@Singleton`) | `focus/boot/BootReceiver` (`@AndroidEntryPoint`, no-op) |
| nfc | `nfc/di/NfcModule.kt`, `nfc/di/NfcGatewayModule.kt` | `PlaceholderNfcGateway` (UNAVAILABLE, `readTag` returns null, writes fail with IO_ERROR), `PlaceholderPairingRepository` (in-memory), `PlaceholderTagWriter` (WriteFailed IO_ERROR) | `nfc/NfcTriggerActivity` (finishes immediately) |
| system | `system/di/SystemModule.kt` | `PlaceholderFocusEffects` (no-op, healthy), `PlaceholderPermissionChecker` (empty list), `PlaceholderGrayscaleFallbackSettings` (in-memory false) | — |
| blocker | `blocker/di/BlockerModule.kt` | `PlaceholderBlockListRepository` (in-memory), `PlaceholderInstalledAppsSource` (empty) | `blocker/FocusAccessibilityService` (empty overrides), `blocker/BlockingActivity` (finishes) |
| app | `di/AppModule.kt` (`@Multibinds Set<AppStartHook>`, `AppStartRunner`), `di/TimeModule.kt` (`Clock` = `DeviceClock`), `di/CoroutinesModule.kt` | — | `MainActivity` |

`AppStartRunner(hooks, @ApplicationScope scope).run()` launches each hook in its own coroutine. `FocusTagApp.onCreate` and the harness both call it.

**Navigation:** `ui/nav/FocusTagNavHost.kt` with `@Serializable data object StatusRoute` / `SetupRoute`. `ui/nav/StartViewModel` maps `PairingRepository.pairings` to `sealed StartDestination { Loading, Setup, Status }`. While Loading it shows an empty `Surface` (no flash of the wrong screen). `onPairingComplete` navigates to Status with `popUpTo<SetupRoute> { inclusive = true }`. Status has a top-bar Settings icon → Setup. Setup has a back arrow when it isn't the start destination. `ui/common/PermissionBanner(missing: List<PermissionItem>, onClick)` is a stateless shell.

**Manifest:** permissions as in subtask 7; `uses-feature android.hardware.nfc required=true`; `<queries>` for `MAIN/LAUNCHER` **and** `MAIN/HOME` (the default-launcher lookup in D-22). `MainActivity` (exported, launcher, `singleTop`). `NfcTriggerActivity`: exported, `Theme.FocusTag.Translucent`, `excludeFromRecents`, `noHistory`, `taskAffinity=""`, `NDEF_DISCOVERED` + `DEFAULT` + `<data scheme="focustag" host="toggle"/>`. `BlockingActivity`: not exported, `taskAffinity="${applicationId}.blocking"`, `excludeFromRecents`, `launchMode=singleTask`. `FocusAccessibilityService`: exported, `BIND_ACCESSIBILITY_SERVICE`, meta-data → `res/xml/accessibility_service_config.xml` (`typeWindowStateChanged|typeWindowsChanged`, `feedbackGeneric`, `canRetrieveWindowContent=true`, `notificationTimeout=0`, `description=@string/accessibility_service_description`). `BootReceiver`: exported, `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED`. The NFC tech-list XML is **dropped** (YAGNI: we only use `NDEF_DISCOVERED`).

##### R5. Test fakes (`app/src/test/.../testing/`)

`FakeClock` (2026-10-06T10:00Z, Europe/Madrid; `advanceBy(Duration)`, `zone` setter). `FakeNfcGateway` (`availability` `MutableStateFlow`, `enqueueRead(ScannedTag?)`, `present(FakeTagHandle)` fires the reader-mode callback, `writeResults` queue, recorded writes, `readerModeEnabled` flag for balance checks). `FakeFocusEffects` (records an `enable`/`disable` call list, configurable `EffectsStatus`). `FakePermissionChecker` (`MutableStateFlow` items, refresh count). `FakeFocusEngine` (in-memory `FocusController` + `FocusStateReader` for the T5–T7 VM tests). `TestDataStores.preferences(dir, scope)`. `MainDispatcherRule`.

Hilt test overrides (`@TestInstallIn`, which apply to **every** `@HiltAndroidTest`): `TestTimeModule` replaces `TimeModule` (`@Singleton FakeClock`, also bound as `Clock`), and `TestNfcGatewayModule` replaces `NfcGatewayModule` (`@Singleton FakeNfcGateway`, also bound as `NfcGateway`). The real `AndroidNfcGateway` is tested by T3 without Hilt. `robolectric.properties`: `sdk=37`, `application=dagger.hilt.android.testing.HiltTestApplication`.

##### R6. E2E harness (`e2e/FocusTagE2E.kt`)

`@HiltAndroidTest abstract class FocusTagE2E` with rules `HiltAndroidRule` (order 0) and `createEmptyComposeRule()` (order 1). Scenario classes extend it and are annotated `@HiltAndroidTest @RunWith(AndroidJUnit4::class)`. It reads the graph lazily through `EntryPoints.get(app, HarnessEntryPoint::class.java)`, which exposes `FocusController`, `FocusStateReader`, `PairingRepository`, `BlockListRepository`, `PermissionChecker`, `FakeNfcGateway`, `FakeClock` and `AppStartRunner`. The harness's own `@Before` resolves nothing, so no singleton (and no DataStore) exists until a scenario first touches the graph.

| DSL | Implementation |
|-----|----------------|
| `startApp()` | `AppStartRunner.run()` (what `FocusTagApp.onCreate` does), then `idle()` |
| `openMainUi()` | `ActivityScenario.launch(MainActivity)`; the compose rule finds it |
| `pairTags()` | `PairingRepository.save` for both roles with fixed `TAG_A`/`TAG_B` (uid, uuid) |
| `scanTag(role)` / `scanTagWithWrongUid(role)` / `scanUnknownTag()` / `scanForeignUri()` | `FakeNfcGateway.enqueueRead(ScannedTag(...))`, then launch `NfcTriggerActivity` with an `ACTION_NDEF_DISCOVERED` intent (data `focustag://toggle/<id>`), then `idle()` |
| `scanTagDirect(role)` | `FocusController.onTagScanned(role)`, for slices that run before T3 lands |
| `openApp(pkg)` | keeps a `ServiceController<FocusAccessibilityService>` and calls `onAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED, pkg)` |
| `turnZenRuleOffExternally()` | `NotificationManager.setAutomaticZenRuleState(id, FALSE)` on our rule, then broadcast `ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED` |
| `revoke(grant)` / `grant(grant)` | `SystemGrant { NOTIFICATION_POLICY, POST_NOTIFICATIONS, ACCESSIBILITY_SERVICE, NFC, WRITE_SECURE_SETTINGS }` mapped onto the Robolectric shadows (`ShadowNotificationManager.setNotificationPolicyAccessGranted`, `ShadowApplication.grant/denyPermissions`, `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, `FakeNfcGateway.availability`); then `PermissionChecker.refresh()` |
| `advanceClock(d)` | `FakeClock.advanceBy(d)` |
| `seedPreferences(file) { store -> }` | cold-start seam, see below |
| `reboot()` | reset volatile OS state (our zen rules set to `STATE_FALSE`, all notifications cancelled), then `sendBroadcast(BOOT_COMPLETED, package=ours)` + `idle()`. It does not call `startApp()`, so it isolates `BootReceiver`. |
| `assertMode(m)` | `eventually { FocusStateReader.state.first().mode == m }` |
| `assertZenRuleActive(b)` | `NotificationManager.getAutomaticZenRules()` filtered to our package, `getAutomaticZenRuleState(id) == STATE_TRUE` (Robolectric 4.17 shadows both) |
| `assertBlockingShown(pkg)` / `assertNothingBlocked()` | `shadowOf(app).nextStartedActivity` → `BlockingActivity` with `EXTRA_BLOCKED_PACKAGE` |
| `assertEffectsDegraded(b)` | `FocusStateReader.effectsStatus.value.isDegraded` |
| `eventually(timeout = 5s) { }` / `idle()` | loops `shadowOf(mainLooper).idle()` + a short sleep until the assertion holds. DataStore uses real `Dispatchers.IO` (no virtual time in E2E). |

**Process death = cold start from persisted state.** A Hilt test has exactly one `SingletonComponent`, and Hilt has no public API to rebuild it. (Rebuilding it reflectively needs three private touches in 2.60.1, namely `component`, `onComponentReadyRunner` and `tryToCreateComponent()`, so that approach was rejected.) A real new process holds nothing but the files on disk, so the harness reproduces that state instead. `seedPreferences(file) { store -> … }` opens a standalone Preferences DataStore on `context.preferencesDataStoreFile(file)` with its own scope, runs the block, then `cancelAndJoin`s the scope, which releases DataStore's single-instance lock. It throws `IllegalStateException` if the graph was already touched. E2E-7 seeds `focus_state` through T2's store (T2 keeps `FocusStateStore` constructible from a `DataStore<Preferences>`), adds one OFF zen rule of ours to `NotificationManager` (the leftover from the "previous process"), then calls `startApp()` and asserts FOCUS, the rule active, exactly one rule, and the notification shown. E2E-8 seeds the same way and calls `reboot()` instead of `startApp()`. T2 tests survival across store re-creation at unit level, and T4 tests rule adoption at unit level. **Self-test (T1):** `scanTagDirect(ACTIVATE)` → `assertMode(FOCUS)`. Seeding the same file twice works, which proves the lock is released. Seeding after a graph access throws.

Assumption for E2E-6 (handed to T5): the service remembers the last foreground package from its window events and re-checks it on the FREE→FOCUS edge, with `rootInActiveWindow` only as a fallback. That keeps the scenario drivable via `openApp(pkg)` before `scanTag(ACTIVATE)`.

##### R7. Files T1 creates

`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `.gitignore`, `.github/workflows/ci.yml`, `scripts/setup-android-sdk.sh`; `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/lint.xml`; `app/src/main/AndroidManifest.xml`, `res/xml/accessibility_service_config.xml`, `res/values/{strings,themes,colors}.xml`, `res/drawable/ic_launcher_{foreground,background}.xml`, `res/mipmap-anydpi/ic_launcher{,_round}.xml`; main sources `FocusTagApp`, `MainActivity`, `di/{AppModule,TimeModule,CoroutinesModule,Qualifiers,DeviceClock,AppStartRunner}`, `ui/theme/{Color,Theme,Type}`, `ui/nav/{FocusTagNavHost,Routes,StartViewModel}`, `ui/common/PermissionBanner`, `ui/status/StatusDestination` (placeholder), `ui/setup/SetupDestination` (placeholder), the 4 `Contracts.kt`, the per-layer modules and placeholders and stubs from R4; tests `testing/*` (R5), `testing/di/{TestTimeModule,TestNfcGatewayModule}`, `e2e/{FocusTagE2E,HarnessEntryPoint,SystemGrant}`, `e2e/HarnessSelfTest`, `SmokeTest` (JVM), `ui/nav/NavigationTest` (Robolectric Compose: Setup start, Status ↔ Setup once paired via the placeholder repo), `HiltGraphTest` (`@HiltAndroidTest` launching `MainActivity`). DECISIONS: D-07 (Android 17 changes), and a correction to D-01/D-06 (`android-37.0`, Kotlin 2.3.21).

##### R8. Risks / open questions

* **Cold-start seeding** (R6) relies on DataStore releasing its file lock when the scope completes (DataStore ≥ 1.1). The harness self-test guards this.
* **Maven Central rate limiting (HTTP 429 seen through the proxy)** when Robolectric downloads android-all (~170 MB) on a cold cache. Mitigation: retry, keep the `~/.m2` cache, CI caching. If it persists, pin `robolectric.dependency.repo.url`.
* **Kotlin 2.3.21 instead of 2.4.20.** This is a deliberate compatibility choice (R1). Revisit when KSP/Dagger ship 2.4-based releases.
* **`compileSdk` DSL for minor SDK levels** (`android-37.0`): verified only by the first build. Fallback in R1.
* **Robolectric SDK 37** is new (android-all built July 2026). Compose or `MessageQueue` issues → `sdk=36` fallback in `robolectric.properties` (target stays 37).
* `PermissionItem` holds `Intent`s, which have no structural `equals`. Tests compare `action`/`component`, not the item.
* Global `@TestInstallIn` means every Hilt test sees `FakeNfcGateway`/`FakeClock`. That's intended (hardware edges); a layer test that needs something else uses `@BindValue`.

##### R9. Implementation notes (T1, as built)

* Robolectric SDK 37 on JDK 21 needs extra test-JVM flags, and Espresso 3.7.0 is pinned (D-08). Compose tests use the `junit4.v2` rule factories: v1 is deprecated, and v2 uses `StandardTestDispatcher`, so call `waitForIdle()` where effects must settle.
* The harness lives in `e2e/` and is split to respect the file-size limit: `FocusTagE2E` (DSL), `HarnessEntryPoint`, `HarnessTags` (`A`/`B` pairings, URI builder), `SystemGrant`, `ZenRules` (OS-side zen rule access; it lifts the shadow's policy-access check the way the OS would) `Polling` (`idleMainLooper`, `retryUntilPasses`, each attempt bounded by `withTimeout`), `TagScans` and `StartedActivities`. Also `HarnessSelfTest`, which asserts only harness-side facts so that it holds with both placeholders and real layers. Every Act/Assert call closes the `seedPreferences` window. `@After` cancels the `@ApplicationScope` via `testing/cancelApplicationScope(context)`, and any Hilt test that touches the graph should do the same.
* The harness spells out `"android.nfc.action.NDEF_DISCOVERED"` instead of importing `android.nfc` (R2.4).
* `ui/theme/Type.kt` is not created (YAGNI: default Material typography).
* `AppStartRunner` logs and swallows a throwing hook (expected failures must be typed inside the hook).
* `res/xml/data_extraction_rules.xml` excludes all app data from backup/transfer (pairings and the zen rule id are device-bound).
* Remaining lint warnings: `NewerVersionAvailable` (Kotlin pin, D-02), `UnnecessaryRequiredFeature` (NFC is required by design), and `UnsafeProtectedBroadcastReceiver` on the `BootReceiver` stub. T2 resolves the last one by checking `intent.action`.
* Local only: this container's proxy gets HTTP 429 from `repo.maven.apache.org`. Builds here use `--init-script` with Google's Maven Central mirror (`maven-central.storage-download.googleapis.com/maven2`). The repository itself keeps plain `mavenCentral()`.

---

### T2 — Focus core: state machine, store, engine, boot · status: `done`
**Goal:** Correct, idempotent FREE/FOCUS logic that persists across kills and reboots and drives the effects.
**Owns:** `focus/**` except `Contracts.kt` (incl. `focus/di/FocusModule.kt`, `focus/boot/BootReceiver.kt`, `focus/notification/FocusNotifier.kt`), `res/values/strings_focus.xml`, `res/drawable/ic_focus_notification.xml`, `test/.../focus/**`.
**Depends on:** T1. **Decisions:** D-34, D-40–D-45.
**Subtasks:**
1. `FocusStateMachine.transition(state, role)` (pure, exhaustive `when`).
2. `FocusStateStore` (Preferences DataStore): mode, session start, daily totals map (prune > 30 days), corruption handler → FREE.
3. `FocusStats`: current session duration and today's total including the live session, with midnight split (D-42). Exposed as a `Flow` that ticks every second while FOCUS.
4. `FocusEngine` implements `FocusController` + `FocusStateReader`: `onTagScanned(role)` serialised with a `Mutex`, so concurrent scans can't race; persist, then `reconcile()`. Returns an outcome (`Activated`, `Deactivated`, `NoChange`) for UI feedback.
5. `reconcile()`: FOCUS → `FocusEffects.enable()` + notifier show; FREE → `FocusEffects.disable()` + notifier cancel. Safe to call any number of times. Catches `SecurityException` from effects and records a "degraded" flag instead of crashing (D-35).
6. Zen re-assertion listener (D-34): a dynamically registered receiver in the app scope that calls `reconcile()` while FOCUS.
7. `BootReceiver` (`goAsync`) + `AppStartHook` contribution → `reconcile()`.
8. `FocusNotifier`: ongoing notification channel "Focus active" (D-44). It is a no-op if the notification permission is missing.

**Acceptance criteria:** all 4 (state × tag) combinations behave as specified; repeated scans don't change state or timestamps; state survives store re-creation (process death); reboot reconcile re-enables effects; today's total is correct across midnight; revoked permission → no crash, degraded flag set.
**Test strategy:** pure JUnit for the state machine and stats (FakeClock, midnight cases); Robolectric for the store (real DataStore in a temp dir), engine with `FakeFocusEffects` (verify call sequences, concurrency with 50 parallel scans), BootReceiver intent → reconcile, notifier. Slice E2E: boot broadcast and app start drive reconcile through the real graph.

#### Refinement notes (T2)

##### F1. Classes (package `io.github.fbarcalar.focustag.focus`, all `internal` unless a test/harness needs them)

| File | Type | Responsibility / signature |
|------|------|----------------------------|
| `FocusStateMachine.kt` | `object FocusStateMachine` + `data class Transition(val newState: FocusState, val outcome: ScanOutcome) { val changed get() = outcome != NO_CHANGE }` | Pure D-40: `fun transition(state: FocusState, role: TagRole, now: Instant): Transition`. Exhaustive `when` over `(state, role)`; `now` is used only for FREE+A. FOCUS+A keeps the original `since`. |
| `FocusReconciler.kt` | `fun interface FocusReconciler { suspend fun reconcile() }` | Narrow port used by the boot receiver and the start hook (ISP), so neither sees the controller, and the hook is JVM-testable with a lambda. |
| `FocusEngine.kt` | `@Singleton class FocusEngine @Inject constructor(store: FocusStateStore, effects: FocusEffects, notifier: FocusNotifier, statsSource: FocusStatsSource, clock: Clock) : FocusController, FocusStateReader, FocusReconciler` | `onTagScanned` and `reconcile` share one `Mutex`. Scan = read `store.current()` → `transition` → if changed, persist (`enterFocus(since)` / `enterFree(endedAt = now, zone = clock.zone)`) → `reconcileLocked()` → return outcome. **Every scan reconciles, even NO_CHANGE** (idempotent, and it self-heals effects, MC-09). `state = store.state`, `stats = statsSource.stats`, `effectsStatus` = private `MutableStateFlow(EffectsStatus())`. |
| (same file, private) | `reconcileLocked()` | `when (store.current())`: `Focus` → `effects.enable()` + `notifier.show(since)`; `Free` → `effects.disable()` + `notifier.cancel()`. The effects call is wrapped in `catch (SecurityException)` → `EffectsStatus(setOf(Effect.ZEN_RULE))` (defence in depth; T4 already maps it at its adapter). The returned status is stored in `effectsStatus`. |
| `store/FocusStateStore.kt` | `data class FocusSnapshot(val state: FocusState, val dailyTotals: Map<LocalDate, Duration>)` + `class FocusStateStore(private val dataStore: DataStore<Preferences>)` (public ctor: E2E-7/8 seed through it, R6) + `companion fun create(scope: CoroutineScope, produceFile: () -> File): FocusStateStore` | `val snapshot: Flow<FocusSnapshot>` (one consistent read for the stats), `val state: Flow<FocusState>` (`map` + `distinctUntilChanged`), `suspend fun current(): FocusState`, `suspend fun enterFocus(since: Instant)`, `suspend fun enterFree(endedAt: Instant, zone: ZoneId)`. `enterFree` does the read-modify-write inside one `edit {}`: adds the session split by day (F3) to the stored totals, prunes, and clears the session. `create` = `PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }, scope, produceFile)`; the module and the corruption test both use it, so the tested handler is the shipped one. |
| `store/FocusPreferences.kt` | `internal object FocusPreferences` | Pure codec (F2): keys + `fun decode(prefs): FocusSnapshot`, `fun MutablePreferences.writeFocus(since)`, `writeFree(totals)`. Kept apart so the store file stays small. |
| `stats/DailyTotals.kt` | pure top-level functions | `fun splitByDay(start: Instant, end: Instant, zone: ZoneId): Map<LocalDate, Duration>` (empty if `end <= start`); `fun Map<LocalDate, Duration>.plusSession(start, end, zone)`; `fun Map<LocalDate, Duration>.retainFrom(firstDay: LocalDate)`. Day boundaries via `LocalDate.atStartOfDay(zone)` (DST-safe). |
| `stats/FocusStatsSource.kt` | top-level pure `fun focusStats(snapshot: FocusSnapshot, now: Instant, zone: ZoneId): FocusStats` + `class FocusStatsSource @Inject constructor(store: FocusStateStore, clock: Clock) { val stats: Flow<FocusStats> }` | `focusStats`: `currentSession = (now - since).coerceAtLeast(ZERO)` in FOCUS, ZERO in FREE; `todayTotal = stored[today] + splitByDay(since, now, zone)[today]`. The class only adds ticking (F3). |
| `notification/FocusNotifier.kt` | `interface FocusNotifier { fun show(since: Instant); fun cancel() }` + `class AndroidFocusNotifier @Inject constructor(@ApplicationContext context: Context) : FocusNotifier` | D-44 (F5). The interface lets the engine be tested on the plain JVM. |
| `reassert/ZenChangeSignals.kt` | `fun interface ZenChangeSignals { fun changes(): Flow<Unit> }` + `class BroadcastZenChangeSignals @Inject constructor(@ApplicationContext context)` | Thin adapter: `callbackFlow` registering one receiver (F4), `awaitClose { unregisterReceiver }`. It emits only for intents that pass the pure filter `isReassertionSignal(intent, currentFilter)` in the same file (F4), so our own activation echoes never reach the engine. |
| `FocusStartHook.kt` | `class FocusStartHook @Inject constructor(store: FocusStateStore, signals: ZenChangeSignals, reconciler: FocusReconciler) : AppStartHook` | One hook, two steps that both keep effects in line with the store: `onAppStart()` = `reconciler.reconcile()` (D-43, process death), then `store.state.map { it is FocusState.Focus }.distinctUntilChanged().flatMapLatest { focus -> if (focus) signals.changes() else emptyFlow() }.conflate().collect { reconciler.reconcile() }` (D-34). Long-running; the app scope cancels it. (Replaces the earlier split into `ReconcileOnAppStart` + `ZenReassertion`: one fewer class and binding, same tests.) |
| `boot/BootReceiver.kt` | `@AndroidEntryPoint class BootReceiver` | F6. |
| `di/FocusModule.kt` | `@Module @InstallIn(SingletonComponent)` interface + companion | `@Binds` `FocusEngine` → `FocusController`, `FocusStateReader`, `FocusReconciler`; `AndroidFocusNotifier` → `FocusNotifier`; `BroadcastZenChangeSignals` → `ZenChangeSignals`; `@Binds @IntoSet` `FocusStartHook` → `AppStartHook`. `@Provides @Singleton fun focusStateStore(@ApplicationContext ctx, @ApplicationScope scope, @IoDispatcher io) = FocusStateStore.create(CoroutineScope(scope.coroutineContext + io)) { ctx.preferencesDataStoreFile("focus_state") }`. **No `DataStore<Preferences>` is put in the graph**, so it can't collide with other layers' stores. |

Resources: `res/values/strings_focus.xml` (channel name/description, notification title "Focus active", text "Scan the living-room tag to exit") and **one new file, `res/drawable/ic_focus_notification.xml`** (monochrome vector small icon; lint flags a launcher icon used as a status-bar icon). The drawable is new, so it overlaps no one; it is added to T2's "Owns".

##### F2. DataStore schema (file `focus_state`, Preferences)

| Key | Type | Meaning |
|-----|------|---------|
| `mode` | String (`FocusMode.name`) | `FOCUS` / `FREE` |
| `session_start_epoch_ms` | Long | present only while FOCUS |
| `daily_totals` | Set<String> | completed focus ms per local date, one entry `"<yyyy-MM-dd>=<ms>"` per day (≤ 30 entries) |

* Decoding: `mode == FOCUS` **and** a start present → `Focus(since)`; anything else (missing file, unknown mode, FOCUS without a start) → `Free`. Same "fail to FREE" policy as the corruption handler (subtask 2). The state is written as both keys in one `edit {}`, so the inconsistent case is unreachable in practice.
* Totals: one fixed `stringSetPreferencesKey` instead of one dynamic key per day, so there's no key-prefix scanning and no per-key removal: decode the set to a `Map<LocalDate, Duration>` (malformed entries skipped), and `enterFree` writes back the whole pruned map. Retention: keep the 30 local dates `today-29 … today` (`retainFrom`); malformed entries disappear on that write.

##### F3. Stats and ticking (D-42)

* Midnight split happens **at session end** (`enterFree` adds `splitByDay(since, endedAt, zone)`) and **while live** (`focusStats` adds only today's slice of the live session). "Today" = `LocalDate.ofInstant(now, clock.zone)`; `DeviceClock` follows timezone changes.
* `FocusStatsSource.stats = store.snapshot.flatMapLatest { snap -> ticks(snap).map { focusStats(snap, clock.instant(), clock.zone) } }.distinctUntilChanged()`:
  * FOCUS: `ticks` emits immediately and then every 1 s (`delay(1.seconds)`).
  * FREE: emits immediately, then again at the next local midnight or after an hour, whichever comes first (`delay(minOf(untilNextMidnight, 1.hours))`), so a FREE screen left open overnight drops yesterday's total and a clock or zone change is picked up within an hour. No per-second work while FREE.
* Cold flow; it ticks only while collected (Status screen). Coroutine `delay` (virtual in `runTest`) drives the cadence; the value always comes from the injected `Clock`.

##### F4. Concurrency and re-assertion

* One `Mutex` in `FocusEngine` serialises scans and reconciles, so persistence and effects can't interleave (50 parallel scans → exactly one transition). DataStore's `edit` is atomic, and `enterFree` does its read-modify-write inside it. Readers (`state`, `stats`) never take the mutex.
* Every long-lived collector runs in `AppStartHook`s on `@ApplicationScope` (R2.2). No `GlobalScope`, no `runBlocking`.
* **Zen re-assertion (D-34/D-35):** `BroadcastZenChangeSignals` registers one receiver (`ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`; the sender is the system uid, which may deliver to non-exported receivers) for `NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED` (package-targeted at the rule's owner, so a dynamic receiver gets it), `ACTION_INTERRUPTION_FILTER_CHANGED` (registered receivers only) and `ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED` (the last one lets a re-grant recreate the rule and a revoke update the degraded flag promptly, D-35/MC-10). It is registered **only while FOCUS** (`flatMapLatest`) and is unregistered on FREE or on scope cancel. Each signal → `reconcile()` → `FocusEffects.enable()`, which owns the "is it active? else FALSE→TRUE / recreate" logic (T4). `conflate()` collapses bursts into at most one pending reconcile.
* **No amplification of our own echoes.** Our own activation (and T4's FALSE→TRUE) fires these broadcasts too. `isReassertionSignal` drops the ones that can only mean "on": a status change with `EXTRA_AUTOMATIC_ZEN_RULE_STATUS == AUTOMATIC_RULE_STATUS_ACTIVATED`, and a filter change while `currentInterruptionFilter != INTERRUPTION_FILTER_ALL` (DND is still on; if another mode masks ours being off, the status broadcast reports it). Everything else (deactivated/disabled/removed/unknown status, a missing extra, filter back to ALL, an access change) is a signal. So a re-assertion costs at most one extra reconcile, which is a no-op as long as `enable()` is (F9). Reading `currentInterruptionFilter` needs no permission.
* **Ordering:** the receiver registers asynchronously after the FOCUS state is persisted. Tests that send a zen broadcast right after a scan first wait until the receiver is registered (`shadowOf(app).registeredReceivers` contains the action); E2E-10 needs the same wait.
* In practice the bound accessibility service keeps the process (and so the receiver) alive. Gaps after a process kill are covered by the next app start, boot or scan.

##### F5. Notifier (D-44, D-07)

* Channel `focus_active`, `IMPORTANCE_LOW`, created idempotently on each `show`. Notification: `setOngoing(true)`, `setOnlyAlertOnce(true)`, `setSilent(true)`, `CATEGORY_STATUS`, `setWhen(since)` + `setUsesChronometer(true)` (a live session timer at no cost), no actions. The content intent is `PendingIntent.getActivity(context, 0, launchIntent, FLAG_IMMUTABLE)` with `launchIntent = packageManager.getLaunchIntentForPackage(packageName)` (explicit component, already `NEW_TASK`; `MainActivity` is `singleTop`, so a tap brings the existing task forward), so `focus` doesn't import `MainActivity` and never opts into background activity starts (D-07; the tap itself grants the launch). `FLAG_IMMUTABLE` is required since target 31; nothing ever updates the intent, so no `FLAG_UPDATE_CURRENT`. The lookup is nullable: if it is null the notification is posted without a content intent (no `!!`).
* `show` is a no-op when `POST_NOTIFICATIONS` isn't granted or `areNotificationsEnabled()` is false (explicit `checkSelfPermission`, which also satisfies lint `MissingPermission`). A `SecurityException` is caught at this adapter. `cancel` always runs. A fixed notification id.

##### F6. Boot receiver (D-43)

`onReceive`: inject with `@Inject lateinit var` fields. Hilt injects a receiver inside the generated `Hilt_BootReceiver.onReceive`; the class extends `BroadcastReceiver()` in source (whose `onReceive` is abstract, so no `super` call compiles) and the Hilt Gradle plugin's bytecode transform inserts the injecting super call. `BootReceiverTest` running the real graph proves the fields are set. Return unless `intent.action` is `BOOT_COMPLETED` or `MY_PACKAGE_REPLACED` (this fixes lint `UnsafeProtectedBroadcastReceiver`). Otherwise `val pending = goAsync()`, then `appScope.launch { reconciler.reconcileLogged() }.invokeOnCompletion { pending.finish() }` (`reconcileLogged` logs any non-cancellation failure instead of letting it crash the process through the handler-less app scope), with an injected `FocusReconciler` and `@ApplicationScope CoroutineScope`. The reconcile that `FocusTagApp.onCreate` runs at boot as well is harmless (mutex + idempotent).

##### F7. Placeholder replacement

Delete `focus/PlaceholderFocusEngine.kt`; rewrite `focus/di/FocusModule.kt` (F1) and the body of `focus/boot/BootReceiver.kt`. `HiltGraphTest`'s "controller and reader are the same instance" still holds (one `@Singleton FocusEngine`). `testing/FakeFocusEngine` (T1) stays for the T5–T7 VM tests. No change to `Contracts.kt`, the manifest or T1 files.

##### F8. Tests (`app/src/test/.../focus/**`)

Plain JUnit (JVM; DataStore Preferences is pure JVM, so the store and the engine need no Robolectric; it runs on `TemporaryFolder` + `TestDataStores`):
* `FocusStateMachineTest`: the 4 (state × role) cases with outcomes; FOCUS+A keeps `since` (not `now`); FREE+B stays `Free`.
* `DailyTotalsTest`: same-day session; a session across midnight split in two; a multi-day session; the DST day (Europe/Madrid 2026-10-25 has 25 h); `end <= start` → empty; `plusSession` adds to existing days; `retainFrom` keeps exactly 30 days.
* `FocusStatsTest` (the pure `focusStats`): FREE → current ZERO and today = stored; FOCUS same day; a live session started yesterday counts only today's slice; stored + live sum; a clock behind `since` → ZERO; a timezone move changes "today".
* `FocusStatsSourceTest` (`runTest` + Turbine + `FakeClock`): FOCUS emits each second as the clock advances; FREE emits once and again after midnight; FREE→FOCUS switches cadence.
* `FocusStateStoreTest`: empty → `Free`; `enterFocus` persists; **state survives store re-creation** (cancel the scope, reopen the file); `enterFree` adds split totals and prunes; a garbage file through `FocusStateStore.create` → `Free`, no throw; unknown `mode` / FOCUS without a start → `Free`; a malformed `daily_totals` entry is skipped and dropped on the next `enterFree`.
* `FocusEngineTest` (`FakeFocusEffects`, local `FakeFocusNotifier`, `FakeClock`, real store): each of the 4 cases (outcome, persisted state, effects call list, notifier calls); a double A scan keeps `since` and only re-enables; a double B scan leaves today's total unchanged; B after 25 min → today's total 25 min; across midnight → split; `reconcile()` twice → two identical idempotent calls; **process death**: a new engine over the re-opened FOCUS store → `reconcile()` → `enable` + `show(since)`; degraded status from effects → `effectsStatus.isDegraded`, and a later healthy reconcile clears it; effects throwing `SecurityException` → no throw, `failed = {ZEN_RULE}`; **50 parallel scans** (`Dispatchers.Default`): all A → exactly one `ACTIVATED` and one persisted `since`; a mix of A and B → the outcomes alternate consistently (`#ACTIVATED − #DEACTIVATED` is 1 if the final state is FOCUS, else 0) and the effects call list has one entry per scan (no interleaving).
* `FocusStartHookTest` (`MutableSharedFlow` signals, counting `FocusReconciler` lambda): start → exactly one reconcile in FREE and in FOCUS; a signal while FOCUS → reconcile; while FREE → none; FOCUS→FREE unsubscribes (`subscriptionCount == 0`); a burst while a reconcile is in progress → at most one queued reconcile.

Robolectric:
* `BroadcastZenChangeSignalsTest`: each of the 3 actions (sent with `setPackage`) emits; a status change with `STATUS_ACTIVATED` and a filter change while the filter isn't `ALL` don't; a status change without the extra does; the receiver is unregistered after collection is cancelled (`ShadowApplication.registeredReceivers`).
* `AndroidFocusNotifierTest`: with `POST_NOTIFICATIONS` granted → one ongoing notification on a low-importance channel, no actions, a non-null immutable content intent targeting our launch activity, chronometer `when == since`; `show` twice → still one notification; `POST_NOTIFICATIONS` denied or notifications disabled → nothing posted, no throw; `cancel` removes it.
* `BootReceiverTest` (`@HiltAndroidTest`, real graph, `cancelApplicationScope` in `@After`; observable = the notification, because effects are the system placeholder; that `reconcile` calls `enable` is covered by `FocusEngineTest`): in FOCUS with the notification cleared, `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` → the notification is back; an unrelated action → nothing.

Slice E2E `focus/FocusSliceE2ETest.kt` (extends `FocusTagE2E`, §2.2). The effects are still `PlaceholderFocusEffects` (no-op), so the observable is the **Focus notification** (asserted locally via `ShadowNotificationManager`). These assertions stay true once T4 lands.
1. `scanTagDirect(ACTIVATE)` → FOCUS + notification; `advanceClock(25.minutes)`; `scanTagDirect(DEACTIVATE)` → FREE, no notification, `stats.first().todayTotal == 25.minutes`.
2. Cold start: `seedPreferences("focus_state") { FocusStateStore(it).enterFocus(t0) }` → `startApp()` → FOCUS, notification shown, `currentSession == now - t0` (process death, D-41).
3. Reboot: same seed → `reboot()` (it cancels notifications and doesn't run `startApp`) → notification shown again via `BootReceiver`.
4. Re-assertion path: `startApp()`, `scanTagDirect(ACTIVATE)`, wait until the zen receiver is registered (F4), cancel all notifications, `turnZenRuleOffExternally()` (its broadcast carries no status extra, so it passes the filter) → notification restored (proves broadcast → reconcile).
5. A double scan of A keeps the session start (`state.first() == Focus(t0)`).

Cross-layer scenarios E2E-1…13 aren't T2's (T2 merges first in G2); later merges write them.

##### F9. Risks and concerns

* **Re-assertion loop (contract expectation on T4):** `FocusEffects.enable()` must be a no-op when our rule is already `STATE_TRUE`. Otherwise its own FALSE→TRUE fires a DEACTIVATED status / filter-ALL broadcast (which T2's filter must pass, since it can't tell it from a user switch-off) → reconcile → FALSE→TRUE … forever. T2's filter (F4) drops the "on" echoes and `conflate()` limits the rate, but neither can break such a loop. This fits the frozen KDoc ("idempotent"); T4 should test it.
* **Hilt binding collision across layers:** if T3/T4/T5 each `@Provides` an unqualified `DataStore<Preferences>`, the merged graph fails with duplicate bindings. T2 avoids putting one in the graph; the other layers should do the same or qualify it.
* **Dynamic receiver lifetime:** it exists only while the process lives (F4). If MC-07 shows gaps, the fix is a manifest receiver for `ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED` (the system targets it at the owner package, so it is exempt from implicit-broadcast limits; verify on device first). That would be a T1 manifest change; not requested now.
* **Disk errors:** an `IOException` from DataStore propagates out of `onTagScanned` (the contract promises only "no throw for permission problems"). T3's `NfcTriggerActivity` should treat a throw as "no change" feedback.
* `goAsync` budget is ~10 s; reconcile is a few binder calls, so no timeout wrapper (YAGNI).
* Robolectric delivering `BOOT_COMPLETED` to a Hilt `@AndroidEntryPoint` manifest receiver under `HiltTestApplication` is assumed (the harness self-test already sends it). If it fails, `BootReceiverTest` creates the receiver directly and calls `onReceive`.
* The harness's `@After` cancels the app scope while a `goAsync` coroutine may still run, or before it starts; `invokeOnCompletion { finish() }` runs in both cases, so the pending result is always finished.

##### F10. Implementation notes (T2, as built)

* `FocusStartHook` depends on `FocusStateReader.state` (the contract) instead of `FocusStateStore`, so its test runs on `FakeFocusEngine`; the behaviour is the same.
* `LocalDate.ofInstant` needs API 34 (minSdk is 33), so `stats/DailyTotals.kt` has `localDateOf(instant, zone)` (`atZone(zone).toLocalDate()`), used everywhere.
* `NotificationManager.AUTOMATIC_RULE_STATUS_ACTIVATED` is API 35. It is an inlined constant, so `@SuppressLint("InlinedApi")` applies; on API 33/34 the system never sends that status.
* The engine doesn't log the `SecurityException` it maps (no `android.util.Log`, so it stays a plain JVM unit).
* Test helpers `focus/FocusNotifications.kt` (`focusNotificationShown`, `clearNotifications`) are available to the cross-layer E2E scenarios that check the D-44 notification (E2E-1, E2E-7, E2E-8). `BootReceiverTest` extends `FocusTagE2E`, using it only as a Hilt test base.

---

### T3 — NFC layer: gateway, validation, pairing, tag writing, background trigger · status: `done`
**Goal:** Tags can be paired/written, and scans with the app closed are validated and forwarded to the engine.
**Owns:** `nfc/**` except `Contracts.kt` (incl. `nfc/di/NfcModule.kt`, `nfc/NfcTriggerActivity.kt`), `res/values/strings_nfc.xml`, `test/.../nfc/**`.
**Depends on:** T1. **Decisions:** D-10–D-16.
**Subtasks:**
1. `NdefCodec`: build the message (URI + AAR) and parse `focustag://toggle/<tagId>` strictly (scheme, host, single UUID path segment, case rules).
2. `TagValidator` (pure): `(ScannedTag, pairings) -> TagScanResult.Valid(role) | Unknown | UidMismatch | Malformed`.
3. `TagPairingStore` (DataStore): pair(role, uid, tagId), reset(role), resetAll, Flow of pairings. Rejects pairing the same UID to both roles.
4. `AndroidNfcGateway`: availability/enabled state, `enableReaderMode(activity, callback)` / `disable`, and `writePairing(tag, role)`: generate UUID, write NDEF (`Ndef` or `NdefFormatable`), verify read-back, then persist. Errors: read-only, too small, IO lost → typed failure.
5. `NfcTriggerActivity`: parse `EXTRA_TAG` + `EXTRA_NDEF_MESSAGES` into `ScannedTag` → validate → `FocusController.onTagScanned` → toast ("Focus on" / "Free time" / "Already in focus" …) → `finish()`. Unknown tags finish silently.

**Acceptance criteria:** unknown tag, wrong UID, malformed URI, foreign scheme and an unpaired role are all ignored; a valid A/B scan reaches the controller exactly once per intent; re-pairing invalidates the old tagId; write failures leave the existing pairing untouched; no `android.nfc` types outside `AndroidNfcGateway`/`NfcTriggerActivity`.
**Test strategy:** JUnit table tests for the codec and validator; Robolectric for the store and for `NfcTriggerActivity` (build an intent with a mocked/shadowed `Tag` + `NdefMessage`, assert calls on a fake `FocusController` via `@BindValue`); `FakeNfcGateway` for pairing-flow tests. Slice E2E: NDEF intent → controller via the harness.

#### Refinement notes (T3)

##### N1. Classes (package `nfc`; `android.nfc.*` only in `AndroidNfcGateway*.kt` and `NfcTriggerActivity.kt`, R2.4)

| File | Responsibility / signature |
|------|-----------------------------|
| `FocusTagUri.kt` | Pure `object FocusTagUri { fun build(tagId: String): String; fun parseTagId(uri: String): String? }`. This is the URI half of "NdefCodec". It's a regex on the whole string, with no `android.net.Uri`, so it runs on the plain JVM. Rules: scheme `focustag` and host `toggle` are case-insensitive (RFC 3986; `NdefRecord.toUri()` already lower-cases the scheme). There must be exactly one path segment, a canonical 8-4-4-4-12 hex UUID (any case, returned lower-case). No userinfo, port, query, fragment, trailing slash or extra segment. `build` writes `UUID.toString()` (lower case). |
| `AndroidNfcGatewayNdef.kt` | Everything NDEF-specific on the Android side (the `NdefMessage` half of "NdefCodec" plus the I/O adapter; one file instead of two, ≲ 90 lines). `internal fun focusMessage(uri, packageName): NdefMessage` (`NdefRecord.createUri` + `createApplicationRecord`, URI first so it drives dispatch, D-10). `internal fun uris(messages: List<NdefMessage>): List<String>`: each record's `toUri()`, **skipping `TNF_EXTERNAL_TYPE` records** (checked in android-all 17: `toUri()` turns an external-type record, i.e. our AAR, into `vnd.android.nfc://ext/android.com:pkg`, it does *not* return null), nulls dropped. `internal fun Tag.toScannedTag(messages: List<NdefMessage>) = ScannedTag(id.toUidHex(), uris(messages))` and `internal fun ByteArray.toUidHex()` (upper case), shared by `readTag` and reader mode. `internal fun Tag.ndefTarget(message: NdefMessage): NdefTarget?` returns `Ndef.get(tag)` → `NdefTarget` (connect/`use`, `writeNdefMessage`, then `ndefMessage` (a fresh read, not `cachedNdefMessage`) for the read-back), else `NdefFormatable.get(tag)` → a target with `maxSize = null` that calls `format(message)` and has `readBackUris() = null`, else `null` (→ `NOT_NDEF`). `FormatException`, `SecurityException` (stale tag) and `IllegalStateException` (connect conflict) are rethrown as `IOException` at this adapter boundary (§2.1). |
| `TagValidator.kt` | Also declares `TagScanResult` (its only producer; R3 says it lives next to the validator): `sealed interface TagScanResult { data class Valid(val role: TagRole); data object Unknown; data object UidMismatch; data object Malformed }`. `object TagValidator { fun validate(tag: ScannedTag, pairings: Map<TagRole, TagPairing>): TagScanResult }` is pure. Order: no URI parses (`firstNotNullOfOrNull(FocusTagUri::parseTagId)`) → `Malformed`; no pairing has that `tagId` → `Unknown` (this also covers an unpaired role); the pairing's `uidHex` ≠ scanned `uidHex` → `UidMismatch`; otherwise `Valid(pairing.role)` (D-12). |
| `TagPairingStore.kt` | `class TagPairingStore(dataStore: DataStore<Preferences>) : PairingRepository`. It's built with a plain constructor (no `@Inject`), so a test can hand it a temp-dir store. `pairings` maps the prefs to `Map<TagRole, TagPairing>` with `distinctUntilChanged`, and a role with only one of its two keys counts as unpaired. `save` runs inside **one** `edit {}`: if the other role's stored UID equals `pairing.uidHex`, it leaves the prefs unchanged and returns `UidUsedByOtherRole`; otherwise it writes both keys and returns `Paired`. Saving the same role again replaces the old pairing, which invalidates the old tagId. `reset(role)` removes both keys; `resetAll` clears the store. |
| `NfcTagWriter.kt` | `class NfcTagWriter @Inject constructor(gateway: NfcGateway, repository: PairingRepository) : TagWriter`. `pair(tag, role)`: (1) `repository.pairings.first()`; if the **other** role's `uidHex == tag.scanned.uidHex` → `UidUsedByOtherRole`, with **no write** (the contract ordering); (2) `tagId = UUID.randomUUID().toString()`; (3) `gateway.writeFocusTag(tag, FocusTagUri.build(tagId))`, where `Failed(r)` → `WriteFailed(r)` and the repository is untouched; (4) `repository.save(TagPairing(role, tagId, uid))`, whose result is returned as is. If the other role takes the same UID between steps 1 and 4, `save` still refuses it, and the written tag simply carries an unpaired id. |
| `TagWriteRules.kt` | The pure write/verify policy, behind a seam so it can be tested without hardware: `internal interface NdefTarget { val isWritable: Boolean; val maxSize: Int? /* null = unknown (formatable) */; fun write() /* throws IOException */; fun readBackUris(): List<String>? /* null = cannot re-read */ }` and `internal object TagWriteRules { fun write(target: NdefTarget, messageSize: Int, expectedUri: String): WriteResult }`. The checks run in order: not writable → `READ_ONLY`; `messageSize > maxSize` → `TOO_SMALL`; `IOException` during write or read → `IO_ERROR`; a read-back whose first URI ≠ `expectedUri` (or `null`) → `VERIFY_FAILED`; otherwise `Written`. |
| `AndroidNfcGateway.kt` | `@Singleton class AndroidNfcGateway @Inject constructor(@ApplicationContext context, @IoDispatcher io) : NfcGateway`. `availability`: a `callbackFlow` that emits the current state, then re-emits on `NfcAdapter.ACTION_ADAPTER_STATE_CHANGED` (registered with `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`, unregistered in `awaitClose`), `distinctUntilChanged`. A missing adapter → `UNAVAILABLE`. `readTag(intent)`: `EXTRA_TAG` (`IntentCompat.getParcelableExtra`), or `null` if it's absent; the URIs come from `EXTRA_NDEF_MESSAGES` (`IntentCompat.getParcelableArrayExtra`, always present on a real `NDEF_DISCOVERED`; absent → empty list, no fallback). `enableReaderMode`: `adapter.enableReaderMode(activity, callback, FLAG_READER_NFC_A or _B or _F or _V, null)`, **without** `FLAG_READER_SKIP_NDEF_CHECK`, so the platform runs its NDEF check, the `Ndef` tech is present and `cachedNdefMessage` is filled in. The callback wraps the tag in `internal class AndroidNfcTagHandle(val tag: Tag, override val scanned: ScannedTag)` with `scanned = tag.toScannedTag(listOfNotNull(Ndef.get(tag)?.cachedNdefMessage))` (a blank or formatable tag → empty URIs). `disableReaderMode` is a no-op when there's no adapter. `writeFocusTag`: `require(tag is AndroidNfcTagHandle)` (anything else is a programmer error), then `withContext(io) { TagWriteRules.write(target, message.byteArrayLength, uri) }` (`tag.tag.ndefTarget(message) ?: return Failed(NOT_NDEF)`). |
| `TagScanProcessor.kt` | Also declares `enum class ScanFeedback(@StringRes val message: Int) { FOCUS_ON, FREE_TIME, ALREADY_FOCUS, ALREADY_FREE; companion fun of(role, outcome) }` (pure mapping: `ACTIVATED`→FOCUS_ON, `DEACTIVATED`→FREE_TIME, `NO_CHANGE`+ACTIVATE→ALREADY_FOCUS, `NO_CHANGE`+DEACTIVATE→ALREADY_FREE). `class TagScanProcessor @Inject constructor(repository: PairingRepository, controller: FocusController) { suspend fun process(scan: ScannedTag): ScanFeedback? }` validates against `pairings.first()`. `Valid(role)` → `controller.onTagScanned(role)` exactly once → feedback. Any other result → `null`, silently (D-12). No `android.util.Log`: it would also throw "not mocked" in the plain-JVM `TagScanProcessorTest` (no `isReturnDefaultValues`). |
| `NfcTriggerActivity.kt` | `@AndroidEntryPoint class NfcTriggerActivity : ComponentActivity()` (Hilt needs `ComponentActivity`; the T1 stub extends `Activity`). It injects `NfcGateway`, `TagScanProcessor` and `@ApplicationScope CoroutineScope`; no `android.nfc` import. `onCreate`: (1) `savedInstanceState != null` (re-created) → `finish()` and return: the first instance already launched the work; (2) `gateway.readTag(intent) ?: return finish()`; (3) `val work = appScope.async { processor.process(scan) }`, then `lifecycleScope.launch { work.await()?.let { Toast.makeText(applicationContext, getString(it.message), LENGTH_SHORT).show() }; finish() }`. **Why the activity waits instead of finishing at once:** the toggle (DataStore write + zen rule) runs on the app scope so a destroyed activity can't cancel it, but keeping the invisible translucent activity resumed until it's done (typically well under a second) keeps the process at foreground priority; a process with no visible component becomes cached and can be frozen (cached-apps freezer) or killed mid-toggle. The toast is shown from the main thread while still in the foreground. If the activity is destroyed before `await` returns, only the toast is lost, never the toggle. There's no `setContentView` (translucent theme), and `onNewIntent` isn't needed: with `taskAffinity=""` + `noHistory` + standard launch mode, each scan gets a new instance. |
| `di/NfcGatewayModule.kt` | Name and package unchanged. `@Binds AndroidNfcGateway → NfcGateway`. |
| `di/NfcModule.kt` | Becomes an `abstract class` with a `companion object`: `@Provides @Singleton fun pairingStore(@ApplicationContext, @ApplicationScope, @IoDispatcher): TagPairingStore` creates the DataStore privately (R2.2: `PreferenceDataStoreFactory.create(scope = CoroutineScope(appScope.coroutineContext + io), corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }) { preferencesDataStoreFile("tag_pairings") }`). `DataStore<Preferences>` itself isn't exposed, so it can't clash with other layers' stores. `@Binds TagPairingStore → PairingRepository`, `@Binds NfcTagWriter → TagWriter`. |
| deleted | `PlaceholderNfcGateway`, `PlaceholderPairingRepository`, `PlaceholderTagWriter`. |
| `res/values/strings_nfc.xml` | `nfc_scan_focus_on` "Focus on", `nfc_scan_free_time` "Free time", `nfc_scan_already_focus` "Already in focus", `nfc_scan_already_free` "Already in free time". |

##### N2. DataStore `tag_pairings` keys

`activate_tag_id`, `activate_uid`, `deactivate_tag_id`, `deactivate_uid` (strings), built from `TagRole.name.lowercase()`. A corrupt file is replaced with empty prefs, so both tags read as unpaired and the app routes to Setup, which is the safe outcome.

##### N3. Error handling

* Scans: every non-`Valid` result is ignored silently (no toast, no log), and a `null` from `readTag` just finishes. `FocusController` never throws for permission problems (contract).
* Writes: all expected failures are `WriteResult.Failed(reason)`. Framework exceptions are converted at the `AndroidNfcGatewayNdef` boundary. `TagLostException` is an `IOException` → `IO_ERROR`.
* **NdefFormatable path:** after `format(message)` the same `Tag` object still lacks the `Ndef` tech, so it can't be read back. That path therefore returns `VERIFY_FAILED` (D-10 forbids reporting an unverified write as success). The tag is now NDEF-formatted, so the user's second tap goes through the `Ndef` path and verifies. NTAG21x ship NDEF-formatted (D-16), so this path is rare. T7's error copy should say "try again".

##### N4. Tests (`test/.../nfc/`)

*JVM unit*
* `FocusTagUriTest`: a table covering valid, upper-case scheme/host/UUID (accepted and normalised), foreign scheme, wrong host, missing id, empty id, non-UUID, a UUID without dashes, two segments, trailing slash, query, fragment, port, userinfo, surrounding whitespace, plus a `build`→`parseTagId` round trip.
* `TagValidatorTest`: a table covering Valid A, Valid B, unknown id, UID mismatch, foreign URI, empty URI list, the focustag URI after a foreign one (still found), an unpaired role (empty map) → Unknown, and a stale id after a re-pair → Unknown.
* `TagWriteRulesTest` (a `FakeNdefTarget`): READ_ONLY, TOO_SMALL (and size == max fits), IO_ERROR on write, IO_ERROR on read, VERIFY_FAILED on a mismatch and on `null` read-back, Written. Also: write is not attempted when the tag is read-only or too small.
* `ScanFeedbackTest`: all 6 (role × outcome) combinations.
* `TagScanProcessorTest` (`FakeFocusEngine` + `InMemoryPairingRepository`, a test fake in `test/.../nfc/`): valid A/B → `FakeFocusEngine.scans == listOf(role)` and the matching feedback; Unknown/UidMismatch/Malformed → `scans` empty, `null`.
* `NfcTagWriterTest` (`FakeNfcGateway` + the in-memory repo): a UID owned by the other role → `UidUsedByOtherRole` and `gateway.writes` empty; success → the written URI parses to the saved tagId, a UUID; a write failure → `WriteFailed(reason)` and the old pairing is unchanged; re-pairing the same role yields a new tagId; re-pairing the same tag for its own role is allowed.

*Robolectric*
* `TagPairingStoreTest` (`TestDataStores` + `TemporaryFolder`): an empty store emits `{}`; save then emit; `UidUsedByOtherRole` leaves the prefs unchanged; save for the same role replaces; `reset`/`resetAll`; a role with a partial key is ignored; the pairing survives store re-creation (cancel the scope, reopen).
* `AndroidNfcGatewayTest` (no Hilt). `Tag` is built via `ReflectionHelpers.callStaticMethod(Tag::class.java, "createMockTag", byte[] uid, int[] {TagTechnology.NFC_A}, Bundle[] {…}, 0L)` (a hidden but present static in android-all 17; `ShadowNfcAdapter.createMockTag()` is the fallback). `NdefMessage`/`NdefRecord` are real framework code under Robolectric. Cases: `readTag` with `EXTRA_TAG` + `EXTRA_NDEF_MESSAGES` (built with the real `focusMessage`) → upper-case UID and exactly `[uri]` (the AAR is skipped, which guards the `TNF_EXTERNAL_TYPE` rule); no `EXTRA_TAG` → `null`; no messages → empty URIs. `focusMessage` has the URI record first and an AAR with our package, and round-trips through `uris`. `availability`: `ShadowNfcAdapter.setNfcHardwareExists(false)` → UNAVAILABLE; `setEnabled(false/true)` + a broadcast of `ACTION_ADAPTER_STATE_CHANGED` → DISABLED/ENABLED (Turbine). Reader mode enable/disable → `shadowOf(adapter).isInReaderMode`, and `dispatchTagDiscovered(mockTag)` invokes `onTag` with the matching `scanned.uidHex` (empty URIs: the mock tag has no `Ndef` tech). `writeFocusTag` on a tag with no NDEF techs → `NOT_NDEF` (the real write I/O is covered by `TagWriteRulesTest` and MC-01).
* `NfcSliceE2ETest extends FocusTagE2E` (the slice, driven through `NfcTriggerActivity` via the harness, works with placeholder or real focus). Processing is asynchronous, so toast checks run inside `eventually { }` and count only once the trigger activity `isFinishing` (it finishes only after its single job completed, so a later count can't grow); the test keeps its own `Robolectric.buildActivity` helper for that, mirroring `deliverScan` (T1-owned). Cases: `pairTags()`; `scanTag(A)` → FOCUS and **exactly one** toast, "Focus on" (`ShadowToast.shownToastCount()` / `getTextOfLatestToast()`, which proves one processing per intent); `scanTag(A)` again → one more toast, "Already in focus"; `scanTag(B)` → FREE, "Free time"; `scanUnknownTag` / `scanTagWithWrongUid` / `scanForeignUri` → FREE, no toast, activity finished; scan without `pairTags()` → ignored; an intent with no tag (`enqueueRead(null)`) → the activity is finishing and nothing changes; an activity created with a non-null `savedInstanceState` (`create(Bundle())`) → finishing, the enqueued read is not consumed and the mode is unchanged. Pairing slice: `TagWriter` is taken from an nfc-test `@EntryPoint`; after `pairTags()`, `pair(FakeTagHandle(A uid), ACTIVATE)` → `Paired` with a new tagId; a scan of the old `HarnessTags.A` URI → FREE and no toast (re-pair invalidates); then `deliverScan` of the newly written URI → FOCUS.
* Each Hilt test calls `cancelApplicationScope` in `@After` (R9), which the harness does already.
* Before Review: `grep -rln 'android\.nfc' app/src/main` lists only `nfc/AndroidNfcGateway*.kt` (R2.4; `AndroidNfcGatewayTest` is the only test that imports it).

*Cross-layer E2E at Integrate:* T3 merged second (right after T2, merge order by readiness), so it wrote **E2E-2** and **E2E-3**. E2E-1/-4 pass to T4 and E2E-6 to T5 (see §2.2).

##### N5. Risks

* `Tag.createMockTag` is `@hide`. Reflection works on android-all 17 (checked: `createMockTag(byte[], int[], Bundle[], long)`); fallback `ShadowNfcAdapter.createMockTag()` (no custom UID).
* `ShadowNfcAdapter.setEnabled` sends no broadcast, so tests send `ACTION_ADAPTER_STATE_CHANGED` themselves.
* Real `Ndef` I/O can't run under Robolectric (it needs an `INfcTag` binder). The seam `NdefTarget` keeps that adapter ≲ 40 lines; it's covered by MC-01.
* Toast: shown before `finish()`, from the main thread, while the translucent activity is still resumed, so the API 30+ background-toast limits don't apply; MC-02 confirms on device. If the scan's work outlives the activity (re-creation), the toast is dropped by design, not the toggle.
* The translucent activity stays resumed for the duration of `onTagScanned` (persist + reconcile). If MC-02 shows a visible flicker or delay, that is still preferable to finishing first and running the toggle in a cached, freezable process.
* **Deviation from the T3 test strategy:** there's no `@BindValue FocusController` activity test. Overriding it would mean `@UninstallModules(FocusModule)`, coupling the test to T2's module contents. Exactly-once is proven by the toast count in the slice E2E (including the re-creation case) plus `TagScanProcessorTest`.
* **Contract note, no change requested:** `WriteFailure` has no "tap again" value, so the formatable path maps to `VERIFY_FAILED` (N3).

---

### T4 — System layer: DND + grayscale mode, fallback, permission checker · status: `done`
**Goal:** `FocusEffects` turns DND + grayscale on/off through one AutomaticZenRule, with an optional secure-settings fallback and a live permission checklist.
**Owns:** `system/**` except `Contracts.kt` (incl. `system/di/SystemModule.kt`), `res/values/strings_system.xml`, `test/.../system/**`.
**Depends on:** T1. **Decisions:** D-30–D-35, D-47, D-25.
**Subtasks:**
1. `ZenRuleController`: find or create the rule (adopt an existing rule we own via `getAutomaticZenRules()` to avoid duplicates; no persisted id, see S4); build the policy (alarms only, see S2) + `ZenDeviceEffects` grayscale; `activate()` / `deactivate()` via `setAutomaticZenRuleState`; `isActive()`; FALSE→TRUE re-assert; recreate if deleted.
2. `SecureSettingsGrayscale`: `isGranted()`; `enable()` saves the previous two values then writes them; `disable()` restores them; no-op without the grant.
3. `SystemFocusEffects` implements `FocusEffects` (zen rule + fallback if the user enabled it), and reports a per-effect status.
4. `AndroidPermissionChecker`: live `Flow<List<PermissionItem>>` for NFC enabled, Accessibility service enabled (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` + `AccessibilityManager` state listener), notification policy access, POST_NOTIFICATIONS, battery optimisation exemption, grayscale capability (API ≥ 35 && policy access) and the optional WRITE_SECURE_SETTINGS grant (with the adb command string). Each item has an `Intent` to its system screen (accessibility → also an App-info intent for "Allow restricted settings"). Re-evaluated on `refresh()` (called on ON_RESUME) and on relevant broadcasts.
5. `GrayscaleFallbackSettings` (DataStore toggle).

**Acceptance criteria:** activate/deactivate are idempotent; no duplicate rules after repeated calls or process death; `SecurityException` is surfaced as a status, not a crash; fallback restores the exact previous values; every checklist item has a correct status and a resolvable intent; the adb command contains the real `applicationId`.
**Test strategy:** Robolectric with `ShadowNotificationManager` (verify rule creation, state calls, device effects, duplicate prevention); fallback tested against Robolectric `Settings.Secure` with the permission granted/denied via `ShadowApplication`; permission checker tests per item; intent resolution assertions. Slice E2E: `FocusEffects` enable/disable through the real graph.

#### Refinement notes (T4)

##### S1. API facts checked (android-37.0 stubs + `api-versions.xml`, Robolectric 4.17 `ShadowNotificationManager` bytecode)

* API 35+: `AutomaticZenRule.Builder(name, conditionId)` with `setType(TYPE_OTHER)`, `setConfigurationActivity`, `setInterruptionFilter`, `setZenPolicy`, `setDeviceEffects`, `setTriggerDescription`; `ZenDeviceEffects.Builder().setShouldDisplayGrayscale(true)`; `NotificationManager.getAutomaticZenRuleState(id)`; `Condition(uri, summary, state, source)` with `SOURCE_USER_ACTION`. On API 33/34 (minSdk 33) only the API 29 constructor `AutomaticZenRule(name, owner, configActivity, conditionId, ZenPolicy, filter, enabled)` and `Condition(uri, summary, state)` exist, and the rule state can't be read.
* **Decision (KISS): zen effects are API 35+ only.** Below 35 the controller does nothing and returns `Unsupported` (no legacy constructor, no second code path, no `sdk=34` test needing an extra android-all download). Reasons: the target device runs Android 16; the 33/34 path couldn't apply grayscale anyway; and since the state can't be read there, `enable()` couldn't be a no-op on an already active rule, which re-opens the broadcast feedback loop (S2, `SystemFocusEffects`). minSdk 33 still installs and runs: NFC toggling, blocking and the optional secure-settings grayscale fallback work, and Status shows the effects as degraded. One gate, `@ChecksSdkIntAtLeast(api = 35) fun zenRulesSupported(sdkInt: Int = Build.VERSION.SDK_INT)`, is used by both the controller and the checklist, so lint `NewApi` checks every call behind it. Recorded on D-30.
* `addAutomaticZenRule` / `getAutomaticZenRules` / `setAutomaticZenRuleState` (API 24) throw `SecurityException` without policy access. `ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED` is API 30 (T2 listens to it, D-34).
* `owner` must be a `ConditionProviderService`; we have none, so the rule sets **only `configurationActivity = MainActivity`** (correction to D-30's "owner/configuration activity"). The rule is ours when `conditionId == ZEN_CONDITION_ID` (`focustag://zen/focus`) and the config activity is in our package (the harness `ZenRules` filter matches too).
* The daltonizer keys are `@hide`, so they are string literals: `accessibility_display_daltonizer_enabled`, `accessibility_display_daltonizer`.
* Robolectric shadow: static rule/state maps reset per test. `getAutomaticZenRules()` returns **every** rule (the real OS returns only the caller's), so we filter. Rules are parcel-copied, so device effects round-trip. A never-set state reads `STATE_UNKNOWN` (2). Every call enforces policy access (default **denied**), and no broadcasts are sent. `ShadowPowerManager.setIgnoringBatteryOptimizations` and `ShadowSettings.ShadowSecure` (no permission enforcement) cover the rest. Only android-all 37 is cached locally.

##### S2. Classes (package `system`, every file ≤ 200 lines)

| File | Responsibility / signature |
|------|----------------------------|
| `zen/ZenRuleSpec.kt` | `class ZenRuleSpec @Inject constructor(@ApplicationContext ctx)`: `fun newRule(): AutomaticZenRule` (Builder + `TYPE_OTHER` + `INTERRUPTION_FILTER_PRIORITY` + policy + grayscale effects + `configurationActivity = MainActivity`), `fun condition(active: Boolean): Condition` (`SOURCE_USER_ACTION`), `fun isOurs(rule: AutomaticZenRule): Boolean`. Policy: `ZenPolicy.Builder().disallowAllSounds().allowAlarms(true).hideAllVisualEffects()` (in this order: the later call wins). **Alarms are allowed** (D-30 note): a personal focus mode that silences the morning alarm is a safety problem, and alarms are not a distraction source. Calls, messages, repeat callers, events, reminders, media and system sounds stay blocked. Name/trigger text from `strings_system.xml`. The same file holds the top-level `zenRulesSupported()` gate (S1). Every member is `@RequiresApi(35)` or sits behind that gate. |
| `zen/ZenRuleSelection.kt` | Pure: `data class OwnedRule(val id: String, val createdAtMs: Long)`, `data class RuleChoice(val keep: String?, val duplicates: List<String>)`, `fun chooseRule(rules: List<OwnedRule>): RuleChoice` (oldest wins, tie by id). It is separate because `AutomaticZenRule` can't be built in a plain JVM test, and dedupe is the riskiest logic. |
| `zen/ZenRuleController.kt` | `@Singleton`, wraps `NotificationManager`. `sealed interface ZenOutcome { Applied; AccessDenied; NotApplied; Unsupported }` lives in the same file (`NotApplied` = the user *disabled* the mode, or the state is still not TRUE after the re-assert; `Unsupported` = API < 35). `suspend fun activate(): ZenOutcome`, checked in this order: API < 35 → `Unsupported`; no policy access → `AccessDenied`; `ensureRule()` = list ours → `chooseRule` → remove duplicates → adopt `keep` or `addAutomaticZenRule(spec.newRule())` (also re-creates a deleted rule, D-35); rule `isEnabled == false` (user disabled the mode, R-T4-2) → `NotApplied` with no state call; **state already TRUE → `Applied` with no further NM write (strict no-op, see `SystemFocusEffects`)**; otherwise `setState(FALSE)` + `setState(TRUE)` (D-34 re-assert; `FALSE` is skipped when the state is already FALSE), then re-read: TRUE → `Applied`, else `NotApplied` (no retry inside the call). `suspend fun deactivate(): ZenOutcome`: API < 35 → `Unsupported`; no access → `AccessDenied`; find ours (never creates), set FALSE only if the state is TRUE; no rule → `Applied`. Every NM call goes through one private `nm { }` wrapper that catches **only** `SecurityException` → `AccessDenied` (revocation race; not `runCatching`, which would also swallow `CancellationException`). It runs on `@IoDispatcher`. Existing rules are adopted, **not updated** (Android 15 keeps user edits anyway). |
| `grayscale/DaltonizerSettings.kt` | Thin adapter over `Settings.Secure`: `data class DaltonizerValues(val enabled: String?, val mode: String?)`, `fun read(): DaltonizerValues`, `fun write(values): Boolean` (`putString`, so `null` restores "unset"; `SecurityException → false`, the only catch), `val GRAYSCALE = DaltonizerValues("1", "0")`. |
| `grayscale/DaltonizerSnapshotStore.kt` | Snapshot of the pre-FOCUS values in the system DataStore: `suspend fun saved(): DaltonizerValues?`, `suspend fun saveIfAbsent(values)`, `suspend fun clear()`. It persists across process death, so a FREE scan in a new process still restores. |
| `grayscale/SecureSettingsGrayscale.kt` | `fun isGranted()` (`checkSelfPermission(WRITE_SECURE_SETTINGS)`). `suspend fun enable(): FallbackOutcome`: not granted → `NotGranted`; `saveIfAbsent(read())` (a re-enable never overwrites the original with our own grayscale values), then `write(GRAYSCALE)` only if `read() != GRAYSCALE` ; `write` returning false → `NotGranted`. `suspend fun disable()`: no snapshot → `Applied` (no-op); not granted → `NotGranted` (snapshot kept for later); else `write(snapshot)`, and `clear()` only if that write succeeded. `sealed interface FallbackOutcome { Applied; NotGranted }`. |
| `grayscale/DataStoreGrayscaleFallbackSettings.kt` | Implements `GrayscaleFallbackSettings` on the system DataStore (default false). |
| `SystemFocusEffects.kt` | `@Singleton`, implements `FocusEffects`, with one `Mutex` (concurrent reconciles from boot/app-start/receiver can't create two rules or double-snapshot). `enable()`: `zen.activate()`, then `if (fallback.enabled.first()) grayscale.enable() else grayscale.disable()` (a fallback switched off mid-session is restored at the next reconcile). `disable()`: `zen.deactivate()` + `grayscale.disable()` (always, so values are restored even if the toggle was turned off). Non-`Applied` outcomes (incl. `Unsupported`: DND isn't on, so Status must not claim it is) map to `Effect.ZEN_RULE` / `Effect.GRAYSCALE_FALLBACK` in `EffectsStatus.failed`. Never throws for permission problems. **Feedback-loop rule:** T2 reconciles on `ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED`, `ACTION_INTERRUPTION_FILTER_CHANGED` and `ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED` while FOCUS, and a reconcile calls `enable()`. So `enable()` with our rule already TRUE (and the fallback already applied) must make **zero** zen state writes: any FALSE→TRUE there would fire a broadcast → reconcile → enable → broadcast, forever (`conflate()` in T2 only slows it). Likewise `disable()` writes nothing when the rule is already off and no snapshot exists. Tested in S6. |
| `permissions/PermissionStatusReader.kt` | Adapter, one `fun` per OS-read item: accessibility (parse `ENABLED_ACCESSIBILITY_SERVICES` with `ComponentName.unflattenFromString`; GRANTED if any entry is in our package, so no class reference into `blocker`), policy access (`isNotificationPolicyAccessGranted`), `POST_NOTIFICATIONS` / `WRITE_SECURE_SETTINGS` (`checkSelfPermission`), battery (`PowerManager.isIgnoringBatteryOptimizations(pkg)`), grayscale capability. Policy access and grayscale capability are both `UNSUPPORTED` when `!zenRulesSupported()` (S1): DND access is useless below 35, so it must not raise the banner there. |
| `permissions/PermissionActions.kt` | `fun actionFor(id): PermissionAction`, see S3. |
| `permissions/PermissionChangeSignals.kt` | `val changes: Flow<Unit>`: `callbackFlow` merging a `ContentObserver` on `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` and a receiver (`RECEIVER_NOT_EXPORTED`) for `ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED`; both unregister in `awaitClose` (R2.2). It replaces subtask 4's `AccessibilityManager` state listener, which fires on *any* service and only duplicates the observer. |
| `permissions/AndroidPermissionChecker.kt` | `@Singleton`, implements `PermissionChecker`. `items` is a `MutableStateFlow` built from `PermissionId.entries` (enum order). `refresh()` re-evaluates **synchronously** (deterministic in tests; about 6 cheap binder/settings reads on ON_RESUME). On the `@ApplicationScope` it collects `NfcGateway.availability` (NFC is live) and `PermissionChangeSignals.changes` → `refresh()`. The NFC value starts as `ENABLED` until the first emission, so no false banner flashes. |
| `di/SystemModule.kt` | `@Binds` the three contracts to the real classes. A companion `@Provides @Singleton @SystemDataStore DataStore<Preferences>` (file `system_settings`, scope `appScope.coroutineContext + io`, `ReplaceFileCorruptionHandler { emptyPreferences() }` like the other layers, R2.2). The qualifier `@SystemDataStore` is defined in the same file. The system layer never binds an **unqualified** `DataStore<Preferences>`, and its two consumers (`DaltonizerSnapshotStore`, `DataStoreGrayscaleFallbackSettings`) inject the qualified one. This matches the other layers (T2 keeps its store out of the graph, T3 doesn't expose its DataStore, T5 uses `@BlockListPreferences`), so the merged graph has no duplicate binding. |

The three `Placeholder*` files are deleted.

**Class split (reviewed, KISS):** every class above is either a thin framework adapter (`ZenRuleController`, `DaltonizerSettings`, `PermissionStatusReader`, `PermissionChangeSignals`), pure logic that needs a JVM test (`chooseRule`), a contract implementation, or the persistence of one concern (`DaltonizerSnapshotStore`, `DataStoreGrayscaleFallbackSettings`). `ZenRuleSpec` keeps the rule's shape out of the controller so both stay under the size limits. The only fold is the outcome type, which now lives in the controller file (it has no other user). No further merging: the grayscale trio would mix the `SecurityException` boundary, persistence and the snapshot policy in one class.

##### S3. Checklist items (`required` drives the Status banner)

| Id | Status source | Action | Required |
|----|---------------|--------|----------|
| NFC_ENABLED | `NfcGateway.availability`: ENABLED→GRANTED, DISABLED→MISSING, UNAVAILABLE→UNSUPPORTED | `OpenSettings([ACTION_NFC_SETTINGS])` | yes |
| ACCESSIBILITY_SERVICE | `Settings.Secure` string (above) | `OpenSettings([ACTION_ACCESSIBILITY_SETTINGS, ACTION_APPLICATION_DETAILS_SETTINGS package:<pkg>])` (D-25) | yes |
| NOTIFICATION_POLICY_ACCESS | `isNotificationPolicyAccessGranted`; `UNSUPPORTED` below API 35 | `OpenSettings([ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS])` | yes |
| POST_NOTIFICATIONS | `checkSelfPermission` | `RequestRuntime(POST_NOTIFICATIONS, ACTION_APP_NOTIFICATION_SETTINGS + EXTRA_APP_PACKAGE)` | yes |
| BATTERY_OPTIMIZATION_EXEMPTION | `isIgnoringBatteryOptimizations` | `OpenSettings([ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS package:<pkg>, ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS])`, `@SuppressLint("BatteryLife")` per D-47 | no (recommended: Focus works without it) |
| GRAYSCALE_CAPABILITY | SDK ≥ 35 && policy access (`UNSUPPORTED` below 35) | same as policy access | no (derived from DND access; not shown twice in the banner) |
| WRITE_SECURE_SETTINGS | `checkSelfPermission` | `AdbGrant("adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")` (`packageName` = `applicationId`, since there is no suffix) | no |

##### S4. DataStore `system_settings` keys

`grayscale_fallback_enabled` (Boolean), `daltonizer_snapshot_saved` (Boolean; it is needed because `null` previous values are legitimate), `daltonizer_prev_enabled` (String, absent = unset), `daltonizer_prev_mode` (String, absent = unset). **No zen rule id is persisted** (this replaces "persisted rule id" in subtask 1). The OS listing filtered by `isOurs` (condition id `focustag://zen/focus` **and** config activity in our package; confirmed by review: the condition id isn't user-editable, and the OS only lists the caller's rules anyway) is the source of truth: a stored id can go stale (the user deletes the mode, or revoking DND access makes the OS remove our rules), and adoption plus dedupe needs the listing anyway. This also covers E2E-7 (a leftover rule from a "previous process" is adopted and no second rule is created).

##### S5. Error handling

`SecurityException` (and nothing broader) is caught only in `ZenRuleController`'s `nm { }` wrapper and in `DaltonizerSettings.write` (adapter boundary) and becomes `AccessDenied` / `NotGranted` → `EffectsStatus.failed`. Missing access is checked up front, so the normal path never throws. Nothing else is caught; programmer errors propagate. `deactivate()` without access also reports `ZEN_RULE` failed. This is honest: we can't confirm the rule is off, although the OS normally deletes our rules on revocation.

##### S6. Tests

* **JVM:** `ZenRuleSelectionTest`: none → nothing kept; one → kept; several → oldest kept, the rest are duplicates; tie broken by id.
* **Robolectric `ZenRuleControllerTest`** (policy access granted via the shadow unless stated): first activation creates one rule with TYPE_OTHER, PRIORITY, our condition id, config activity `MainActivity`, grayscale device effects and a policy with calls/messages disallowed and **alarms allowed**; activation sets the state to TRUE; activating twice keeps one rule; **activating an already TRUE rule writes no state** (the shadow keeps no call log, so read its private `automaticZenRuleStates` map with `ReflectionHelpers.getStaticField` and check the stored `Condition` is the **same instance** before and after); deactivating an already FALSE rule writes no state (same check); a rule the user disabled (`isEnabled = false`) returns NotApplied and gets no state call; an existing rule of ours is adopted instead of creating a new one; duplicate rules of ours are reduced to one; other packages' / other condition ids' rules are untouched; a rule switched off externally is re-activated; a rule deleted externally is re-created; deactivation sets FALSE; deactivation without a rule creates nothing; without policy access activation returns AccessDenied and creates nothing; access revoked between check and call (shadow throws) → AccessDenied. **JVM `ZenSupportTest`:** `zenRulesSupported(34)` is false and `(35)` true. The `Unsupported` branch itself is one guarded line, covered by that test plus lint `NewApi`; no `sdk=34` Robolectric run.
* **Robolectric `SecureSettingsGrayscaleTest`** (temp DataStore, `shadowOf(app).grant/denyPermissions`): without the grant, enable returns NotGranted and settings are untouched; enable writes `1`/`0` and snapshots the previous values; disable restores the exact previous values, including previously **unset** keys; enabling twice keeps the original snapshot; disable without a snapshot is a no-op; the snapshot survives store re-creation and is restored by a new instance; disable after the grant is lost returns NotGranted and keeps the snapshot. (Settings writes don't feed T2's zen broadcasts, so the `read() != GRAYSCALE` guard is a cheap nicety, not a loop guard.)
* **Robolectric `DataStoreGrayscaleFallbackSettingsTest`:** off by default; the toggle persists across store re-creation.
* **Robolectric `SystemFocusEffectsTest`** (real controller and grayscale, temp DataStore): enable with access is healthy and the rule is active; enable without access fails ZEN_RULE only; fallback on without the grant fails GRAYSCALE_FALLBACK only; fallback on with the grant applies grayscale and disable restores it; a fallback switched off mid-session is restored on the next enable; disable switches the rule off and is healthy; 20 concurrent enables leave exactly one rule; **loop guard:** after a first `enable()`, a second `enable()` makes no zen state write (same-instance check), which is what T2's broadcast-driven reconcile relies on.
* **Robolectric `PermissionStatusReaderTest` / `AndroidPermissionCheckerTest`:** one test per item and status (NFC × 3 via `FakeNfcGateway`; accessibility enabled / other package's service only / empty; policy access; notifications; battery via `ShadowPowerManager`; grayscale capability GRANTED/MISSING on 37; secure settings; the below-35 `UNSUPPORTED` mapping of policy access and grayscale capability is the pure gate, tested in `ZenSupportTest`); items cover every `PermissionId` once in enum order with the S3 `required` flags; `refresh()` picks up a changed grant; an NFC flow change updates items without `refresh()`; the policy-access broadcast and the accessibility settings observer trigger re-evaluation.
* **Robolectric `PermissionActionsTest`:** action, data and extras of every intent (compared field-wise, R8); accessibility has the settings intent plus App info `package:io.github.fbarcalar.focustag`; the adb command equals the exact string with the real `applicationId`; each `OpenSettings` intent resolves against the matching activity registered in `ShadowPackageManager`. Real resolution on the Pixel is covered by MC-10/T7.
* **Slice E2E `system/SystemSliceE2ETest`** (`FocusTagE2E`, `scanTagDirect`, so it works with the placeholder or the real engine): `grant(NOTIFICATION_POLICY)` + ACTIVATE → `assertZenRuleActive(true)`, not degraded; then DEACTIVATE → `assertZenRuleActive(false)`; without DND access, ACTIVATE → FOCUS, `assertEffectsDegraded(true)`, no crash; `revoke`/`grant(ACCESSIBILITY_SERVICE)` and `grant(WRITE_SECURE_SETTINGS)` flip the matching items of the real `PermissionChecker`. `@After` cancels the app scope (harness).
* **Cross-layer E2E written by T4 at Integrate** (T4 merges after T2, so its merge completes these sets; E2E-1/4 need T3 and E2E-9 needs T6). One file each in `e2e/scenarios/`:
  * `E2E7ProcessDeathInFocusTest` (E2E-7): `seedPreferences("focus_state")` with T2's store in FOCUS; then, still before touching the graph, set policy access directly on the shadow (`grant()` would touch the graph via `refresh()`) and add one rule built with `ZenRuleSpec(app).newRule()` in state FALSE (the leftover of the "previous process"); `startApp()` → `assertMode(FOCUS)`, `assertZenRuleActive(true)`, exactly one rule of ours, Focus notification shown.
  * `E2E8RebootInFocusTest` (E2E-8): seed FOCUS the same way (no rule), set policy access on the shadow, `reboot()` → `assertZenRuleActive(true)`, exactly one rule, not degraded.
  * `E2E10ZenRuleExternallyOffTest` (E2E-10): `grant(NOTIFICATION_POLICY)`, `startApp()`, `scanTagDirect(ACTIVATE)`, `turnZenRuleOffExternally()` → `assertZenRuleActive(true)`, exactly one rule. A second `ZenRules.broadcastStatusChanged()`-style broadcast while the rule is active then leaves the stored `Condition` the same instance (as above), proving the real engine + real effects don't loop.

##### S7. Risks / open questions

* **R-T4-1** Whether `getAutomaticZenRuleState` reports a user's QS deactivation as FALSE, and whether FALSE→TRUE with `SOURCE_USER_ACTION` beats that override on Android 16, can't be tested on the JVM → MC-07. If it doesn't, `activate()` returns `NotApplied` and Status shows degraded instead of silently failing.
* **R-T4-2** A mode the user *disabled* (not just deactivated) in Settings can't be re-enabled by the app without overriding user settings. We report `NotApplied` and don't update the rule.
* **R-T4-3** On API 33/34 there is no DND or zen grayscale (S1 decision). Only the secure-settings fallback can grey the screen there. Acceptable: the target device runs Android 16.
* **R-T4-4** Toggling the fallback in Setup takes effect at the next reconcile (next scan/app start/boot), not instantly. This is acceptable because Setup is usually used in FREE. Instant application would need a `reconcile()` entry point in `focus` contracts (no contract change requested).
* **R-T4-5** Alarms are allowed through the mode (D-30 note); everything else is blocked. MC-06 checks both. Adopted rules aren't updated, so a rule created by an earlier build keeps its old policy until the user deletes the mode (none exists yet, since T4 is the first build that creates rules).
* **R-T4-7** Loop safety when the re-assert doesn't stick (`NotApplied`): the next reconcile tries FALSE→TRUE again. That only loops if the OS broadcasts for a request it then ignores; a no-op state change sends nothing. MC-07 records it, and T2's `conflate()` bounds the rate.
* **R-T4-6** `system` references `MainActivity` (T1, app root) for the config activity. It's a class reference, not an edit, and it's compile-checked.

##### S8. Implementation notes (T4, as built)

* The SDK gate lives in its own file, `zen/ZenSupport.kt` (`zenRulesSupported()` / `zenRulesSupportedOn(sdkInt)`). Putting it in `ZenRuleSpec.kt` made the JVM test load `Uri` statics.
* Test-only helpers: `system/zen/ZenTestSupport` (OS-side rule access plus the shadow's state-map read for the same-instance checks) and `system/grayscale/SystemStoreRule` (a temp-dir DataStore that can be closed and reopened to simulate process death).
* `AndroidPermissionChecker` builds the `PermissionAction`s once. `Intent` has no structural `equals`, so fresh intents would turn every `refresh()` into a new `StateFlow` emission.
* `SystemFocusEffects.enable()` applies the fallback before the zen rule. The order has no observable effect.
* **Not done from S6:** there are no `ShadowPackageManager` resolution assertions for the intents: registering an activity for each intent only to resolve it again proves nothing. Intents are checked field by field, and real resolution is MC-10/T7. The revocation race *is* tested (`ZenRuleControllerRevocationTest`, a custom shadow that reports access as granted while `getAutomaticZenRules` throws). `ZenRuleController` is stateless and isn't a `@Singleton`; `SystemFocusEffects` holds the mutex.
* **For integration:** E2E-7 must build its leftover rule with `ZenRuleSpec(app).newRule()` (condition id `focustag://zen/focus`, config activity in our package), or the controller won't adopt it. The DND-access broadcast and the accessibility observer are owned by `PermissionChangeSignals`. T2 needs only the zen broadcasts for D-34.
* **Integrate (as built):** rebased on T2 and T3. Scenarios E2E-1/4/7/8/10 live in `e2e/scenarios/E2E<n>…Test.kt` (named like T3's). Verified against the real engine: `FocusStartHook` re-asserts on an external switch-off, and a zen broadcast while the rule is on leaves the stored condition untouched (E2E-10), so there is no re-assert loop. E2E-9 now waits only for T6.

---

### T5 — Blocker: decision logic, accessibility service, blocking screen, app list · status: `todo`
**Goal:** While FOCUS, opening a blocked app immediately shows a blocking screen and returns the user home.
**Owns:** `blocker/**` except `Contracts.kt` (incl. `blocker/di/BlockerModule.kt`, `FocusAccessibilityService.kt`, `BlockingActivity.kt`, `ui` of the blocking screen inside `blocker/ui/`), `res/values/strings_blocker.xml`, `test/.../blocker/**`.
**Depends on:** T1. **Decisions:** D-20–D-25.
**Subtasks:**
1. `BlockDecider` (pure) incl. the always-allowed set (D-22) resolved by `AlwaysAllowedResolver` (default launcher, dialer, IMEs, SystemUI, Settings, self).
2. `BlockListStore` (DataStore `Set<String>`) implementing `BlockListRepository`, with `canRemove` gated on FREE (D-45).
3. `InstalledAppsRepository`: launchable apps (label, package, icon loader) sorted by label, excluding self; icons loaded lazily off the main thread.
4. `FocusAccessibilityService`: handles window events → decider → launch `BlockingActivity` (dedupe: don't relaunch if already showing for the same package within 500 ms) / fallback GLOBAL_ACTION_HOME; collects the focus state and, on the FREE→FOCUS edge, checks the current window (D-23).
5. `BlockingActivity`: full-screen Compose UI ("<App> is blocked during Focus", "Scan the living-room tag to exit"), back/“Go home” → launcher home intent; finishes itself if the state becomes FREE.

**Acceptance criteria:** blocked package in FOCUS → blocking screen; FREE → never blocks; always-allowed packages are never blocked even if listed; blocked app already in the foreground when Focus starts gets blocked; no infinite relaunch loop; removing from the list is impossible while FOCUS (adding works).
**Test strategy:** JUnit table tests for the decider; Robolectric for the store, the app repository (`ShadowPackageManager` with launcher activities), the service (`onAccessibilityEvent` with synthetic events, assert started intent via `ShadowApplication.nextStartedActivity`), and a Compose UI test for `BlockingActivity`. Slice E2E: accessibility event → blocking screen through the harness.

---

### T6 — Status screen · status: `in-progress`
**Goal:** Clear FOCUS / FREE TIME display with timers, hint and a permission warning, and no way to exit.
**Owns:** `ui/status/**`, `res/values/strings_status.xml`, `test/.../ui/status/**`.
**Depends on:** T1 (contracts), merged after T2 & T4 so it runs against real bindings. **Decisions:** D-45, D-46.
**Subtasks:**
1. `StatusViewModel`: combines `FocusStateReader` (state + stats flow) and `PermissionChecker` into `StatusUiState`. Calls `refresh()` on resume.
2. `StatusScreen`: large mode indicator (colour + text "FOCUS"/"FREE TIME"), session timer (FOCUS only, hh:mm:ss), today's total, hint "Scan the living-room tag to exit" in FOCUS (and "Scan the desk tag to focus" in FREE), warning banner listing revoked required permissions with a tap → Setup, entry to Setup.
3. Accessibility semantics (content descriptions, large text), and no mode-changing controls.

**Acceptance criteria:** the UI matches state within 1 s; banner appears/disappears with permission changes; no button or gesture changes the mode; timers survive configuration change.
**Test strategy:** ViewModel unit tests with fakes + Turbine; Compose UI tests (Robolectric) for both modes, banner on/off, and an assertion that no clickable node changes the mode.

#### Refinement notes (T6)

##### U1. Classes (package `ui.status`, all `internal` except the frozen entry point; every file ≤ 200 lines)

| File | Responsibility / signature |
|------|----------------------------|
| `StatusUiState.kt` | `sealed interface StatusUiState { data object Loading; data class Ready(val mode: FocusMode, val currentSession: Duration, val todayTotal: Duration, val missingPermissions: List<PermissionItem>, val failedEffects: Set<Effect>) }` plus the pure mapper `fun statusUiState(state: FocusState, stats: FocusStats, effects: EffectsStatus, items: List<PermissionItem>): StatusUiState.Ready`. `missingPermissions = items.missingRequired` (the contract helper; items are kept, not ids, because T1's `PermissionBanner` takes `List<PermissionItem>`; so `UNSUPPORTED` below API 35 and the non-required items never raise the banner). `failedEffects = effects.failed` **only in FOCUS**, empty in FREE: in FREE nothing should be on, and T4 reports `deactivate()` without DND access as failed (S5), which would be a false "not applied" warning; the permission banner already covers that case. `Loading` avoids a flash of "FREE TIME" before the store's first read (same idea as `StartDestination.Loading`). |
| `StatusViewModel.kt` | `@HiltViewModel class StatusViewModel @Inject constructor(reader: FocusStateReader, permissions: PermissionChecker) : ViewModel()`. `val uiState: StateFlow<StatusUiState> = combine(reader.state, reader.stats, reader.effectsStatus, permissions.items, ::statusUiState).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)`. `fun onResume() = permissions.refresh()`. No mode-changing dependency: `FocusController` is not injected (D-45 by construction). `WhileSubscribed(5 s)` keeps the upstream alive across a rotation; the timers are anyway derived from the persisted `since` by T2, so they survive configuration change and process death without any UI state. |
| `StatusDestination.kt` | Frozen `StatusDestination(onOpenSetup)`, `// PLACEHOLDER(T6)` body replaced: `hiltViewModel<StatusViewModel>()`, `collectAsStateWithLifecycle()`, `LifecycleResumeEffect(Unit) { viewModel.onResume(); onPauseOrDispose {} }` (subtask 1, "refresh on resume"), then `StatusScreen(state, onOpenSetup)`. `LifecycleResumeEffect(key1, …) { …; onPauseOrDispose { } }` exists in `lifecycle-runtime-compose` since 2.7; the pinned 2.11.0 has it (a key is mandatory). |
| `StatusScreen.kt` | Stateless `StatusScreen(state: StatusUiState, onOpenSetup: () -> Unit, modifier)`: `Scaffold` + `TopAppBar` (app name, Settings icon with the existing `R.string.action_open_setup` description, which `NavigationTest` relies on). Body: `Loading` → empty; `Ready` → a `verticalScroll` `Column` (so 200 % font scale never clips): `StatusWarnings`, `ModeIndicator`, `SessionTimers`, `ModeHint`. The screen has a single event, so it takes `onOpenSetup` instead of a one-case `onEvent` (KISS). |
| `StatusSections.kt` | `ModeIndicator(mode)`: full-width rounded `Surface`, `primaryContainer` in FOCUS / `surfaceVariant` in FREE, text "FOCUS" / "FREE TIME" in `displayMedium`; the text, not only the colour, carries the mode. Semantics: `heading()` + `liveRegion = Polite`, so TalkBack announces a mode change while the screen is open. `SessionTimers(mode, currentSession, todayTotal)`: "Current session" (FOCUS only) and "Today" rows, values in `headlineLarge` with tabular digits (`fontFeatureSettings = "tnum"`) so the ticking digits don't jitter. `ModeHint(mode)`: "Scan the living-room tag to exit" / "Scan the desk tag to focus". No clickable modifiers and no gesture detectors in this file. |
| `StatusWarnings.kt` | `StatusWarnings(missing: List<PermissionItem>, failedEffects: Set<Effect>, onOpenSetup)`: (1) T1's `PermissionBanner(missing, onClick = onOpenSetup)` as-is (count + "Tap to fix in Setup"); (2) directly under it, in the same error-container colour, a plain (not clickable) line naming them ("Missing: Do Not Disturb access, Accessibility service"; names in `strings_status.xml`, one per `PermissionId`, exhaustive `when`); (3) when `failedEffects` is non-empty, a notice "Do Not Disturb and grayscale are not active" (ZEN_RULE) / "Grayscale fallback could not be applied" (GRAYSCALE_FALLBACK), tapping to Setup (D-46: escapes are shown, not prevented). The banner is the single tap target for the permission part, so TalkBack does not meet two buttons doing the same thing. The notice has `Role.Button` and an `onClickLabel` ("Open setup"). T1's shell only prints a count; extending it to list names would be a T1 edit, so the names line lives here. |
| `DurationFormat.kt` | Pure `fun formatClock(d: Duration): String` → `"%02d:%02d:%02d"` from `d.coerceAtLeast(ZERO)` with **total** hours (25 h → `25:00:00`; sub-second truncated). Composable `spokenDuration(d)` builds the TalkBack text from plurals ("1 hour, 5 minutes", seconds only under one minute), set as the timer's `contentDescription`, because "01:05:03" is read as a time of day. |
| `StatusPreviews.kt` | `@Preview`s (light + dark via `uiMode`) for Loading, FREE healthy, FOCUS healthy, FOCUS with two missing permissions and ZEN_RULE failed, FOCUS at `fontScale = 2f`. Kept apart so the screen files stay small. |
| `res/values/strings_status.xml` | mode labels, timer labels, hints, effect notices, permission names, `plurals` for spoken hours/minutes/seconds, "Missing: %1$s". |

##### U2. Behaviour details

* **Within 1 s (AC):** T2's stats flow ticks every second in FOCUS from the injected `Clock`; the VM adds no delay or sampling. The mode comes from `state`, the timers from `stats`; both are fed by the same store snapshot, so after a scan they agree on the next emission.
* **No exit (D-45):** the only clickable nodes are the Settings icon, the permission banner and the effects notice, and all of them call `onOpenSetup`. No `pointerInput`, `swipeable` or back handler on the screen.
* **Banner:** appears/disappears with `PermissionChecker.items`, which T4 updates live (NFC flow, DND broadcast, accessibility observer) and on every `refresh()` from ON_RESUME.

##### U3. Tests (`test/.../ui/status/**`)

* **JVM `StatusUiStateTest`** (pure mapper): FREE → mode FREE, session ZERO passthrough; FOCUS carries the stats; only required MISSING items are listed (UNSUPPORTED and non-required MISSING are not); failed effects shown in FOCUS, hidden in FREE; healthy → empty sets.
* **JVM `DurationFormatTest`:** ZERO → `00:00:00`; 59 s; 1 h 5 min 3 s → `01:05:03`; 25 h → `25:00:00`; 999 ms truncated; negative → `00:00:00`.
* **JVM `StatusViewModelTest`** (`MainDispatcherRule`, Turbine, `FakeFocusEngine`, `FakePermissionChecker`): `uiState.value` is `Loading` before any collector; once collected it becomes `Ready(FREE)` (a local `awaitReady()` skips an initial `Loading`, so the test does not depend on whether Turbine sees the `stateIn` seed); FREE → FOCUS → FREE updates the mode; a stats emission updates the timers; a permission revoked/re-granted adds/removes the item; `effectsStatus` degraded in FOCUS shows the effects; `onResume()` calls `refresh()` exactly once.
* **Robolectric Compose `StatusScreenTest`** (`createComposeRule` v2, stateless screen, `waitForIdle`): FOCUS shows "FOCUS", the session and today timers and the exit hint, and not the desk hint; FREE shows "FREE TIME", today's total, the desk hint and no session timer; Loading shows neither mode label; banner + names shown when permissions are missing, absent otherwise; the effects notice shows in FOCUS degraded; clicking the banner calls `onOpenSetup`; **every node with a click action calls `onOpenSetup` and nothing else exists to click** (`onAllNodes(hasClickAction())`: click each, counter == node count, and the set is exactly {Settings, banner, notice}; the names line has no click action); the mode label is a heading with a polite live region; at `DeviceConfigurationOverride.FontScale(2f)` the today timer is still reachable (`performScrollTo().assertIsDisplayed()`); the timer has the spoken content description.
* **Robolectric Hilt `StatusConfigurationChangeTest`** (extends `FocusTagE2E` as a Hilt base, like T2's `BootReceiverTest`; real graph): paired, `scanTagDirect(ACTIVATE)`, `advanceClock(10.minutes)`, `ActivityScenario.launch(MainActivity)` (its own handle, because the harness keeps its scenarios private; closed in `@After`), timer `00:10:00` (the clock moved before the first collection, so the first stats emission already reads it; no tick is needed); `scenario.recreate()` → still "FOCUS" and `00:10:00` (AC "timers survive configuration change").

##### U4. Cross-layer E2E written by T6 at Integrate (T2, T3, T4 are merged, so both sets are complete). One file each in `e2e/scenarios/`:

* **`E2E11StatusReflectsScansTest`** (E2E-11, T2+T3+T6): `pairTags()`, `grant` NOTIFICATION_POLICY, POST_NOTIFICATIONS, ACCESSIBILITY_SERVICE (NFC is ENABLED by default) → `openMainUi()` → wait for "FREE TIME", no banner. `scanTag(ACTIVATE)` (real `NfcTriggerActivity` path) → "FOCUS" + exit hint within the harness timeout; `advanceClock(25.minutes)`, then `shadowOf(Looper.getMainLooper()).idleFor(1 s)` so T2's next 1 s tick fires (its `delay` runs in `viewModelScope` on the paused main looper, and the harness's `eventually` only runs tasks already due, so without this the screen keeps `00:00:00`) → session `00:25:00` on screen; `scanTag(DEACTIVATE)` → "FREE TIME", today `00:25:00`, no session timer. Then the no-exit check on the real screen: every clickable node is a Setup entry (`hasClickAction()` count equals the Settings icon only, since no banner is shown) and the mode is unchanged.
* **`E2E9DndRevokedMidSessionTest`** (E2E-9, T2+T4+T6): all grants, `pairTags()`, `startApp()` (runs `FocusStartHook`, which registers T2's zen receiver while FOCUS; under `HiltTestApplication` nothing else does, as in E2E-10), `openMainUi()`, `scanTagDirect(ACTIVATE)` (E2E-11 already covers the NFC path), `assertZenRuleActive(true)`, "FOCUS" with no warnings. Wait until T2's zen receiver is registered (F4; the same `registeredReceivers` check as E2E-10, copied locally because it is private there). Act: `revoke(NOTIFICATION_POLICY)` (shadow + `refresh()`), then `app.sendBroadcast(Intent(ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED).setPackage(app.packageName))`, as the OS does on revocation (the shadow sends no broadcasts, S1). Both dynamic receivers listen to this action: T2's `BroadcastZenChangeSignals` (`isReassertionSignal` → true → `reconcile()` → `enable()` fails → `ZEN_RULE` degraded) and T4's `PermissionChangeSignals` (→ `refresh()`, already done by `revoke`). So the banner proves T4's live checklist and the notice proves T2's reconcile. Assert: no crash, `assertMode(FOCUS)`, `assertEffectsDegraded(true)`, the screen still shows "FOCUS", the banner (count 1) and the name "Do Not Disturb access", and the effects notice. Second test (same file, MC-10 "re-granting restores"): after the revoke, `grant(NOTIFICATION_POLICY)` + the same broadcast → `assertEffectsDegraded(false)`, banner and notice gone, `assertZenRuleActive(true)`.
* Both update the §2.2 "Written by" column to **T6** with the file names.

##### U5. Risks / open questions

* `scanTag` launches `NfcTriggerActivity` while `MainActivity` is resumed; the empty compose rule should keep finding `MainActivity`'s hierarchy (the trigger activity has no Compose content and finishes). If not, E2E-11 falls back to waiting on `focusStateReader` first and then re-querying the UI.
* T7 may also name permissions in `strings_setup.xml`; duplicate wording across two owned files is accepted over a shared T1 resource (which would need a T1 edit).
* The FOCUS ticker leaves a delayed message on the main looper; Robolectric's paused-looper idle runs only due tasks, so `waitForIdle`/`eventually` do not spin on it (and tests that need a tick advance the looper explicitly, U4).
* `LifecycleResumeEffect` calls `refresh()` also on the first composition; `refresh()` is cheap and synchronous (S2), so no guard.

---

### T7 — Setup screen · status: `todo`
**Goal:** Pair/re-pair/reset tags, pick blocked apps, see and fix permissions.
**Owns:** `ui/setup/**`, `res/values/strings_setup.xml`, `test/.../ui/setup/**`.
**Depends on:** T1 (contracts), merged after T3, T4, T5. **Decisions:** D-14, D-25, D-33, D-45.
**Subtasks:**
1. `SetupViewModel`: pairing state machine (Idle → WaitingForTag(role) → Writing → Success/Error), pairings, block list, installed apps with search query, permissions, focus mode (for read-only gating).
2. Tag section: cards for Tag A and Tag B (paired/unpaired, short UID), "Pair"/"Re-pair"/"Reset" buttons (disabled in FOCUS with explanation); while waiting, enable reader mode via `DisposableEffect` on the hosting Activity and disable it on leave/pause; NFC-off state links to NFC settings.
3. Block-list section: search field + `LazyColumn` with icon, label, checkbox; unchecking disabled in FOCUS.
4. Permission checklist: rows with status chip + "Open settings" button launching the item's intent; notifications row uses the runtime permission launcher; secure-settings row shows the adb command with a copy button and a fallback toggle (enabled only when granted).
5. Re-check on ON_RESUME.

**Acceptance criteria:** the pairing flow works end-to-end against `FakeNfcGateway`, incl. timeout/cancel and write errors; search filters case-insensitively; FOCUS gating per D-45; every permission row opens the right intent; reader mode is never left enabled after leaving the screen.
**Test strategy:** ViewModel tests with fakes (pairing success/error/double-scan/cancel); Compose UI tests (Robolectric) for each section, the FOCUS-gated state, and the adb command text; Robolectric check that the reader mode enable/disable calls are balanced over lifecycle.

---

### T8 — Hardening, docs, final verification · status: `todo`
**Goal:** No leftovers, a full E2E matrix, user documentation.
**Owns:** `README.md`, `docs/MANUAL_CHECKS.md`, PLAN.md status updates, any E2E scenario from §2.2 still missing. A production fix found here goes back to the owning layer's task as a review-cycle item.
**Depends on:** T2–T7.
**Subtasks:**
1. Audit: every E2E scenario in §2.2 exists and passes. No `Placeholder*` classes remain. No `android.nfc` imports outside the allowed files. Standards in §2.1 are respected (separate reviewer subagent).
2. `README.md`: build (SDK script), install (`adb install -r`), permission walkthrough incl. "Allow restricted settings", DND access, battery, notifications, the optional adb `WRITE_SECURE_SETTINGS` command, pairing tags, recommended tags, a pointer to the manual checks, known limitations.
3. `docs/MANUAL_CHECKS.md` (the list in §5 expanded into steps with expected results).
4. Final `./gradlew assembleDebug lint test` on the integration branch, push, completion report.

**Acceptance criteria:** the audit is clean; the README covers every permission and command; all tasks are `done`.
**Test strategy:** the full suite; the audit.

## 4. Parallel groups

| Group | Tasks | Can run concurrently because | Gate after the group |
|-------|-------|------------------------------|----------------------|
| **G1** | T1 | Foundation | Full build + push |
| **G2** | T2, T3, T4, T5 | They depend only on T1 contracts; file ownership is disjoint (separate packages, `strings_<layer>.xml`, separate test dirs); each builds alone because T1 placeholders complete the DI graph (D-50) | Merge in order T2 → T4 → T5 → T3, full build after each merge + at the end, push |
| **G3** | T6, T7 | Both depend only on contracts + G2 bindings; disjoint `ui/status` vs `ui/setup` | Full build + push |
| **G4** | T8 | Needs everything | Final build, report |

Cross-layer E2E scenarios (§2.2) are written during each merge, so every group gate already runs every scenario its merged tasks unlock.

## 5. Manual device checks (Pixel 10a, cannot be automated)

* **MC-01** Pair Tag A and Tag B on the Setup screen; verify the tags contain `focustag://toggle/<uuid>` (e.g. with NFC Tools) and that pairing scans do **not** toggle focus.
* **MC-02** App closed (swiped away), phone unlocked: scan Tag A → toast + FOCUS; scan Tag A again → no change; scan Tag B → FREE.
* **MC-03** Scan an unrelated NFC tag / a tag copied with the same URI → nothing happens.
* **MC-04** In FOCUS: open each blocked app from the launcher, recents, a notification and split-screen → blocking screen, then home. Phone, launcher, Settings and FocusTag stay usable.
* **MC-05** Grayscale is visibly applied in FOCUS and removed in FREE (zen rule). Repeat with the secure-settings fallback toggle on, and confirm the previous colour-correction settings are restored.
* **MC-06** DND: send a test notification and a call from a non-starred contact in FOCUS → suppressed; FREE → delivered. An alarm set for one minute later still rings in FOCUS.
* **MC-07** In FOCUS, turn the mode off from Quick Settings / Settings → Modes → the app re-asserts it (D-34). Record both the `activate()` outcome (Status degraded or not) **and** whether the mode is actually back on (Quick Settings tile, DND icon, grayscale). Android may report the raw TRUE condition while a user override keeps the mode off, so the outcome alone isn't enough.
* **MC-08** In FOCUS, reboot the phone; after unlock, FOCUS, DND, grayscale and blocking are all active and the timers continue.
* **MC-09** Force-stop the app in FOCUS → after any tag scan or app launch, state and effects are consistent; the accessibility service re-binds.
* **MC-10** Revoke DND access / disable the accessibility service mid-session → no crash; the Status banner shows it; re-granting restores effects.
* **MC-11** Phone locked (screen on): scanning does nothing (expected, documented Android behaviour).
* **MC-12** Session timer and today's total look right over a session that crosses midnight (or by changing the device time).
