---
name: navigation-3
description:
  Learn how to install and migrate to Jetpack Navigation 3, and how to
  implement features and patterns such as deep links, multiple backstacks, scenes
  (dialogs, bottom sheets, list-detail, two-pane, supporting pane), conditional navigation
  (such as logged-in navigation vs anonymous), returning results from flows, integration
  with Hilt, ViewModel, Kotlin, and view interoperability.
license: Complete terms in LICENSE.txt
metadata:
  author: Google LLC
  last-updated: "2026-06-02"
  keywords:
    - recipe
    - Android
    - Navigation 2
    - Navigation 3
    - migration
    - Compose
    - guide
    - dependencies
    - NavKey
    - NavHost
    - NavDisplay
    - BottomSheet
    - list-detail
    - scenes
    - two-pane
    - supporting pane
    - multiple backstacks
    - dialog
    - Hilt
    - ViewModel
    - View interop.
---

## Migration guide

- _[Navigation 2 to Navigation 3 migration guide](references/android/guide/navigation/navigation-3/migration-guide.md)_: Step-by-step guide to migrate an Android application from Navigation 2 to Navigation 3, covering dependency updates, route changes, state management, and UI component replacements.

### Requirements

- _[Guide: Migrate to type-safe navigation in Compose](references/android/guide/navigation/type-safe-destinations.md)_ : Step-by-step guide to migrating an Android application from string-based navigation to **Type-Safe Navigation** in Jetpack Compose using Jetpack Navigation 2.

## Developer documentation

- \*[Navigation 3](references/android/guide/navigation/navigation-3/index.md). Search documentation for more information on basics, saving and managing navigation state, modularizing navigation code, creating custom layouts using Scenes, animating between destinations, or applying logic or wrappers to destinations.

## Recipes

Code examples showcasing common patterns.

### Basic API usage

- _[Basic](references/android/guide/navigation/navigation-3/recipes/basic.md)_: Shows most basic API usage.
- _[Saveable back stack](references/android/guide/navigation/navigation-3/recipes/basicsaveable.md)_: Shows basic API usage with a persistent back stack.
- _[Entry provider DSL](references/android/guide/navigation/navigation-3/recipes/basicdsl.md)_: Shows basic API usage using the entryProvider DSL.

### Common UI

- _[Common UI](references/android/guide/navigation/navigation-3/recipes/common-ui.md)_: Demonstrates how to implement a common navigation UI pattern with a bottom navigation bar and multiple back stacks, where each tab in the navigation bar has its own navigation history.

### Deep links

- _[Basic](references/android/guide/navigation/navigation-3/recipes/deeplinks-basic.md)_: Shows how to parse a deep link URL from an Android Intent into a navigation key.
- _[Advanced](references/android/guide/navigation/navigation-3/recipes/deeplinks-advanced.md)_: Shows how to handle deep links with a synthetic back stack and correct "Up" navigation behavior.

### Scenes

#### Use built-in Scenes

- _[Dialog](references/android/guide/navigation/navigation-3/recipes/dialog.md)_: Shows how to create a Dialog.

#### Create custom Scenes

- _[BottomSheet](references/android/guide/navigation/navigation-3/recipes/bottomsheet.md)_: Shows how to create a BottomSheet destination.
- _[List-Detail Scene](references/android/guide/navigation/navigation-3/recipes/scenes-listdetail.md)_: Demonstrates how to implement adaptive list-detail layouts using the Navigation 3 Scenes API.
- _[Two pane Scene](references/android/guide/navigation/navigation-3/recipes/scenes-twopane.md)_: Demonstrates how to implement adaptive two-pane layouts using the Navigation 3 Scenes API.

### Material Adaptive

- _[Material List-Detail](references/android/guide/navigation/navigation-3/recipes/material-listdetail.md)_: Demonstrates how to implement an adaptive list-detail layout using Material 3 Adaptive.
- _[Material Supporting Pane](references/android/guide/navigation/navigation-3/recipes/material-supportingpane.md)_: Demonstrates how to implement an adaptive supporting pane layout using Material 3 Adaptive.

### Animations

- _[Animations](references/android/guide/navigation/navigation-3/recipes/animations.md)_: Shows how to override the default animations for all destinations and a single destination.

### Common back stack behavior

- _[Multiple back stacks](references/android/guide/navigation/navigation-3/recipes/multiple-backstacks.md)_: Shows how to create multiple top level routes, each with its own back stack. Top level routes are displayed in a navigation bar allowing users to switch between them. State is retained for each top level route, and the navigation state persists config changes and process death.

### Conditional navigation

- _[Conditional navigation](references/android/guide/navigation/navigation-3/recipes/conditional.md)_: Switch to a different navigation flow when a condition is met. For example, for authentication or first-time user onboarding.

### Architecture

- _[Modularized navigation code (Hilt)](references/android/guide/navigation/navigation-3/recipes/modular-hilt.md)_: Demonstrates how to decouple navigation code into separate modules using Hilt or Dagger for DI.
- _[Modularized navigation code (Koin)](references/android/guide/navigation/navigation-3/recipes/modular-koin.md)_: Demonstrates how to decouple navigation code into separate modules using Koin for DI.

### Working with ViewModel

#### Passing navigation arguments

- _[Basic ViewModel](references/android/guide/navigation/navigation-3/recipes/passingarguments.md)_ : Navigation arguments are passed to a `ViewModel` constructed using `viewModel()`

### Returning results

- _[Returning Results as Events](references/android/guide/navigation/navigation-3/recipes/results-event.md)_ : Returning results as events to content in another `NavEntry`
- _[Returning Results as State](references/android/guide/navigation/navigation-3/recipes/results-state.md)_ : Returning results as state stored in a `CompositionLocal`
