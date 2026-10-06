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
  nfc/Contracts.kt               (T1, frozen) NfcGateway, ScannedTag, PairingRepository, TagPairing,
                                            TagScanResult, TagWriter
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

E2E tests are **not** a final phase. T1 ships an E2E harness: `test/.../e2e/FocusTagE2E.kt`, a Robolectric + Hilt test base with a small DSL (`scanTag(role)`, `scanUnknownTag()`, `openApp(pkg)`, `reboot()`, `killProcess()`, `revoke(permission)`, `assertMode(...)`, `assertZenRuleActive(...)`, `assertBlockingShown(...)`). It fakes only the hardware/OS edges (`NfcGateway`, system manager shadows). Every task adds the scenarios it can drive **in the same commit series as the feature**:

* **Slice E2E** (written during Implement, owned by the task): the task's layer driven from its Android entry point (intent, broadcast, accessibility event, screen) with the other layers' placeholders/fakes.
* **Cross-layer E2E** (written during Integrate): each scenario below lists the tasks it needs. **The task whose merge completes that set writes it**, after rebasing on the integration head. The files live in `test/.../e2e/scenarios/<Scenario>Test.kt`, one file per scenario, so ownership never overlaps.

| Scenario | Needs |
|----------|-------|
| E2E-1 Desk tag while FREE → FOCUS persisted, zen rule active, notification shown | T2, T3, T4 |
| E2E-2 Double desk scan / living-room tag while FREE → no change | T2, T3 |
| E2E-3 Unknown tag, UID mismatch, foreign URI → ignored | T2, T3 |
| E2E-4 Living-room tag while FOCUS → FREE, zen rule off, today's total updated | T2, T3, T4 |
| E2E-5 FOCUS + open blocked app → blocking screen; allowed app → nothing | T2, T5 |
| E2E-6 Blocked app already in foreground when FOCUS starts → blocked | T2, T3, T5 |
| E2E-7 Process death in FOCUS → new graph, same DataStore → reconcile re-applies effects | T2, T4 |
| E2E-8 Reboot broadcast in FOCUS → effects re-applied | T2, T4 |
| E2E-9 DND access revoked mid-session → no crash, degraded status, banner on Status | T2, T4, T6 |
| E2E-10 Zen rule turned off externally while FOCUS → re-asserted | T2, T4 |
| E2E-11 Status screen reflects scans live (FREE → FOCUS → FREE), no exit control | T2, T3, T6 |
| E2E-12 Setup: pair A and B via fake gateway → app routes to Status; reset blocked in FOCUS | T2, T3, T7 |
| E2E-13 Setup: remove app from block list blocked in FOCUS, allowed in FREE | T2, T5, T7 |

## 3. Tasks

### T1 — Project skeleton, DI, navigation, contracts, CI · status: `in-progress`
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

**Acceptance criteria:** the three commands pass from a clean clone after `scripts/setup-android-sdk.sh`; the app launches to the Setup placeholder; navigation works in the Robolectric test; every contract has KDoc; the manifest contains every component listed above.
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

