package com.example.whatcanicook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Every description used here is a real line taken from the Lidl receipt
 * export, so these tests pin the classifier against the data it has to handle.
 */
class ProductClassifierTest {

    private fun categoryOf(raw: String, artId: String? = null) =
        ShelfLifeTable.classify(raw, artId).category

    // ---------------------------------------------------------------- normalise

    @Test
    fun `strips the trailing loose-produce code`() {
        assertEquals("vine tomatoes", ProductNormaliser.normalise("Vine Tomatoes 0082465"))
    }

    @Test
    fun `splits run-together descriptions`() {
        // The leading pack count is dropped along with the other digits; what
        // matters for classification is the words.
        assertEquals("frozen salmon fillets", ProductNormaliser.normalise("6FrozenSalmonFillets"))
        assertEquals("whole nut chocolate", ProductNormaliser.normalise("WholeNutChocolate"))
    }

    @Test
    fun `strips weights`() {
        assertEquals("plums", ProductNormaliser.normalise("Plums 1kg 0080417"))
        assertEquals("mangetout", ProductNormaliser.normalise("Mangetout 165g 0082368"))
    }

    // ------------------------------------------------------- loose produce code

    @Test
    fun `loose produce code is recognised when it matches the article id`() {
        assertEquals("0082465", ProductNormaliser.looseProduceCode("Vine Tomatoes 0082465", "0082465"))
    }

    @Test
    fun `loose produce code is rejected when it does not match the article id`() {
        assertNull(ProductNormaliser.looseProduceCode("Vine Tomatoes 0082465", "9999999"))
    }

    @Test
    fun `packaged goods have no loose produce code`() {
        assertNull(ProductNormaliser.looseProduceCode("Wholewheat Fusilli"))
        assertNull(ProductNormaliser.looseProduceCode("15 Free Range Eggs"))
    }

    @Test
    fun `unknown produce still gets a produce shelf life from its code`() {
        val c = ShelfLifeTable.classify("Romanesco Thing 0084999", "0084999")
        assertEquals(Confidence.MEDIUM, c.confidence)
        assertTrue("should be short-dated, was ${c.shelfLifeDays}", c.shelfLifeDays <= 7)
    }

    // -------------------------------------------------------------- accents

    @Test
    fun `folds accented letters instead of deleting them`() {
        // Deleting the accent split the word into "pur e", so the pantry rule
        // never matched and tomato puree was classified as a fresh tomato.
        assertEquals("baresa tomato puree", ProductNormaliser.normalise("Baresa Tomato Purée"))
        assertEquals("creme fraiche", ProductNormaliser.normalise("Crème Fraîche"))
        assertEquals("spultuch", ProductNormaliser.normalise("SPÜLTUCH"))
    }

    @Test
    fun `accented tomato puree is cupboard, not salad`() {
        assertEquals(FoodCategory.PANTRY, categoryOf("Baresa Tomato Purée"))
    }

    // ------------------------------------------------- processed vs fresh

    @Test
    fun `tins and jars are not fresh ingredients`() {
        assertEquals(FoodCategory.PANTRY, categoryOf("Baresa Chopped Tomatoes with Onions & Garlic"))
        assertEquals(FoodCategory.PANTRY, categoryOf("Freshona Chopped Tomatoes"))
        assertEquals(FoodCategory.PANTRY, categoryOf("Baresa PEELED TOMATOES in tomato juice"))
        assertEquals(FoodCategory.PANTRY, categoryOf("Peppers & Walnut Sauce"))
        assertEquals(FoodCategory.PANTRY, categoryOf("Ching's Secret Dark Soy Sauce"))
    }

    @Test
    fun `dried herbs are not fresh herbs`() {
        assertEquals(FoodCategory.PANTRY, categoryOf("Batts Basil"))
        assertEquals(FoodCategory.PANTRY, categoryOf("Batts Rosemary"))
        // but genuinely fresh herbs still are
        assertEquals(FoodCategory.HERBS, categoryOf("Fresh Cut Rosemary 0081011"))
    }

    // ------------------------------------------------------------- the big trap

    @Test
    fun `peppermint tea is not a pepper`() {
        assertEquals(FoodCategory.PANTRY, categoryOf("Peppermint Tea"))
    }

    @Test
    fun `tomato puree is not a tomato`() {
        assertEquals(FoodCategory.PANTRY, categoryOf("Tomato Puree"))
    }

    @Test
    fun `frozen salmon is not fresh fish`() {
        assertEquals(FoodCategory.FROZEN, categoryOf("6FrozenSalmonFillets"))
        assertEquals(FoodCategory.FROZEN, categoryOf("Froz StirFry Veg Ori"))
    }

    // ------------------------------------------------------------ real receipts

