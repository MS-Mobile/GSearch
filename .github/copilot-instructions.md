# GitHub Copilot Instructions

## Project Context

This is **GSearch**, an Android application built with Kotlin, Jetpack Compose, and modern Android architecture components.

**Reference `.github/GUIDELINES.md` for detailed project standards and conventions before making changes.**

## Core Principles

1. **Minimal Changes Only** – Do not modify code unless absolutely necessary. Never refactor or rename existing patterns (e.g., `UiState` → `Ui.State`).
2. **Preserve Existing Conventions** – Follow the established patterns already present in the codebase.
3. **Clean Up After Yourself** – When discarding Agent mode changes, delete all files you created.

## Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material 3
- **Architecture:** MVVM with UDF (Unidirectional Data Flow)
- **DI:** Hilt
- **Database:** Room
- **Navigation:** Compose Destinations
- **Async:** Kotlin Coroutines & Flow
- **Serialization:** Moshi

## Key Patterns

### ViewModel Structure
ViewModels follow a consistent pattern with:
- `UiState` data class for screen state
- `UiEvent` sealed class for user actions
- `UiEventState` sealed class for one-time events
- Single `onEvent(event: UiEvent)` function to handle all events

### Composables
- Screen composables receive `uiState` and `onEvent` lambda
- Use `collectAsStateWithLifecycle()` for Flow collection
- Follow existing naming: `*Screen.kt` for screens, `*View.kt` for reusable components

### Data Layer
- Entity classes use Room annotations (`@Entity`, `@PrimaryKey`)
- DAOs are interfaces with suspend functions
- Repositories wrap DAOs and handle data operations

## Maintenance Commands

### Check Latest Gradle Version
To find the latest stable Gradle version, use the official Gradle API:
```powershell
Invoke-RestMethod -Uri "https://services.gradle.org/versions/current"
```
This returns the current stable version with download URLs and checksums. Update `gradle/wrapper/gradle-wrapper.properties` with the version from `downloadUrl`.

## Do NOT

- Change import styles or organization
- Rename classes, functions, or variables without explicit request
- Add dependencies without explicit request
- Modify build configuration without explicit request
- Create unnecessary abstraction layers
- Change existing sealed class/interface structures
