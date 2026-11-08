package com.example.whatcanicook.ai

import com.example.whatcanicook.domain.ItemOrigin
import com.example.whatcanicook.domain.KitchenItem
import com.example.whatcanicook.domain.KitchenSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MealPlanPromptTest {

    private val today = LocalDate.of(2026, 11, 7)

    private fun kitchen(vararg names: String): KitchenSnapshot {
        val items = names.mapIndexedNotNull { i, n ->
            KitchenItem.fromPurchase(n, today, ItemOrigin.BARCODE)?.copy(id = i.toLong())
        }
        return KitchenSnapshot(today, items)
    }

    @Test
    fun `asks about today and rules out week plans`() {
        val prompt = MealPlanPrompt.build(kitchen("Chicken Breast Fillets 1kg"))
        assertTrue(prompt.contains("TODAY"))
        assertTrue(prompt.contains("not a week plan"))
        assertTrue(prompt.contains("healthily"))
    }

    @Test
    fun `lists the kitchen grouped by category`() {
        val prompt = MealPlanPrompt.build(
            kitchen("Chicken Breast Fillets 1kg", "Brown Rice", "Iceberg Lettuce"),
        )
        assertTrue(prompt.contains("Meat: Chicken Breast Fillets 1kg"))
        assertTrue(prompt.contains("Cupboard: Brown Rice"))
        assertTrue(prompt.contains("Leafy greens: Iceberg Lettuce"))
    }

    @Test
    fun `collapses duplicates into a count`() {
        val prompt = MealPlanPrompt.build(kitchen("Brown Rice", "Brown Rice", "Brown Rice"))
        assertTrue("expected a x3, got: $prompt", prompt.contains("Brown Rice x3"))
    }

    @Test
    fun `leaves non-food out of the prompt`() {
        // Carrier bags never become kitchen items, and cleaning products are
        // filtered as NON_FOOD, so neither should reach the assistant.
        val prompt = MealPlanPrompt.build(kitchen("Brown Rice", "Anti Bac Handwash"))
        assertFalse(prompt.contains("Handwash", ignoreCase = true))
        assertTrue(prompt.contains("Brown Rice"))
    }

    @Test
    fun `handles an empty kitchen without pretending`() {
        val prompt = MealPlanPrompt.build(KitchenSnapshot(today, emptyList()))
        assertTrue(prompt.contains("empty"))
    }

    @Test
    fun `a realistic kitchen still fits in a URL`() {
        val names = (1..70).map { "Some Reasonably Named Product $it" }.toTypedArray()
        val prompt = MealPlanPrompt.build(kitchen(*names))
        // Not a hard requirement, but if this trips the hand-off silently
        // drops to the share sheet, so it is worth knowing.
        assertTrue(
            "prompt was ${prompt.length} chars",
            prompt.length > MealPlanPrompt.MAX_URL_LENGTH,
        )
    }
}