* `compileSdk { version = release(37) }`, `targetSdk { version = release(37) }`, `minSdk { version = release(33) }` (AGP 9 DSL; it resolves to `platforms/android-37.0`). Fallback if the DSL form fails: `compileSdk = 37`.
* `gradle.properties`: `org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8`, `kotlin.compiler.execution.strategy=in-process`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true` (disable if KSP/Hilt break it), `android.useAndroidX=true`. Unit tests: `maxHeapSize = "1g"`, `isIncludeAndroidResources = true`.
* Root `build.gradle.kts` declares the plugins with `apply false`: `com.android.application`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`, `com.google.devtools.ksp`, `com.google.dagger.hilt.android`. Putting KGP 2.3.21 on the classpath is what upgrades AGP's built-in Kotlin.
* **Android 17 target changes to record in DECISIONS (D-07):** static final fields can't be modified via reflection (no impact); lock-free `MessageQueue` (no reflection on it; Robolectric is unaffected); BAL hardening for `IntentSender`/`PendingIntent` (our only `PendingIntent` is the notification's content intent, which is user-initiated, and the blocker starts activities from an accessibility service, so there's no impact; T2 must not use `MODE_BACKGROUND_ACTIVITY_START_ALLOWED`); the large-screen orientation/resizability opt-out is removed (phone only, no impact). All of these apply only on Android 17 devices. The Pixel 10a runs 16.
* `scripts/setup-android-sdk.sh`: `ANDROID_HOME` defaults to `/opt/android-sdk` if it exists, else `$HOME/android-sdk`. The script is idempotent and installs only what is missing (cmdline-tools zip, the three packages, licences). It writes `local.properties` (`sdk.dir=…`). `/opt/android-sdk` is already provisioned in this container.
* CI (`.github/workflows/ci.yml`): ubuntu-latest, `actions/setup-java` (temurin 21), `gradle/actions/setup-gradle`, the SDK script, then `./gradlew assembleDebug lint test`. It caches `~/.m2/repository/org/robolectric` (Robolectric downloads android-all there).
* Lint (`app/lint.xml`): `abortOnError=true`, `warningsAsErrors=false`, no baseline. `GradleDependency`/`NewerVersionAvailable` stay as warnings. The manifest uses `tools:ignore="ProtectedPermissions"` on `WRITE_SECURE_SETTINGS`.

##### R2. Cross-cutting rules the contracts rely on

1. **Time:** inject only `java.time.Clock`. Production binds `DeviceClock`, a `Clock` whose `getZone()` returns `ZoneId.systemDefault()` on every call, so a timezone change is picked up. `FakeClock` is a `Clock` with a mutable instant and zone. This one dependency replaces the "Clock + ZoneId provider" pair in subtask 4.
2. **Process lifetime:** everything that lives as long as the process (DataStore instances, dynamically registered receivers, collectors) is bound to the `@ApplicationScope CoroutineScope` (`SupervisorJob() + Dispatchers.Default`) and releases on cancellation (`awaitClose` / `finally { unregister }`). Each DataStore is created in its layer's Hilt module with `PreferenceDataStoreFactory.create(scope = CoroutineScope(appScope.coroutineContext + ioDispatcher)) { context.preferencesDataStoreFile("<layer>") }`. **The `by preferencesDataStore` delegate is forbidden**: it's process-static, so it would survive the harness's `killProcess()` and break the single-instance check. File names: `focus_state`, `tag_pairings`, `block_list`, `system_settings`.
3. **Qualifiers** (`di/Qualifiers.kt`): `@ApplicationScope`, `@IoDispatcher`, `@DefaultDispatcher`. There's no main-dispatcher qualifier (ViewModels use `viewModelScope`).
4. **Dependency direction:** a layer depends only on other layers' `Contracts.kt`. `focus` defines `FocusEffects`, which `system` implements. `android.nfc.*` may be imported only in `nfc/AndroidNfcGateway*` and `nfc/NfcTriggerActivity` (checked by T8).
5. **Bindings:** each layer binds its contracts in `<layer>/di/<Layer>Module.kt` (`@InstallIn(SingletonComponent)`). The NFC hardware binding lives in its **own** module, `nfc/di/NfcGatewayModule.kt`, so tests can replace just the gateway.

##### R3. Contracts (frozen after T1; KDoc on every member in the real files)

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
    /** Rejects a UID already paired to the other role. */
    suspend fun save(pairing: TagPairing): PairingResult
    suspend fun reset(role: TagRole)
    suspend fun resetAll()
}

/** Pairing use case: new UUID → write → verify → save; old pairing untouched on failure. */
interface TagWriter { suspend fun pair(tag: NfcTagHandle, role: TagRole): PairingResult }

