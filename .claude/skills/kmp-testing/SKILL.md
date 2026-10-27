---
name: kmp-testing
description: |
  Testing patterns for KMP (Android-first) - ViewModel and repository unit tests with kotlin.test, Turbine, UnconfinedTestDispatcher, fake repositories, SavedStateHandle, and Compose UI tests. Use this skill whenever writing or reviewing tests for ViewModels, repositories, use cases, or Compose screens. Trigger on phrases like "write a test", "unit test the ViewModel", "test a repository", "Turbine", "fake repository", "UnconfinedTestDispatcher", "runTest", "ComposeTestRule", or "in-memory database".
---
 
# KMP Testing

Shared logic is tested in `commonTest` (`shared/src/commonTest`, `composeApp/src/commonTest`). Run shared tests with `./gradlew :shared:allTests` (Android host tests need `withHostTest {}` on the `android` target, already set in `shared`). Definition of Done: shared logic is covered in `commonTest`.
 
## Stack
 
| Concern | Library |
|---|---|
| Test framework | `kotlin.test` (commonTest); JUnit5 is JVM-only (jvmTest/androidUnitTest) |
| Assertions | `kotlin.test` (`assertEquals`, `assertTrue`, `assertNull`) |
| Flow / StateFlow testing | Turbine |
| Coroutine testing | `kotlinx-coroutines-test` + `UnconfinedTestDispatcher` |
| UI testing | `runComposeUiTest` with Robolectric (`composeApp/src/androidHostTest`) |
 
---
 
## ViewModel Unit Tests
 
### Setup
 
Shared tests live in `commonTest` and use `kotlin.test` (`@Test`, `@BeforeTest`, `@AfterTest`, `assertEquals`, `assertTrue`). JUnit5 (`@BeforeEach`/`@Nested`) is JVM-only — use it only in `jvmTest`/`androidUnitTest`.
 
```kotlin
class LibraryViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
 
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }
 
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
```
 
### Testing State with Turbine
 
```kotlin
@Test
fun `loading library emits entries in state`() = runTest {
    val repo = FakeLibraryRepository()
    val viewModel = LibraryViewModel(GetLibraryUseCase(repo))
 
    viewModel.state.test {
        viewModel.onAction(LibraryAction.OnRefreshClick)
        assertTrue(awaitItem().isLoading)
        assertTrue(awaitItem().entries.isNotEmpty())
    }
}
```
 
### Testing Events (one-time side effects)
 
```kotlin
@Test
fun `clicking entry sends NavigateToDetail event`() = runTest {
    val viewModel = LibraryViewModel(GetLibraryUseCase(FakeLibraryRepository()))
 
    viewModel.events.test {
        viewModel.onAction(LibraryAction.OnEntryClick("123"))
        assertEquals(LibraryEvent.NavigateToDetail("123"), awaitItem())
    }
}
```
 
---
 
## Fake Repositories
 
Prefer **fakes** (not mocks) for repository dependencies. A fake is a simple in-memory implementation:
 
```kotlin
class FakeLibraryRepository : LibraryRepository {
    private val entries = MutableStateFlow<List<LibraryEntry>>(emptyList())
    var shouldReturnError = false

    override fun observeLibrary(): Flow<List<LibraryEntry>> = entries

    override suspend fun sync(): EmptyResult<DataError> {
        return if (shouldReturnError) Result.Error(DataError.Network.NO_INTERNET)
        else Result.Success(Unit)
    }
}
```

For SQLDelight-backed data sources, prefer a real **in-memory database** over a fake: create the schema on an in-memory driver (JDBC `:memory:` driver in `jvmTest`/Android host tests) and test the data source against it. Test Apollo data sources with `MockServer` or a `MapTestNetworkTransport`, and Ktor clients with `MockEngine`.

Because use cases are thin, ViewModel tests don't fake the use case — construct the **real** use case around a fake repository (`GetLibraryUseCase(FakeLibraryRepository())`, as above). You exercise the use case and the ViewModel together with one fake, and there's no separate use-case stub to drift out of sync.
 
---
 
## Testing Use Cases

A use case that only forwards to the repository is already covered by the ViewModel tests above — don't write a separate test just to assert the pass-through. The moment a use case grows real behaviour (sorting, filtering, combining sources, validation), give it its own focused test with a fake repository:

```kotlin
@Test
fun `returns entries sorted by most recent`() = runTest {
    val repo = FakeLibraryRepository().apply { /* seed out-of-order entries */ }
    val getLibrary = GetLibraryUseCase(repo)

    val result = getLibrary()

    assertTrue(result is Result.Success)
    // assert ordering on (result as Result.Success).data
}
```
 
---
 
## SavedStateHandle in Tests
 
Instantiate it directly — no mocking needed:
 
```kotlin
val savedStateHandle = SavedStateHandle(mapOf("entryId" to "123"))
val viewModel = EntryEditorViewModel(savedStateHandle, SaveEntryUseCase(FakeLibraryRepository()))
```
 
---
 
## When to Inject Dispatchers
 
Only inject `CoroutineDispatcher` into a class when:
1. It dispatches to a non-main dispatcher (e.g., `IO`), AND
2. That class is directly unit-tested.
 
ViewModels that only use `viewModelScope` do not need injected dispatchers. Use `Dispatchers.setMain()` in tests instead.
 
If a non-ViewModel class uses `withContext(Dispatchers.IO)` and is unit-tested, inject the dispatcher:
 
