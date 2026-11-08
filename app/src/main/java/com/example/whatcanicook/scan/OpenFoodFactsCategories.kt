package com.example.whatcanicook.scan

import com.example.whatcanicook.domain.FoodCategory

/**
 * Maps Open Food Facts `categories_tags` onto our food categories.
 *
 * This exists because keyword matching on the product name keeps mistaking the
 * processed form of an ingredient for the fresh one: tomato purée and tinned
 * tomatoes read as fresh tomatoes, dried basil reads as a fresh herb. No amount
 * of extra keywords fixes the class of error, only individual instances of it.
 *
 * Open Food Facts already knows, because its tags are hierarchical and carry
 * the preparation: a tin of chopped tomatoes is tagged `en:canned-tomatoes` as
 * well as `en:tomatoes`. So preparation tags are checked first and win.
 *
 * Tags are user-contributed data, not instructions, and unknown tags simply
 * fall through to the keyword classifier.
 */
object OpenFoodFactsCategories {

    /**
     * Ordered: preparation beats ingredient. "Frozen peas" carries both
     * `en:frozen-foods` and `en:peas`, and the first is the one that matters.
     */
    private val RULES: List<Pair<List<String>, FoodCategory>> = listOf(
        // --- not food --------------------------------------------------------
        listOf(
            "non-food", "hygiene", "cleaning", "household", "cosmetics",
            "detergent", "toiletries", "paper-products",
        ) to FoodCategory.NON_FOOD,

        // --- preparation overrides the base ingredient -----------------------
        listOf("frozen") to FoodCategory.FROZEN,
        listOf(
            "canned", "tinned", "preserved", "in-conserve", "jarred",
            "dried-", "dehydrated", "freeze-dried", "powdered", "instant-",
            "pickled", "marinated-in-jars",
        ) to FoodCategory.PANTRY,

        // --- ambient groceries ------------------------------------------------
        listOf(
            "sauces", "condiments", "spreads", "pasta", "rice", "cereals",
            "grains", "legumes", "pulses", "flours", "sugars", "salts",
            "spices", "herbs-and-spices", "oils", "vinegars", "snacks",
            "biscuits", "chocolates", "confectioneries", "beverages",
            "teas", "coffees", "waters", "juices", "soups", "breakfasts",
            "canned-foods", "groceries", "honeys", "jams", "nuts",
        ) to FoodCategory.PANTRY,

        // --- chilled ----------------------------------------------------------
        listOf("cheeses", "cheese") to FoodCategory.CHEESE,
        listOf("eggs") to FoodCategory.EGGS,
        listOf(
            "dairies", "milks", "yogurts", "yoghurts", "creams", "butters",
            "fermented-milk-products", "desserts",
        ) to FoodCategory.DAIRY,
        listOf(
            "fishes", "seafood", "fish", "shellfish", "prawns", "crustaceans",
            "smoked-fish", "molluscs",
        ) to FoodCategory.FISH,
        listOf(
            "meats", "poultry", "beef", "pork", "chicken", "lamb",
            "charcuterie", "sausages", "hams", "prepared-meats",
        ) to FoodCategory.MEAT,
        listOf(
            "breads", "viennoiserie", "pastries", "bakery", "cakes",
        ) to FoodCategory.BAKERY,
        listOf(
            "prepared-salads", "sandwiches", "ready-meals", "dips",
            "hummus", "tofu", "meat-analogues",
        ) to FoodCategory.CHILLED_READY,

        // --- fresh produce, only once nothing above matched --------------------
        listOf(
            "fresh-herbs", "aromatic-plants",
        ) to FoodCategory.HERBS,
        listOf(
            "leaf-vegetables", "salads", "lettuces", "spinaches", "cabbages-leaves",
        ) to FoodCategory.LEAFY_GREENS,
        listOf(
            "berries", "strawberries", "raspberries", "blueberries",
            "bananas", "avocados",
        ) to FoodCategory.SOFT_FRUIT,
        listOf(
            "root-vegetables", "tubers", "potatoes", "onions", "garlics",
            "carrots", "bulb-vegetables",
        ) to FoodCategory.ROOT_VEG,
        listOf(
            "broccoli", "cauliflowers", "cabbages", "brassica",
        ) to FoodCategory.BRASSICA,
        listOf(
            "tomatoes", "cucumbers", "peppers", "mushrooms", "courgettes",
            "aubergines", "fruit-vegetables",
        ) to FoodCategory.SALAD_VEG,
        listOf(
            "fruits", "citrus", "apples", "pears", "stone-fruits", "melons",
        ) to FoodCategory.FRUIT,
        listOf(
            "vegetables", "fresh-vegetables", "plant-based-foods",
        ) to FoodCategory.SALAD_VEG,
    )

    /**
     * The best category these tags imply, or null when nothing is recognised
     * (in which case the caller should fall back to keyword matching).
     */
    fun categoryFor(tags: List<String>): FoodCategory? {
        if (tags.isEmpty()) return null
        // Strip the language prefix: "en:canned-tomatoes" -> "canned-tomatoes".
        val cleaned = tags.mapNotNull { tag ->
            tag.substringAfter(':', tag).trim().lowercase().takeIf { it.isNotEmpty() }
        }
        if (cleaned.isEmpty()) return null

        for ((needles, category) in RULES) {
            if (cleaned.any { tag -> needles.any { tag.contains(it) } }) {
                return category
            }
        }
        return null
    }
}
