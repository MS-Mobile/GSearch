package com.msmobile.gsearch.widget

import com.msmobile.gsearch.widget.WidgetAction.GEMINI
import com.msmobile.gsearch.widget.WidgetAction.LENS
import com.msmobile.gsearch.widget.WidgetAction.MIC
import com.msmobile.gsearch.widget.WidgetAction.SEARCH
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [WidgetConfig.parseOrder] decides what the settings list looks like on open: every
 * action, switched-off ones included, in the arrangement the user last left.
 *
 * The switched-off rows are the reason this is stored at all. Only the enabled actions go
 * into `KEY_ACTIONS`, so before the order was persisted a row that was switched off — or
 * dragged while switched off — had nowhere to record its position and reappeared at the
 * bottom of the list on the next open.
 */
class DisplayOrderTest {

    @Test
    fun `a stored order is returned as it was saved`() {
        assertEquals(
            listOf(MIC, LENS, SEARCH, GEMINI),
            WidgetConfig.parseOrder("MIC,LENS,SEARCH,GEMINI", listOf(LENS, SEARCH)),
        )
    }

    /**
     * The bug this key exists for: a row is dragged while switched off, so `KEY_ACTIONS`
     * does not change and only the stored order records the move.
     */
    @Test
    fun `a switched-off action keeps the position it was dragged to`() {
        val stored = WidgetConfig.serialiseOrder(listOf(MIC, LENS, SEARCH, GEMINI))
        assertEquals(MIC, WidgetConfig.parseOrder(stored, listOf(LENS, SEARCH, GEMINI)).first())
    }

    /**
     * The other half of the same bug: switching a row off used to send it to the bottom on
     * the next open, because its position lived only in the enabled list it just left.
     */
    @Test
    fun `switching an action off leaves it where it sits`() {
        val stored = WidgetConfig.serialiseOrder(listOf(SEARCH, MIC, LENS, GEMINI))
        assertEquals(
            listOf(SEARCH, MIC, LENS, GEMINI),
            WidgetConfig.parseOrder(stored, listOf(SEARCH, LENS)),
        )
    }

    @Test
    fun `every action is listed exactly once`() {
        val order = WidgetConfig.parseOrder("LENS", listOf(SEARCH))
        assertEquals(WidgetAction.entries.size, order.size)
        assertEquals(order.distinct(), order)
    }

    /**
     * The migration case: a preference written by a build that stored only the enabled
     * actions. Reproduces what the old `displayOrder(enabled)` did — enabled first, then
     * the remainder in declaration order — so an upgrade does not rearrange the list.
     */
    @Test
    fun `with no stored order the enabled actions come first, then the rest`() {
        assertEquals(
            listOf(GEMINI, SEARCH, MIC, LENS),
            WidgetConfig.parseOrder(null, listOf(GEMINI, SEARCH)),
        )
    }

    @Test
    fun `an empty stored order is treated as none stored`() {
        assertEquals(
            listOf(LENS, SEARCH, MIC, GEMINI),
            WidgetConfig.parseOrder("", listOf(LENS, SEARCH)),
        )
    }

    /**
     * What a rename or a newly added action leaves behind: the names that still resolve
     * keep their arrangement, and anything the stored value does not mention is appended
     * rather than dropped, so no action can go missing from the settings list.
     */
    @Test
    fun `names that no longer resolve are dropped and the missing ones appended`() {
        assertEquals(
            listOf(GEMINI, MIC, SEARCH, LENS),
            WidgetConfig.parseOrder("GEMINI,ASSISTANT,MIC", listOf(SEARCH, MIC)),
        )
    }

    @Test
    fun `a duplicated name collapses to its first occurrence`() {
        assertEquals(
            listOf(LENS, SEARCH, MIC, GEMINI),
            WidgetConfig.parseOrder("LENS,SEARCH,LENS", listOf(MIC)),
        )
    }

    @Test
    fun `serialiseOrder round-trips through parseOrder`() {
        val order = listOf(GEMINI, LENS, MIC, SEARCH)
        assertEquals(
            order,
            WidgetConfig.parseOrder(
                WidgetConfig.serialiseOrder(order),
                WidgetConfig.DEFAULT_ACTIONS,
            ),
        )
    }

    /**
     * Unlike `serialiseActions`, this one is not capped at [WidgetConfig.MAX_ACTIONS]: the
     * cap is on how many actions the bar can show, and the settings list has to offer the
     * ones it cannot.
     */
    @Test
    fun `serialiseOrder writes every action, not just the ones that fit the bar`() {
        assertEquals(
            "SEARCH,MIC,LENS,GEMINI",
            WidgetConfig.serialiseOrder(listOf(SEARCH, MIC, LENS, GEMINI)),
        )
    }
}