```kotlin
class ImageCompressor(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun compress(bytes: ByteArray): ByteArray = withContext(ioDispatcher) { ... }
}
 
// In test:
val compressor = ImageCompressor(ioDispatcher = UnconfinedTestDispatcher())
```
 
---
 
## View Tests (required for every screen)

Every screen in `composeApp` ships with `runComposeUiTest` tests in the same ticket, next to the view model tests. The tests cover the UI and UX, not only the logic:

- **Every state:** one test per status or state the screen can show (loading, content, empty, offline, error, and each variant such as in library or not). Assert what the user sees.
- **Every action:** each button, row and control sends the right action or callback when clicked (add, retry, back, item click, navigation callbacks).
- **Accessibility:** controls have a `contentDescription` or text, and a click target exists for each action. Use `onNodeWithContentDescription` or `onNodeWithText` for lookups, so a missing label fails the test.
- **UX rules from the ticket:** things that must be hidden, disabled or shown in a given state (for example, the Add button is hidden when the anime is already in the library).
- Test the stateless `XxxScreen(state, onAction)` composable with a hand-built state and a list that records actions. `XxxRoot` and the view model stay covered by the view model tests.
- Use the Robot pattern when a screen has 3 or more tests.
- Check touch targets with `assertTouchWidthIsEqualTo` / `assertTouchHeightIsEqualTo` (48dp). Layout bounds of `IconButton` are smaller than the touch target.
- After writing a test, break the behavior once and see it fail.

**Where and how they run:** view tests live in `composeApp/src/androidHostTest/kotlin/...`, run with `@RunWith(RobolectricTestRunner::class)` and `runComposeUiTest`, and are started with `./gradlew :composeApp:testAndroidHostTest` (also in CI). They are not in `commonTest` because Robolectric is Android-only. The setup lives in `composeApp/build.gradle.kts` (`withHostTest`, JVM flags for the JDK internals Robolectric needs, `ui-test-junit4`, `ui-test-manifest`, which is required) and `robolectric.properties` (SDK 35, because Espresso fails on the newest SDK). Not covered by these tests: real device rendering, Coil image loading, and navigation wiring in `App.kt`; those stay in the emulator check.

A view ticket is not done until these tests pass. If `composeApp` has no test task yet, setting it up is part of that ticket.

---

## Integration and E2E Tests

Write integration tests where database/network interactions are non-trivial. Write E2E tests for complex user flows using `runComposeUiTest`:

```kotlin
@OptIn(ExperimentalTestApi::class)
@Test
fun library_displaysEntries_afterLoad() = runComposeUiTest {
    setContent {
        LibraryScreen(
            state = LibraryState(entries = listOf(EntryUi("1", "Hello", "Mar 15"))),
            onAction = {}
        )
    }
    onNodeWithText("Hello").assertIsDisplayed()
}
```

---

## Robot Pattern (Complex UI / E2E Tests)

For complex end-to-end or multi-step UI tests, use the **Robot Pattern** to separate test intent from Compose interactions. A robot encapsulates all `composeTestRule` interactions for a screen, keeping tests readable and DRY.

### Structure

Every robot function returns `this` so calls can be chained like a builder:

```kotlin
// Robot class — owns all UI interactions for the screen
class LibraryRobot(private val composeTestRule: ComposeUiTest) {

    fun setContent(
        state: LibraryState,
        onAction: (LibraryAction) -> Unit = {}
    ) = apply {
        composeTestRule.setContent {
            LibraryScreen(state = state, onAction = onAction)
        }
    }

    fun assertEntryVisible(title: String) = apply {
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    fun clickEntry(title: String) = apply {
        composeTestRule.onNodeWithText(title).performClick()
    }

    fun assertEmptyState() = apply {
        composeTestRule.onNodeWithTag("empty_state").assertIsDisplayed()
    }
}
```

### Usage in Tests

```kotlin
@OptIn(ExperimentalTestApi::class)
class LibraryScreenTest {

    @Test
    fun displaysEntries_afterLoad() = runComposeUiTest {
        LibraryRobot(this)
            .setContent(LibraryState(entries = listOf(EntryUi("1", "Hello", "Mar 15"))))
            .assertEntryVisible("Hello")
    }

    @Test
    fun showsEmptyState_whenNoEntries() = runComposeUiTest {
        LibraryRobot(this)
            .setContent(LibraryState(entries = emptyList()))
            .assertEmptyState()
    }

    @Test
    fun clickingEntry_triggersAction() = runComposeUiTest {
        var clickedId: String? = null
        LibraryRobot(this)
            .setContent(
                state = LibraryState(entries = listOf(EntryUi("1", "Hello", "Mar 15"))),
                onAction = { if (it is LibraryAction.OnEntryClick) clickedId = it.entryId }
            )
            .assertEntryVisible("Hello")
            .clickEntry("Hello")
    }
}
```

**When to use:** Apply the robot pattern when a screen has 3+ UI test cases, when multiple tests share the same setup/assertion sequences, or when testing complex multi-step user flows (e.g., fill form → submit → assert result).
 
---
 
## What to Test
 
- Unit-test every ViewModel and any non-trivial domain/data logic, including sync (push/pull, conflict, offline) against an in-memory database and a fake remote.
- Unit-test any logic that is likely to change.
- Use fakes over mocks where possible — fakes are simpler and catch more real bugs.
- Write integration tests where DB/network interactions are non-trivial.
- Cover every screen's states, actions, accessibility labels and UX rules with `runComposeUiTest` tests (see View Tests).
- Write E2E Compose tests for critical user flows.
- Use the robot pattern for complex UI/E2E tests with multiple test cases or shared interaction sequences.