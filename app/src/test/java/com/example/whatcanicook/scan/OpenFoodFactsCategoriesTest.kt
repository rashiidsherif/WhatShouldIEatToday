package com.example.whatcanicook.scan

import com.example.whatcanicook.domain.FoodCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The point of using Open Food Facts categories is that they carry the
 * *preparation*, which a product name often doesn't. These tests pin the rule
 * that preparation beats the base ingredient.
 */
class OpenFoodFactsCategoriesTest {

    @Test
    fun `canned tomatoes are cupboard, not salad veg`() {
        val tags = listOf(
            "en:plant-based-foods-and-beverages", "en:plant-based-foods",
            "en:vegetables", "en:canned-foods", "en:canned-tomatoes",
        )
        assertEquals(FoodCategory.PANTRY, OpenFoodFactsCategories.categoryFor(tags))
    }

    @Test
    fun `frozen peas are frozen, not greens`() {
        val tags = listOf("en:plant-based-foods", "en:vegetables", "en:frozen-foods", "en:peas")
        assertEquals(FoodCategory.FROZEN, OpenFoodFactsCategories.categoryFor(tags))
    }

    @Test
    fun `dried herbs are cupboard, not fresh herbs`() {
        val tags = listOf("en:plant-based-foods", "en:dried-products", "en:dried-herbs")
        assertEquals(FoodCategory.PANTRY, OpenFoodFactsCategories.categoryFor(tags))
    }

    @Test
    fun `fresh vegetables still read as fresh`() {
        val tags = listOf("en:plant-based-foods", "en:vegetables", "en:fresh-vegetables")
        assertEquals(FoodCategory.SALAD_VEG, OpenFoodFactsCategories.categoryFor(tags))
    }

    @Test
    fun `recognises the chilled groups`() {
        assertEquals(
            FoodCategory.CHEESE,
            OpenFoodFactsCategories.categoryFor(listOf("en:dairies", "en:cheeses")),
        )
        assertEquals(
            FoodCategory.FISH,
            OpenFoodFactsCategories.categoryFor(listOf("en:seafood", "en:prawns")),
        )
        assertEquals(
            FoodCategory.MEAT,
            OpenFoodFactsCategories.categoryFor(listOf("en:meats", "en:poultry")),
        )
        assertEquals(
            FoodCategory.BAKERY,
            OpenFoodFactsCategories.categoryFor(listOf("en:breads")),
        )
    }

    @Test
    fun `cleaning products are not food`() {
        assertEquals(
            FoodCategory.NON_FOOD,
            OpenFoodFactsCategories.categoryFor(listOf("en:non-food-products", "en:cleaning")),
        )
    }

    @Test
    fun `language prefixes other than en still work`() {
        assertEquals(
            FoodCategory.FROZEN,
            OpenFoodFactsCategories.categoryFor(listOf("fr:surgeles", "de:frozen-foods")),
        )
    }

    @Test
    fun `unknown or empty tags fall through to the keyword classifier`() {
        assertNull(OpenFoodFactsCategories.categoryFor(emptyList()))
        assertNull(OpenFoodFactsCategories.categoryFor(listOf("en:something-we-dont-know")))
    }
}
