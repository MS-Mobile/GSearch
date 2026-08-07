package com.msmobile.gsearch.widget

import com.msmobile.gsearch.widget.WidgetAction.GEMINI
import com.msmobile.gsearch.widget.WidgetAction.LENS
import com.msmobile.gsearch.widget.WidgetAction.MIC
import com.msmobile.gsearch.widget.WidgetAction.SEARCH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the half of [WidgetConfig] that takes no [android.content.Context].
 *
 * The stored form is the enum name, so what is under test here is really the contract with
 * whatever is already on users' devices: a preference written by an older build, or one
 * hand-edited, still has to produce a usable bar.
 */
class WidgetConfigTest {

    @Test
    fun `parseActions falls back to the defaults when nothing is stored`() {
        assertEquals(WidgetConfig.DEFAULT_ACTIONS, WidgetConfig.parseActions(null))
    }

    @Test
    fun `parseActions falls back to the defaults for an empty string`() {
        // Reachable in practice: serialiseActions(emptyList()) writes exactly this.
        assertEquals(WidgetConfig.DEFAULT_ACTIONS, WidgetConfig.parseActions(""))
    }

    @Test
    fun `parseActions falls back to the defaults when no name is recognised`() {
        assertEquals(WidgetConfig.DEFAULT_ACTIONS, WidgetConfig.parseActions("SEARCH_V2,NOPE"))
    }

    @Test
    fun `parseActions keeps the stored order rather than declaration order`() {
        assertEquals(listOf(LENS, SEARCH, MIC), WidgetConfig.parseActions("LENS,SEARCH,MIC"))
    }

    @Test
    fun `parseActions drops names it does not know and keeps the rest`() {
        // The case a rename would produce: one constant gone, the others still valid.
        assertEquals(listOf(MIC, GEMINI), WidgetConfig.parseActions("MIC,ASSISTANT,GEMINI"))
    }

    @Test
    fun `parseActions collapses duplicates to the first occurrence`() {
        assertEquals(listOf(SEARCH, MIC), WidgetConfig.parseActions("SEARCH,MIC,SEARCH"))
    }

    @Test
    fun `parseActions is exact about names, not case-insensitive or trimmed`() {
        // Documents that the match is `it.name == name` and nothing more, so a stored value
        // with stray whitespace decays to the defaults instead of half-loading.
        assertEquals(WidgetConfig.DEFAULT_ACTIONS, WidgetConfig.parseActions("search"))
        assertEquals(WidgetConfig.DEFAULT_ACTIONS, WidgetConfig.parseActions(" SEARCH , MIC "))
    }

    /**
     * The invariant [WidgetConfig.backgroundActionIn] rests on: it calls `first()`, which
     * would throw on an empty list. Nothing guards that at the call site — this fallback is
     * the only thing keeping it safe, so it is pinned here rather than left implied.
     */
    @Test
    fun `parseActions never returns an empty list`() {
        val stored = listOf(null, "", ",", ",,,", "NOPE", "search", "SEARCH")
        stored.forEach { value ->
            assertTrue(
                "parseActions($value) was empty",
                WidgetConfig.parseActions(value).isNotEmpty(),
            )
        }
    }

    @Test
    fun `serialiseActions round-trips through parseActions`() {
        val actions = listOf(GEMINI, SEARCH, LENS)
        assertEquals(actions, WidgetConfig.parseActions(WidgetConfig.serialiseActions(actions)))
    }

    @Test
    fun `serialiseActions collapses duplicates`() {
        assertEquals("SEARCH,MIC", WidgetConfig.serialiseActions(listOf(SEARCH, MIC, SEARCH)))
    }

    @Test
    fun `serialiseActions writes an empty string for an empty list`() {
        assertEquals("", WidgetConfig.serialiseActions(emptyList()))
    }

    @Test
    fun `backgroundActionIn prefers SEARCH wherever it sits in the order`() {
        assertEquals(SEARCH, WidgetConfig.backgroundActionIn(listOf(LENS, MIC, SEARCH)))
    }

    @Test
    fun `backgroundActionIn falls back to the first action when SEARCH is off`() {
        assertEquals(LENS, WidgetConfig.backgroundActionIn(listOf(LENS, MIC, GEMINI)))
    }

    /**
     * The `take(MAX_ACTIONS)` cap in both functions is unreachable today, because there are
     * exactly as many actions as slots and both lists are deduplicated first. This fails the
     * day a fifth action is added — at which point the cap goes live and wants its own test.
     */
    @Test
    fun `the slot cap is currently unreachable because every action fits`() {
        assertEquals(WidgetConfig.MAX_ACTIONS, WidgetAction.entries.size)
    }

    @Test
    fun `the defaults are a usable bar and leave GEMINI off`() {
        assertTrue(WidgetConfig.DEFAULT_ACTIONS.isNotEmpty())
        assertTrue(WidgetConfig.DEFAULT_ACTIONS.size <= WidgetConfig.MAX_ACTIONS)
        assertEquals(WidgetConfig.DEFAULT_ACTIONS.distinct(), WidgetConfig.DEFAULT_ACTIONS)
        assertTrue(SEARCH in WidgetConfig.DEFAULT_ACTIONS)
        assertTrue(GEMINI !in WidgetConfig.DEFAULT_ACTIONS)
    }

    /**
     * Renaming a constant silently reconfigures every widget already on a home screen, since
     * the old name stops resolving and the bar decays to the defaults. Spelling the wire
     * format out here makes that a failing test rather than a support question.
     */
    @Test
    fun `the persisted names are the ones already on devices`() {
        assertEquals(
            listOf("SEARCH", "MIC", "LENS", "GEMINI"),
            WidgetAction.entries.map { it.name },
        )
    }

    @Test
    fun `declaration order is not the default order`() {
        // Guards the comment on DEFAULT_ACTIONS: the defaults are a deliberate subset, so a
        // future edit that makes them fall out of `entries` would be a behaviour change.
        assertNotEquals(WidgetAction.entries.toList(), WidgetConfig.DEFAULT_ACTIONS)
    }
}
