# GSearch Development Guidelines

## Project Overview

GSearch is an Android application for managing visits and householder records. It uses modern Android development practices with Jetpack Compose.

## Architecture

### MVVM with Unidirectional Data Flow (UDF)

```
┌─────────────────────────────────────────────────────────────┐
│                         Screen                              │
│  ┌─────────────────┐              ┌─────────────────────┐   │
│  │    Composable   │──UiEvent───▶│     ViewModel       │   │
│  │                 │◀──UiState───│                     │   │
│  └─────────────────┘              └─────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

- **UiState**: Immutable data class representing the current screen state
- **UiEvent**: Sealed class representing user actions
- **UiEventState**: Sealed class for one-time events (navigation, snackbars, dialogs)

### Package Structure

```
com.msmobile.gsearch/
├── di/                 # Hilt dependency injection modules
├── extension/          # Kotlin extension functions
├── ui/
│   ├── theme/          # Material 3 theming
│   └── views/          # Reusable Compose components
├── util/               # Utility classes and helpers
├── [feature]/          # Feature packages (e.g., visit, householder)
│   ├── Entity.kt       # Room entity
│   ├── Dao.kt          # Room DAO interface
│   ├── Repository.kt   # Data repository
│   ├── ViewModel.kt    # Screen ViewModel
│   └── Screen.kt       # Compose screen
└── ...
```

## Code Conventions

### Kotlin Style

- Use `data class` for immutable data holders
- Use `sealed class` or `sealed interface` for restricted hierarchies
- Prefer expression bodies for simple functions
- Use trailing commas in multi-line declarations

### ViewModel Pattern

```kotlin
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val repository: FeatureRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun onEvent(uiEvent: UiEvent) {
        when (uiEvent) {
            is UiEvent.ActionOne -> handleActionOne()
            is UiEvent.ActionTwo -> handleActionTwo(uiEvent.value)
            // ...
        }
    }

    // Internal sealed classes at the bottom of the file
    sealed class UiEvent {
        data object ActionOne : UiEvent()
        data class ActionTwo(val value: String) : UiEvent()
    }

    sealed class UiEventState {
        data object Idle : UiEventState()
        data object Loading : UiEventState()
        data object Success : UiEventState()
    }

    data class UiState(
        val data: List<Item> = emptyList(),
        val eventState: UiEventState = UiEventState.Idle
    )
}
```

### Compose Screen Pattern

```kotlin
@Destination
@Composable
fun FeatureScreen(
    viewModel: FeatureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FeatureScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun FeatureScreenContent(
    uiState: FeatureViewModel.UiState,
    onEvent: (FeatureViewModel.UiEvent) -> Unit
) {
    // UI implementation
}
```

### Room Entities

```kotlin
@Entity(tableName = "table_name")
data class EntityName(
    @PrimaryKey val id: UUID,
    val field1: String,
    val field2: Int,
    val optionalField: String? = null
)
```

### Room DAOs

```kotlin
@Dao
interface EntityDao {
    @Query("SELECT * FROM table_name WHERE id = :id")
    suspend fun getById(id: UUID): EntityName

    @Upsert
    suspend fun save(entity: EntityName)

    @Query("DELETE FROM table_name WHERE id IN (:ids)")
    suspend fun deleteBulk(ids: List<UUID>)
}
```

### Repositories

```kotlin
class FeatureRepository(private val dao: FeatureDao) {
    suspend fun getById(id: UUID): Feature = dao.getById(id)
    suspend fun save(feature: Feature) = dao.save(feature)
}
```

## Dependency Injection

All dependencies are provided through Hilt modules in the `di/` package.

```kotlin
@InstallIn(SingletonComponent::class)
@Module
class ApplicationModule {
    @Singleton
    @Provides
    fun provideRepository(dao: FeatureDao): FeatureRepository {
        return FeatureRepository(dao)
    }
}
```

## Testing

- Unit tests: `app/src/test/` - JUnit tests for ViewModels and business logic
- Instrumented tests: `app/src/androidTest/` - Hilt-enabled tests for repositories and database

### Unit Test Structure

- **Never pass mocks directly** to `createViewModel()` or similar factory methods
- All mock configuration (stubbing with `on`/`doReturn`) must happen **inside** `createViewModel()`
- Use **parameters** to control mock behavior (e.g., `createBackupResult: Result<Uri>? = null`)
- Use `MockReferenceHolder` only when the test needs to **access** or **verify** the mock after creation
- No `whenever` stubbing in test methods - all behavior is configured in the factory

```kotlin
// ✅ CORRECT - mock configured inside createViewModel with parameters
private fun createViewModel(
    createBackupResult: Result<Uri>? = null,
    repositoryRef: MockReferenceHolder<FeatureRepository>? = null
): FeatureViewModel {
    val repository = mock<FeatureRepository> {
        if (createBackupResult != null) {
            on { create() } doReturn createBackupResult
        }
    }
    repositoryRef?.value = repository
    
    return FeatureViewModel(repository)
}

// Test uses parameters, accesses mock via ref if needed
@Test
fun `test description`() {
    val repositoryRef = MockReferenceHolder<FeatureRepository>()
    val viewModel = createViewModel(
        createBackupResult = Result.success(mockUri),
        repositoryRef = repositoryRef
    )
    val repository = requireNotNull(repositoryRef.value)
    // verify(repository)...
}

// ❌ WRONG - passing configured mock directly
val repository = mock<FeatureRepository> {
    on { create() } doReturn Result.success(mockUri)
}
val viewModel = createViewModel(repository = repository)
```

### Android Test Structure

```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FeatureTest {
    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testDescription_condition_expectedResult() = runTest {
        // Arrange
        // Act
        // Assert
    }
}
```

## Navigation

Uses [Compose Destinations](https://github.com/raamcosta/compose-destinations) library with type-safe navigation.

```kotlin
@Destination
@Composable
fun FeatureScreen(
    navigator: DestinationsNavigator,
    featureId: UUID
) {
    // ...
}
```

## String Resources

- All user-facing strings should be in `res/values/strings.xml`
- Use `StringResource` utility for ViewModel-to-UI string handling

## Version Catalog

Dependencies are managed in `gradle/libs.versions.toml`. Add new dependencies there, not directly in `build.gradle.kts`.

## Git Hooks

The project uses pre-commit hooks for code quality. Configure with:

```bash
./gradlew installGitHooks
```
