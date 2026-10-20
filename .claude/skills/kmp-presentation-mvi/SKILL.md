---
name: kmp-presentation-mvi
description: |
  MVI presentation layer for KMP (Android-first) - State, Action, Event, ViewModel, Root/Screen composable split, UI models, UiText error mapping, and process death with SavedStateHandle. Use this skill whenever creating or reviewing a ViewModel, defining screen state, actions, or events, structuring composables, mapping errors to UI strings, or handling process death. Trigger on phrases like "add a ViewModel", "create a screen", "MVI", "state", "action", "event", "screen composable", "UiText", "SavedStateHandle", "ObserveAsEvents", or "UI model".
---
 
# KMP Presentation Layer (MVI)

**Where things live:** State, Action, Event, ViewModel and UI models are plain Kotlin in `shared/commonMain` (`shared.<feature>.presentation`), as the project keeps view models in `commonMain`. Root/Screen composables, `UiText`, `ObserveAsEvents` and `toUiText()` mappers live in `composeApp/commonMain` (`composeapp.<feature>`, `composeapp.core`), because they depend on Compose and string resources. `shared` must not depend on `composeApp`.
 
## Overview
 
Every screen has:
1. **State** — a single data class holding all UI state fields.
2. **Action** (Intent) — a sealed interface of all user-triggered actions.
3. **Event** — a sealed interface of one-time side effects (navigation, snackbar).
4. **ViewModel** — holds `StateFlow<State>`, processes `Action`, emits `Event` via `Channel`.
 
---
 
## State
 
```kotlin
data class LibraryState(
    val entries: List<EntryUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: DataError? = null
)
```
 
Always update state with `.update { }` — never replace the entire flow:
```kotlin
_state.update { it.copy(isLoading = true) }
```
 
---
 
## Action (Intent)
 
```kotlin
sealed interface LibraryAction {
    data object OnRefreshClick : LibraryAction
    data class OnEntryClick(val entryId: String) : LibraryAction
    data class OnDeleteEntry(val entryId: String) : LibraryAction
}
```
 
---
 
## Event (one-time side effects)
 
```kotlin
sealed interface LibraryEvent {
    data class NavigateToDetail(val entryId: String) : LibraryEvent
    data class ShowError(val error: DataError) : LibraryEvent
}
```
 
---
 
## Use Cases

Every data operation the UI triggers goes through a **use case** (interactor) that lives in the `domain` layer. The ViewModel depends on use cases — never on a repository or data source directly.

Shape: one class per operation, named for the operation (`GetLibraryUseCase`, `SaveEntryUseCase`), exposing a single `suspend operator fun invoke(...)` that returns the domain `Result<T, DataError>`. The `invoke` operator lets the call site read like a plain function call, so injecting it under a verb-like name keeps the ViewModel readable:

```kotlin
class GetLibraryUseCase(
    private val libraryRepository: LibraryRepository,
) {
    suspend operator fun invoke(): Result<List<LibraryEntry>, DataError> =
        libraryRepository.getLibrary()
}
```

**Apply this even when the use case is a one-line pass-through.** It looks like ceremony at first, but it buys three things that matter as the app grows:
- **A single home for behaviour.** When "get entries" later needs sorting, caching, or merging two sources, there's already one obvious place to add it — you change the use case, not every ViewModel that reads entries.
- **A stable seam for tests.** ViewModel tests fake one small use case instead of a whole repository surface; the use case itself is trivially testable in isolation.
- **A uniform call path.** Every screen reaches data the same way, so the codebase stays predictable regardless of who wrote which feature.

Register use cases with Koin as `factory { GetLibraryUseCase(get()) }` (or `factoryOf(::GetLibraryUseCase)`) — they're cheap and stateless, so `factory`, not `single`. Because the `domain` package is pure Kotlin with no Koin, define them in the feature's `presentation` Koin module (see **kmp-di-koin**), which sits next to the ViewModels that consume them.

---

## ViewModel

Inject use cases, named so the call site reads like a verb:

```kotlin
class LibraryViewModel(
    private val getLibrary: GetLibraryUseCase
) : ViewModel() {
 
    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()
 
    private val _events = Channel<LibraryEvent>()
    val events = _events.receiveAsFlow()
 
    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.OnRefreshClick -> loadLibrary()
            is LibraryAction.OnEntryClick -> {
                viewModelScope.launch {
                    _events.send(LibraryEvent.NavigateToDetail(action.entryId))
                }
            }
        }
    }
 
    private fun loadLibrary() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            getLibrary()
                .onSuccess { entries ->
                    _state.update { it.copy(entries = entries.map { it.toEntryUi() }, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false) }
                    _events.send(LibraryEvent.ShowError(error))
                }
        }
    }
}
```
 
---
 
## Coroutine Dispatchers
 
**Do not inject** unless the class is unit-tested and dispatches to a non-main dispatcher. For ViewModel tests, use `Dispatchers.setMain(UnconfinedTestDispatcher())` in test setup.
 
For blocking code that doesn't support suspension, wrap it:
```kotlin
suspend fun compressImage(bytes: ByteArray): ByteArray = withContext(Dispatchers.IO) {
    // blocking compression logic
}
```
 
Only inject `CoroutineDispatcher` when:
1. The class dispatches to a non-main dispatcher (e.g., `IO`), AND
2. That class is directly unit-tested.
 
---
 
## Mapping Errors to UI Strings

`shared` cannot reference `Res.string.*` (the string resources live in `composeApp`), so ViewModels keep **typed errors**, not text, in State and Event, and `composeApp` converts them to `UiText` at the Root/Screen layer:

