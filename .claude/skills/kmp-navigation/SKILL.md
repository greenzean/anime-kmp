---
name: kmp-navigation
description: |
  Type-safe Compose Navigation for KMP (Android-first) - route objects, feature nav graphs, cross-feature callbacks, and wiring in the composeApp NavHost. Use this skill whenever setting up navigation, defining routes, adding a new screen to a nav graph, navigating between features, or wiring nav graphs in the app. Trigger on phrases like "set up navigation", "add a route", "navigate between screens", "nav graph", "NavController", "type-safe nav", "cross-feature navigation", or "NavGraphBuilder".
---
 
# KMP Navigation
 
## Principles
 
- **Type-safe navigation** with `@Serializable` route objects (KotlinX Serialization).
- **One nav graph per feature**, defined in the feature's `composeApp` package (`composeapp.<feature>`, `commonMain`). Navigation is UI, so it never lives in `shared`.
- Feature nav graphs are assembled in one `NavHost` in `composeApp` (`composeapp.navigation`). `androidApp` only hosts `MainActivity`, which calls `App()`.
- Navigation **within** a feature uses a `NavController` passed into the feature nav graph.
- Feature-to-feature navigation uses **callbacks**, keeping features decoupled.
 
---
 
## Route Objects
 
Define routes as `@Serializable` objects or data classes in the feature's `composeApp` package:
 
```kotlin
// composeApp: composeapp.library
@Serializable data object LibraryRoute
@Serializable data class EntryDetailRoute(val entryId: String)
```
 
Use `data object` for screens with no parameters, `data class` for screens with arguments.
 
---
 
## Feature Nav Graph
 
Each feature exposes a `NavGraphBuilder` extension function:
 
```kotlin
// composeApp: composeapp.library
fun NavGraphBuilder.libraryGraph(
    navController: NavController,
    onNavigateToSearch: () -> Unit  // callback for cross-feature navigation
) {
    navigation<LibraryRoute>(startDestination = LibraryRoute) {
        composable<LibraryRoute> {
            LibraryRoot(
                onNavigateToDetail = { navController.navigate(EntryDetailRoute(it)) }
            )
        }
        composable<EntryDetailRoute> { backStackEntry ->
            val route: EntryDetailRoute = backStackEntry.toRoute()
            EntryDetailRoot(
                entryId = route.entryId,
                onNavigateToSearch = onNavigateToSearch
            )
        }
    }
}
```
 
---
 
## Wiring in `composeApp`
 
All feature nav graphs are assembled in one place, called from `App()`:
 
```kotlin
// composeApp: composeapp.navigation
NavHost(navController, startDestination = LibraryRoute) {
    libraryGraph(
        navController = navController,
        onNavigateToSearch = { navController.navigate(SearchRoute) }
    )
    searchGraph(navController)
}
```
 
Cross-feature navigation is always expressed as a lambda callback — never by importing a route from another feature's package.
 
---
 
## Passing Arguments
 
For simple scalar arguments, use `@Serializable data class` routes:
 
```kotlin
@Serializable data class EntryDetailRoute(val entryId: String)
 
// Navigate
navController.navigate(EntryDetailRoute(entryId = "abc123"))
 
// Receive
composable<EntryDetailRoute> { backStackEntry ->
    val route: EntryDetailRoute = backStackEntry.toRoute()
    // route.entryId available here
}
```
 
Avoid passing complex objects via navigation — pass IDs and load data in the destination ViewModel.
 
---
 
## Naming Conventions
 
| Thing | Convention | Example |
|---|---|---|
| Nav route | `<Screen>Route` | `LibraryRoute`, `EntryDetailRoute` |
| Feature nav graph | `<feature>Graph(...)` on `NavGraphBuilder` | `libraryGraph(...)` |
 
---
 
## Checklist: Adding Navigation to a New Feature
 
- [ ] Define `@Serializable` route objects for each screen in `composeapp.<feature>`
- [ ] Add feature nav graph function (`NavGraphBuilder.<feature>Graph(...)`)
- [ ] Pass `NavController` for intra-feature navigation
- [ ] Expose cross-feature destinations as lambda callbacks (not direct route imports)
- [ ] Wire nav graph and cross-feature callbacks in the `composeApp` `NavHost`
 