sealed interface TagScanResult {
    data class Valid(val role: TagRole) : TagScanResult
    data object Unknown : TagScanResult
    data object UidMismatch : TagScanResult
    data object Malformed : TagScanResult
}
```

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

##### R6. E2E harness (`e2e/FocusTagE2E.kt`, plus `e2e/HiltProcess.kt` ≲ 40 lines)

`@HiltAndroidTest abstract class FocusTagE2E` with rules `HiltAndroidRule` (order 0) and `createEmptyComposeRule()` (order 1). Scenario classes extend it and are annotated `@HiltAndroidTest @RunWith(AndroidJUnit4::class)`. The harness never caches injected objects. It reads them through `EntryPoints.get(app, HarnessEntryPoint::class.java)`, which exposes `FocusController`, `FocusStateReader`, `PairingRepository`, `BlockListRepository`, `PermissionChecker`, `FakeNfcGateway`, `FakeClock`, `AppStartRunner` and `@ApplicationScope CoroutineScope`, so every call after a restart reaches the new graph.

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
| `killProcess()` | see below |
| `reboot()` | `killProcess()`, then reset volatile OS state (our zen rules set to `STATE_FALSE`, all notifications cancelled), then `sendBroadcast(BOOT_COMPLETED, package=ours)` + `idle()` |
| `assertMode(m)` | `eventually { FocusStateReader.state.first().mode == m }` |
| `assertZenRuleActive(b)` | `NotificationManager.getAutomaticZenRules()` filtered to our package, `getAutomaticZenRuleState(id) == STATE_TRUE` (Robolectric 4.17 shadows both) |
| `assertBlockingShown(pkg)` / `assertNothingBlocked()` | `shadowOf(app).nextStartedActivity` → `BlockingActivity` with `EXTRA_BLOCKED_PACKAGE` |
| `assertEffectsDegraded(b)` | `FocusStateReader.effectsStatus.value.isDegraded` |
| `eventually(timeout = 5s) { }` / `idle()` | loops `shadowOf(mainLooper).idle()` + a short sleep until the assertion holds. DataStore uses real `Dispatchers.IO` (no virtual time in E2E). |

**`killProcess()` (simulated process death):** (1) destroy the activity scenarios and service controllers the harness holds; (2) `cancelAndJoin` the old `@ApplicationScope` job, which releases the DataStore file locks and unregisters receivers (R2.2); (3) **rebuild the Hilt SingletonComponent** through `HiltProcess.restart(app)`: reflectively set `TestApplicationComponentManager.component` to null and invoke its private `tryToCreateComponent()`. These are the only two reflective touches, isolated in one file and checked against Hilt 2.60.1 bytecode. The new graph uses the same `@TestInstallIn` modules and the same Robolectric `filesDir`, so the DataStore files persist. (4) Carry "outside world" state into the new fakes: the clock instant/zone and NFC availability. `NotificationManager` and `Settings` state survive by design, as on a real device. **Self-test (T1):** `scanTagDirect(ACTIVATE)` → FOCUS; `killProcess()` → `FocusStateReader` is a different instance and (with the in-memory placeholder) the mode is FREE again. That proves a fresh graph. T2 rewrites the self-test expectation to FOCUS when its store lands. **Fallback** if the reflection breaks on a Hilt upgrade: E2E-7/8 seed the DataStore before the first injection (`@Before`, using T2's store with a standalone scope), then start a single process.

Assumption for E2E-6 (handed to T5): the service remembers the last foreground package from its window events and re-checks it on the FREE→FOCUS edge, with `rootInActiveWindow` only as a fallback. That keeps the scenario drivable via `openApp(pkg)` before `scanTag(ACTIVATE)`.

##### R7. Files T1 creates

`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `.gitignore`, `.github/workflows/ci.yml`, `scripts/setup-android-sdk.sh`; `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/lint.xml`; `app/src/main/AndroidManifest.xml`, `res/xml/accessibility_service_config.xml`, `res/values/{strings,themes,colors}.xml`, `res/drawable/ic_launcher_{foreground,background}.xml`, `res/mipmap-anydpi/ic_launcher{,_round}.xml`; main sources `FocusTagApp`, `MainActivity`, `di/{AppModule,TimeModule,CoroutinesModule,Qualifiers,DeviceClock,AppStartRunner}`, `ui/theme/{Color,Theme,Type}`, `ui/nav/{FocusTagNavHost,Routes,StartViewModel}`, `ui/common/PermissionBanner`, `ui/status/StatusDestination` (placeholder), `ui/setup/SetupDestination` (placeholder), the 4 `Contracts.kt`, the per-layer modules and placeholders and stubs from R4; tests `testing/*` (R5), `testing/di/{TestTimeModule,TestNfcGatewayModule}`, `e2e/{FocusTagE2E,HarnessEntryPoint,HiltProcess,SystemGrant}`, `e2e/HarnessSelfTest`, `SmokeTest` (JVM), `ui/nav/NavigationTest` (Robolectric Compose: Setup start, Status ↔ Setup once paired via the placeholder repo), `HiltGraphTest` (`@HiltAndroidTest` launching `MainActivity`). DECISIONS: D-07 (Android 17 changes), and a correction to D-01/D-06 (`android-37.0`, Kotlin 2.3.21).