```kotlin
// shared.library.presentation — no Compose, no resources
data class LibraryState(
    val error: DataError? = null
)
sealed interface LibraryEvent {
    data class ShowError(val error: DataError) : LibraryEvent
}
```

`UiText` (`composeapp.core`) wraps strings that originate from — or could originate from — a string resource:

```kotlin
sealed interface UiText {
    data class DynamicString(val value: String) : UiText
    class StringResource(
        val id: org.jetbrains.compose.resources.StringResource,
        val args: Array<Any> = emptyArray()
    ) : UiText

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> stringResource(id, *args)
    }

    suspend fun getString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> getString(id, *args)
    }
}
```

**When to use `UiText`:** For any string that comes from a string resource, could be localized, or might be either a resource or a dynamic value depending on context (e.g., error messages that map to `Res.string.*`). It exists only on the `composeApp` side.

**When to use plain `String`:** For values that are always dynamic and never come from resources — e.g., an anime title, a formatted date, a score. These can be exposed as `String` directly in the State or UI model in `shared`.

```kotlin
// composeApp: Root composable converts the typed error to text
ObserveAsEvents(viewModel.events) { event ->
    when (event) {
        is LibraryEvent.ShowError -> snackbarHostState.showSnackbar(event.error.toUiText().getString())
        ...
    }
}

// Plain String — always dynamic, never a resource (lives in shared)
data class EntryUi(
    val title: String,
    val formattedDate: String
)
```

Define `DataError.toUiText()` extension functions in `composeapp.core` (shared errors) or in the feature's `composeapp.<feature>` package (feature errors) that map error enums to `UiText.StringResource`. See **kmp-error-handling**.

---

 
## UI Model (Presentation Model)
 
When a domain model needs UI-specific formatting (dates, units, currency), create a dedicated UI model in the presentation layer:
 
```kotlin
data class EntryUi(
    val id: String,
    val title: String,
    val formattedDate: String  // e.g. "Mar 15, 2026"
)
 
fun LibraryEntry.toEntryUi(): EntryUi = EntryUi(
    id = id,
    title = title,
    formattedDate = date.format(...)
)
```
 
UI models are always suffixed with `Ui` (e.g., `EntryUi`, `TodoItemUi`).
 
---
 
## Composable Structure

Both the Root and Screen composable live in the **same file** in `composeApp` (e.g., `composeapp/library/LibraryScreen.kt`).

### Root Composable (suffixed `Root`)

Receives the ViewModel (via `koinViewModel()` from `org.koin.compose.viewmodel.koinViewModel`) and any callbacks needed for navigation. Observes events. Passes state and `onAction` down.

### Screen Composable (suffixed `Screen`)

Receives only `state` and `onAction`. No ViewModel reference. Can be previewed independently.

```kotlin
// LibraryScreen.kt — Root + Screen in a single file

@Composable
fun LibraryRoot(
    onNavigateToDetail: (String) -> Unit,
    viewModel: LibraryViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is LibraryEvent.NavigateToDetail -> onNavigateToDetail(event.entryId)
            is LibraryEvent.ShowError -> { /* show snackbar with error.toUiText() */ }
        }
    }

    LibraryScreen(
        state = state,
        onAction = viewModel::onAction
    )
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    onAction: (LibraryAction) -> Unit
) { ... }

@Preview
@Composable
private fun LibraryScreenPreview() {
    LibraryScreen(state = LibraryState(), onAction = {})
}
```
 
---
 
## Process Death
 
When a screen involves complex forms or critical user input, restore essential fields using `SavedStateHandle` (available in common code through the KMP `lifecycle-viewmodel-savedstate` artifact; verify the version in `libs.versions.toml` supports the targets in use):
 
```kotlin
class EntryEditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    private val _state = MutableStateFlow(
        EntryEditorState(
            title = savedStateHandle["title"] ?: "",
            body = savedStateHandle["body"] ?: ""
        )
    )
 
    fun onAction(action: EntryEditorAction) {
        when (action) {
            is EntryEditorAction.OnTitleChange -> {
                savedStateHandle["title"] = action.title
                _state.update { it.copy(title = action.title) }
            }
        }
    }
}
```
 
Only save what truly matters after process death — not the entire state.
 
---
 
## Naming Conventions
 
| Thing | Convention | Example |
|---|---|---|
| ViewModel | `<Screen>ViewModel` | `LibraryViewModel` |
| State | `<Screen>State` | `LibraryState` |
| Action | `<Screen>Action` | `LibraryAction` |
| Event | `<Screen>Event` | `LibraryEvent` |
| Root composable | `<Screen>Root` | `LibraryRoot` |
| Screen composable | `<Screen>Screen` | `LibraryScreen` |
| UI model | `<Model>Ui` | `EntryUi`, `TodoItemUi` |
 
---
 
## Checklist: Adding a New Screen
 
- [ ] Define `State`, `Action`, `Event` in `shared.<feature>.presentation`
- [ ] Add a use case in `shared.<feature>.domain` for each data operation the screen triggers; inject those (not repositories/data sources) into the `ViewModel`
- [ ] Implement `ViewModel` in `shared.<feature>.presentation` and register it in the feature's Koin module
- [ ] Create `<Screen>Root` composable in `composeapp.<feature>` (holds ViewModel, observes events)
- [ ] Create `<Screen>Screen` composable (pure state + onAction, previewable)
- [ ] Keep typed errors in State/Event; map them to `UiText` via extension functions in `composeApp`
- [ ] Add `SavedStateHandle` for any form fields that must survive process death