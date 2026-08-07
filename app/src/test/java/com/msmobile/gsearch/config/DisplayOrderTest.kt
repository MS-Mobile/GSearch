package com.msmobile.gsearch.config

import com.msmobile.gsearch.widget.WidgetAction
import com.msmobile.gsearch.widget.WidgetAction.GEMINI
import com.msmobile.gsearch.widget.WidgetAction.LENS
import com.msmobile.gsearch.widget.WidgetAction.MIC
import com.msmobile.gsearch.widget.WidgetAction.SEARCH
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [displayOrder] decides what the settings list looks like on open. It is a top-level
 * function in ConfigActivity.kt, so testing it loads no activity.
 */
class DisplayOrderTest {

    @Test
    fun `enabled actions come first, then the rest in declaration order`() {
        assertEquals(
            listOf(GEMINI, SEARCH, MIC, LENS),
            displayOrder(listOf(GEMINI, SEARCH)),
        )
    }

    @Test
    fun `every action is listed exactly once`() {
        WidgetAction.entries.forEach { action ->
            val order = displayOrder(listOf(action))
            assertEquals(WidgetAction.entries.size, order.size)
            assertEquals(order.distinct(), order)
        }
    }

    @Test
    fun `an all-enabled list is returned unchanged`() {
        val everything = listOf(GEMINI, LENS, MIC, SEARCH)
        assertEquals(everything, displayOrder(everything))
    }

    @Test
    fun `an empty list gives declaration order`() {
        assertEquals(WidgetAction.entries.toList(), displayOrder(emptyList()))
    }

    /**
     * Only the enabled actions are persisted, so a disabled one has no stored position to
     * come back to and lands at the end on the next open. The comment on [displayOrder]
     * describes the in-session behaviour, where the list is held in Compose state and rows
     * do keep their place — this is what happens after the screen is closed and reopened.
     */
    @Test
    fun `a disabled action reappears at the end, not at its old position`() {
        val shown = displayOrder(listOf(SEARCH, MIC, LENS))
        assertEquals(listOf(SEARCH, MIC, LENS, GEMINI), shown)

        val afterDisablingMic = displayOrder(listOf(SEARCH, LENS))
        assertEquals(listOf(SEARCH, LENS, MIC, GEMINI), afterDisablingMic)
    }
}