##### R8. Risks / open questions

* **Hilt component rebuild via reflection** (R6) is the only non-public API use. It's gated by the harness self-test, and the fallback is described above.
* **Maven Central rate limiting (HTTP 429 seen through the proxy)** when Robolectric downloads android-all (~170 MB) on a cold cache. Mitigation: retry, keep the `~/.m2` cache, CI caching. If it persists, pin `robolectric.dependency.repo.url`.
* **Kotlin 2.3.21 instead of 2.4.20.** This is a deliberate compatibility choice (R1). Revisit when KSP/Dagger ship 2.4-based releases.
* **`compileSdk` DSL for minor SDK levels** (`android-37.0`): verified only by the first build. Fallback in R1.
* **Robolectric SDK 37** is new (android-all built July 2026). Compose or `MessageQueue` issues → `sdk=36` fallback in `robolectric.properties` (target stays 37).
* `PermissionItem` holds `Intent`s, which have no structural `equals`. Tests compare `action`/`component`, not the item.
* Global `@TestInstallIn` means every Hilt test sees `FakeNfcGateway`/`FakeClock`. That's intended (hardware edges); a layer test that needs something else uses `@BindValue`.

---

### T2 — Focus core: state machine, store, engine, boot · status: `todo`
**Goal:** Correct, idempotent FREE/FOCUS logic that persists across kills and reboots and drives the effects.
**Owns:** `focus/**` except `Contracts.kt` (incl. `focus/di/FocusModule.kt`, `focus/boot/BootReceiver.kt`, `focus/notification/FocusNotifier.kt`), `res/values/strings_focus.xml`, `test/.../focus/**`.
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

---

### T3 — NFC layer: gateway, validation, pairing, tag writing, background trigger · status: `todo`
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

---

