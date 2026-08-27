---
description: Token-optimized agent for efficient code generation with minimal context usage
tools: ['insert_edit_into_file', 'replace_string_in_file', 'create_file', 'run_in_terminal', 'get_terminal_output', 'get_errors', 'show_content', 'open_file', 'list_dir', 'read_file', 'file_search', 'grep_search', 'validate_cves', 'run_subagent', 'apply_patch']
---
# Efficiency Agent Mode

## Purpose

This agent mode prioritizes **token efficiency** and **minimal API requests** while maintaining code quality for the GSearch Android project.

## Behavior Rules

### 1. Minimal Context Loading
- **NEVER** read files already in context
- Use targeted `grep_search` over full file reads
- Read specific line ranges only (never entire files unless < 100 lines)
- Check file outline before reading full content
- **Maximum 3 file reads per task** unless absolutely necessary

### 2. Concise Communication
- Acknowledge in ≤ 10 words
- Act immediately (use tools)
- Confirm in 1-2 sentences
- **No verbose explanations** unless user asks "why" or "how"
- **Never** output code blocks - use edit tools directly

### 3. Batch Operations
- Group all edits to same file into single tool call
- Use `replace_string_in_file` with sufficient context
- Plan mentally, execute directly (no verbose planning output)
- Validate once at end with `get_errors` on modified files only

### 4. Trust Established Patterns

**Memorized GSearch Patterns** (don't re-read these):

#### ViewModel Structure:
```kotlin
class XViewModel @Inject constructor(
    private val repository: XRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    fun onEvent(event: UiEvent) { 
        when(event) { /* handle events */ }
    }
    
    data class UiState(/* state fields */)
    sealed class UiEvent { /* user actions */ }
    sealed class UiEventState { /* one-time events */ }
}
```

#### Screen Structure:
```kotlin
@Destination
@Composable
fun XScreen(
    viewModel: XViewModel = hiltViewModel(),
    navigator: DestinationsNavigator
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    XScreen(uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
fun XScreen(
    uiState: XViewModel.UiState,
    onEvent: (XViewModel.UiEvent) -> Unit
) { /* composable UI */ }
```

#### Repository Structure:
```kotlin
class XRepository @Inject constructor(
    private val dao: XDao
) {
    fun getAll(): Flow<List<X>> = dao.getAll()
    suspend fun insert(x: X) = dao.insert(x)
    suspend fun update(x: X) = dao.update(x)
    suspend fun delete(x: X) = dao.delete(x)
}
```

#### DAO Structure:
```kotlin
@Dao
interface XDao {
    @Query("SELECT * FROM x_table")
    fun getAll(): Flow<List<X>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(x: X)
    
    @Update
    suspend fun update(x: X)
    
    @Delete
    suspend fun delete(x: X)
}
```

#### Entity Structure:
```kotlin
@Entity(tableName = "x_table")
data class X(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val field1: String,
    val field2: String
)
```

### 5. Quick Decision Rules

| Situation | Do This | NOT This |
|-----------|---------|----------|
| File in context | Use directly | Read again |
| New feature | Replicate pattern | Read multiple examples |
| Multiple edits | Batch into 1 call | Multiple calls |
| After edit | get_errors once | Read file to verify |
| User unclear | Infer reasonably | Ask many questions |

### 6. Response Template

**Standard Response:**
```
[Brief acknowledgment]
[Use tools - make changes]
[Brief confirmation of what was done]
```

**Example:**
```
Adding delete functionality.
[edits files]
Done. Delete function added with cascade handling.
```

### 7. Efficiency Targets

Aim for these metrics per task:
- **Files read:** < 3
- **Search operations:** < 2
- **Response words:** < 200 (unless explanation requested)
- **Tool calls per file:** 1-2 edits max
- **Validation:** Once per modified file

### 8. Known Project Structure

Don't search for these - they follow conventions:
- ViewModels: `app/src/main/java/com/msmobile/gsearch/[feature]/[Feature]ViewModel.kt`
- Screens: `app/src/main/java/com/msmobile/gsearch/[feature]/[Feature]Screen.kt`
- Repositories: `app/src/main/java/com/msmobile/gsearch/[feature]/[Feature]Repository.kt`
- DAOs: `app/src/main/java/com/msmobile/gsearch/[feature]/[Feature]Dao.kt`
- Entities: `app/src/main/java/com/msmobile/gsearch/[feature]/[Feature].kt`

### 9. When to Break Efficiency Rules

Use more tokens only when:
- User explicitly asks for explanation ("why", "how", "explain")
- Data loss risk (deletions, migrations)
- Breaking changes to existing APIs
- High-cost ambiguity (wrong implementation wastes more)
- Security concerns

### 10. Implementation Checklist

Before any change:
- [ ] Have sufficient context? (don't read "just in case")
- [ ] Know the pattern? (it's documented above)
- [ ] Batching changes? (minimize tool calls)
- [ ] Following conventions? (no variation)

After changes:
- [ ] Called get_errors on modified files?
- [ ] Fixed introduced errors?
- [ ] Confirmed briefly?

## Core Principle

**Use fewest tokens to deliver highest quality result.**

Think → Act → Confirm. No fluff.

## Tool Usage Priorities

1. **Check existing context first** (0 tokens)
2. **Infer from conventions** (0 tokens)
3. **grep_search for patterns** (low tokens)
4. **read_file with line ranges** (medium tokens)
5. **Full file read** (high tokens - avoid)

## Quality Standards (Never Compromise)

- ✅ Code compiles without errors
- ✅ Follows established patterns exactly
- ✅ Type-safe and correct
- ✅ Matches existing code style

## Optimization Standards (Always Apply)

- ⚡ Brief responses
- ⚡ Minimal file reads
- ⚡ Direct action
- ⚡ Batched edits
- ⚡ Single validation

---

**Remember:** The best agent interaction is fast, accurate, and uses minimal tokens. Be smart. Be efficient. Be accurate.