    @Test
    fun `classifies the most recent shop correctly`() {
        val expected = mapOf(
            "Plums 1kg 0080417" to FoodCategory.FRUIT,
            "Sweet Potatoes 1kg 0080761" to FoodCategory.ROOT_VEG,
            "Organic Cucumber 0084535" to FoodCategory.SALAD_VEG,
            "Wholewheat Fusilli" to FoodCategory.PANTRY,
            "Chicken Breast Fillets 1kg" to FoodCategory.MEAT,
            "15 Free Range Eggs" to FoodCategory.EGGS,
            "Sweet & Crunchy Salad" to FoodCategory.LEAFY_GREENS,
            "Chestnut Mushrooms 0083006" to FoodCategory.SALAD_VEG,
            "Brown Onions 0083399" to FoodCategory.ROOT_VEG,
            "Brown Rice" to FoodCategory.PANTRY,
            "Iceberg Lettuce" to FoodCategory.LEAFY_GREENS,
            "Cherry Tomatoes 0082400" to FoodCategory.SALAD_VEG,
            "Fresh Cut Rosemary 0081011" to FoodCategory.HERBS,
            "Red Apples 0080244" to FoodCategory.FRUIT,
            "Parsnips 0082792" to FoodCategory.ROOT_VEG,
            "Creamy Greek Style Yogurt 1k" to FoodCategory.DAIRY,
            "Gold Kiwi 0080686" to FoodCategory.FRUIT,
            "Garlic 0082706" to FoodCategory.ROOT_VEG,
            "Fine Seasalt Shaker" to FoodCategory.PANTRY,
        )
        expected.forEach { (desc, category) ->
            assertEquals("misclassified: $desc", category, categoryOf(desc))
        }
    }

    @Test
    fun `classifies repeat buys correctly`() {
        assertEquals(FoodCategory.FISH, categoryOf("Half Side of Salmon"))
        assertEquals(FoodCategory.ROOT_VEG, categoryOf("Ginger 0082700"))
        assertEquals(FoodCategory.CHEESE, categoryOf("Greek Salad Cheese"))
        assertEquals(FoodCategory.CHEESE, categoryOf("Low Fat Cottage Ch."))
        assertEquals(FoodCategory.CHEESE, categoryOf("Machego Cheese"))
        assertEquals(FoodCategory.CHEESE, categoryOf("Brie Original"))
        assertEquals(FoodCategory.LEAFY_GREENS, categoryOf("Pak Choi 0083772"))
        assertEquals(FoodCategory.FRUIT, categoryOf("Persimmon 0080656"))
        assertEquals(FoodCategory.FRUIT, categoryOf("White Grapes 0080505"))
        assertEquals(FoodCategory.SALAD_VEG, categoryOf("Mangetout 165g 0082368"))
        assertEquals(FoodCategory.CHILLED_READY, categoryOf("Shortcrust Pastry"))
        assertEquals(FoodCategory.BAKERY, categoryOf("Pain au Chocolat"))
        assertEquals(FoodCategory.PANTRY, categoryOf("Chickpeas"))
        assertEquals(FoodCategory.PANTRY, categoryOf("Tuna Chunks in Water"))
    }

    // --------------------------------------------------------------- non-food

    @Test
    fun `bags are not food and never enter the kitchen`() {
        listOf("Carrier Bag", "Flexi Loop Carrier", "Heavy Duty Bag").forEach {
            assertEquals(it, FoodCategory.NON_FOOD, categoryOf(it))
            assertNull(
                "$it should not become a kitchen item",
                KitchenItem.fromPurchase(it, LocalDate.of(2026, 10, 23), ItemOrigin.LIDL_RECEIPT),
            )
        }
    }

    // ------------------------------------------------------------- purchases

    @Test
    fun `builds kitchen items from purchase lines`() {
        val bought = LocalDate.of(2026, 10, 23)
        val chicken = KitchenItem.fromPurchase(
            "Chicken Breast Fillets 1kg", bought, ItemOrigin.LIDL_RECEIPT,
        )
        assertNotNull(chicken)
        assertEquals(FoodCategory.MEAT, chicken!!.category)
        assertEquals(bought, chicken.purchasedOn)
        assertEquals(ItemOrigin.LIDL_RECEIPT, chicken.origin)

        val onions = KitchenItem.fromPurchase(
            "Brown Onions 0083399", bought, ItemOrigin.LIDL_RECEIPT,
        )!!
        assertEquals(FoodCategory.ROOT_VEG, onions.category)
        assertEquals("brown onions", onions.normalisedName)
    }

    @Test
    fun `snapshot groups items by category alphabetically`() {
        val today = LocalDate.of(2026, 10, 23)
        val names = listOf(
            "Chicken Breast Fillets 1kg", "Brown Onions 0083399",
            "Garlic 0082706", "Wholewheat Fusilli",
        )
        val items = names.mapIndexed { i, n ->
            KitchenItem.fromPurchase(n, today, ItemOrigin.MANUAL)!!.copy(id = i.toLong())
        }
        val snapshot = KitchenSnapshot(today, items)
        assertEquals(4, snapshot.active.size)
        // Cupboard, Meat, Root veg -> three groups
        assertEquals(3, snapshot.categoryCount)
        val labels = snapshot.byCategory.map { it.first.label }
        assertEquals(labels.sortedBy { it.lowercase() }, labels)
        // Garlic before Brown Onions? No: alphabetical within the group.
        val rootVeg = snapshot.byCategory.first { it.first == FoodCategory.ROOT_VEG }.second
        assertEquals(listOf("Brown Onions 0083399", "Garlic 0082706"), rootVeg.map { it.name })
    }
}