### T4 — System layer: DND + grayscale mode, fallback, permission checker · status: `todo`
**Goal:** `FocusEffects` turns DND + grayscale on/off through one AutomaticZenRule, with an optional secure-settings fallback and a live permission checklist.
**Owns:** `system/**` except `Contracts.kt` (incl. `system/di/SystemModule.kt`), `res/values/strings_system.xml`, `test/.../system/**`.
**Depends on:** T1. **Decisions:** D-30–D-35, D-47, D-25.
**Subtasks:**
1. `ZenRuleController`: find or create the rule (persisted rule id; adopt an existing rule we own via `getAutomaticZenRules()` to avoid duplicates); build the policy (nothing allowed) + `ZenDeviceEffects` grayscale; `activate()` / `deactivate()` via `setAutomaticZenRuleState`; `isActive()`; FALSE→TRUE re-assert; recreate if deleted.
2. `SecureSettingsGrayscale`: `isGranted()`; `enable()` saves the previous two values then writes them; `disable()` restores them; no-op without the grant.
3. `SystemFocusEffects` implements `FocusEffects` (zen rule + fallback if the user enabled it), and reports a per-effect status.
4. `AndroidPermissionChecker`: live `Flow<List<PermissionItem>>` for NFC enabled, Accessibility service enabled (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` + `AccessibilityManager` state listener), notification policy access, POST_NOTIFICATIONS, battery optimisation exemption, grayscale capability (API ≥ 35 && policy access) and the optional WRITE_SECURE_SETTINGS grant (with the adb command string). Each item has an `Intent` to its system screen (accessibility → also an App-info intent for "Allow restricted settings"). Re-evaluated on `refresh()` (called on ON_RESUME) and on relevant broadcasts.
5. `GrayscaleFallbackSettings` (DataStore toggle).

**Acceptance criteria:** activate/deactivate are idempotent; no duplicate rules after repeated calls or process death; `SecurityException` is surfaced as a status, not a crash; fallback restores the exact previous values; every checklist item has a correct status and a resolvable intent; the adb command contains the real `applicationId`.
**Test strategy:** Robolectric with `ShadowNotificationManager` (verify rule creation, state calls, device effects, duplicate prevention); fallback tested against Robolectric `Settings.Secure` with the permission granted/denied via `ShadowApplication`; permission checker tests per item; intent resolution assertions. Slice E2E: `FocusEffects` enable/disable through the real graph.

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

### T6 — Status screen · status: `todo`
**Goal:** Clear FOCUS / FREE TIME display with timers, hint and a permission warning, and no way to exit.
**Owns:** `ui/status/**`, `res/values/strings_status.xml`, `test/.../ui/status/**`.
**Depends on:** T1 (contracts), merged after T2 & T4 so it runs against real bindings. **Decisions:** D-45, D-46.
**Subtasks:**
1. `StatusViewModel`: combines `FocusStateReader` (state + stats flow) and `PermissionChecker` into `StatusUiState`. Calls `refresh()` on resume.
2. `StatusScreen`: large mode indicator (colour + text "FOCUS"/"FREE TIME"), session timer (FOCUS only, hh:mm:ss), today's total, hint "Scan the living-room tag to exit" in FOCUS (and "Scan the desk tag to focus" in FREE), warning banner listing revoked required permissions with a tap → Setup, entry to Setup.
3. Accessibility semantics (content descriptions, large text), and no mode-changing controls.

**Acceptance criteria:** the UI matches state within 1 s; banner appears/disappears with permission changes; no button or gesture changes the mode; timers survive configuration change.
**Test strategy:** ViewModel unit tests with fakes + Turbine; Compose UI tests (Robolectric) for both modes, banner on/off, and an assertion that no clickable node changes the mode.

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
* **MC-06** DND: send a test notification and a call from a non-starred contact in FOCUS → suppressed; FREE → delivered.
* **MC-07** In FOCUS, turn the mode off from Quick Settings / Settings → Modes → the app re-asserts it (D-34); record the result.
* **MC-08** In FOCUS, reboot the phone; after unlock, FOCUS, DND, grayscale and blocking are all active and the timers continue.
* **MC-09** Force-stop the app in FOCUS → after any tag scan or app launch, state and effects are consistent; the accessibility service re-binds.
* **MC-10** Revoke DND access / disable the accessibility service mid-session → no crash; the Status banner shows it; re-granting restores effects.
* **MC-11** Phone locked (screen on): scanning does nothing (expected, documented Android behaviour).
* **MC-12** Session timer and today's total look right over a session that crosses midnight (or by changing the device time).
