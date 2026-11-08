package com.example.whatcanicook.domain

import java.text.Normalizer
import java.time.LocalDate

/**
 * Turns a supermarket product description into a food category and an estimated
 * shelf life.
 *
 * Receipts carry no expiry information at all — only a description and a
 * purchase date — so everything here is inference. Two facts from the real
 * receipt data drive the design:
 *
 *  1. Lidl appends the 7-digit article code to loose fresh produce
 *     ("Vine Tomatoes 0082465") and never to packaged goods. In a sample of
 *     445 line items this held in 141 of 141 cases, so it is a reliable
 *     "this is fresh produce" signal even when the name means nothing to us.
 *
 *  2. Descriptions are truncated and run together ("Low Fat Cottage Ch.",
 *     "6FrozenSalmonFillets", "Froz StirFry Veg Ori"), so matching has to cope
 *     with camel case, missing spaces and abbreviations.
 *
 * Matching is whole-word only. Substring matching is actively harmful here: it
 * classifies "Peppermint Tea" as a pepper.
 */
object ProductNormaliser {

    private val LOOSE_PRODUCE_CODE = Regex("""\s(\d{7})\s*$""")
    private val WEIGHT = Regex("""\b\d+(\.\d+)?\s?(kg|g|mg|ml|cl|l|pk|pack|pcs|pc)\b""")
    private val CAMEL = Regex("""([a-z])([A-Z])""")
    private val LETTER_DIGIT = Regex("""([A-Za-z])(\d)""")
    private val DIGIT_LETTER = Regex("""(\d)([A-Za-z])""")
    private val NON_ALPHA = Regex("""[^a-z ]""")
    private val SPACES = Regex("""\s+""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")

    /**
     * The trailing article code Lidl prints on loose produce, or null.
     * Pass [artId] to require that the code actually matches the line's
     * article id, which is what makes this signal trustworthy.
     */
    fun looseProduceCode(raw: String, artId: String? = null): String? {
        val code = LOOSE_PRODUCE_CODE.find(raw.trim())?.groupValues?.get(1) ?: return null
        if (artId != null && artId.trim() != code) return null
        return code
    }

    /** Lower-cased, de-abbreviated, weight-stripped form used for matching. */
    fun normalise(raw: String): String {
        var s = LOOSE_PRODUCE_CODE.replace(raw.trim(), "")
        // Fold accents to their base letters BEFORE stripping non-letters,
        // otherwise "Purée" loses the é entirely and becomes "pur e", so
        // \bpuree\b never matches and tomato purée is read as a fresh tomato.
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
        s = COMBINING_MARKS.replace(s, "")
        s = CAMEL.replace(s) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        s = LETTER_DIGIT.replace(s) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        s = DIGIT_LETTER.replace(s) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        s = s.lowercase()
        s = WEIGHT.replace(s, " ")
        s = NON_ALPHA.replace(s, " ")
        return SPACES.replace(s, " ").trim()
    }
}

/** One keyword rule. [phrases] are matched as whole words against the normalised name. */
data class ShelfLifeRule(
    val category: FoodCategory,
    val days: Int,
    val phrases: List<String>,
) {
    private val patterns: List<Regex> = phrases.map { phrase ->
        Regex("""\b${Regex.escape(phrase)}\b""")
    }

    fun matches(normalised: String): Boolean = patterns.any { it.containsMatchIn(normalised) }
}

/** The result of classifying a product description. */
data class Classification(
    val category: FoodCategory,
    val shelfLifeDays: Int,
    val confidence: Confidence,
) {
    val isFood: Boolean get() = category != FoodCategory.NON_FOOD
}

object ShelfLifeTable {

    private fun rule(category: FoodCategory, days: Int, vararg phrases: String) =
        ShelfLifeRule(category, days, phrases.toList())

    /**
     * Ordered most-specific first — the first match wins. "tomato puree" must be
     * tested before "tomato", "peppermint" before "pepper", and anything frozen
     * before the fresh version of the same food.
     */
    val rules: List<ShelfLifeRule> = listOf(
        // --- not food at all -------------------------------------------------
        rule(
            FoodCategory.NON_FOOD, 0,
            "carrier bag", "carrier", "heavy duty bag", "bag for life", "flexi loop",
            "washing", "detergent", "sponge", "foil", "cling film", "bin liner",
            "toilet roll", "kitchen towel", "shampoo", "soap", "toothpaste",
            "battery", "batteries", "candle",
            // Lidl UK receipts carry some German article names.
            "spultuch", "sp ltuch", "tuch", "cloth", "scourer",
            // Toiletries and household, which otherwise land in "Unsorted"
            // and clutter the list of food to eat.
            "handwash", "hand wash", "anti bac", "antibac", "sanitiser",
            "pads", "tampons", "razor", "razors", "deodorant", "wipes",
            "nappies", "toothbrush", "floss", "tissues", "cotton",
            "bleach", "polish", "freshener", "dishwasher", "rinse aid",
        ),

        // --- frozen beats everything fresh ------------------------------------
        rule(
            FoodCategory.FROZEN, 180,
            "frozen", "froz", "ice cream", "ice lolly",
        ),

        // --- jars and sauces, before the vegetable they are named after -------
        rule(
            FoodCategory.PANTRY, 365,
            "sauce", "pesto", "chutney", "pickle", "relish", "salsa",
        ),

        // --- ambient goods whose names collide with fresh foods ----------------
        // Tins, jars and dried goods must be tested before the fresh version
        // of the same ingredient, or "chopped tomatoes" reads as a salad.
        rule(
            FoodCategory.PANTRY, 365,
            "chopped tomatoes", "peeled tomatoes", "plum tomatoes in",
            "tomatoes in tomato juice", "tomato juice", "sundried", "sun dried",
            "dried", "batts", "chickpeas in", "beans in",
        ),
        rule(
            FoodCategory.PANTRY, 365,
            "peppermint", "tea", "coffee", "puree", "passata", "ketchup",
            "tinned", "tin", "canned", "jar", "chunks in water", "chunks in oil",
            "baking mix", "porridge", "oats", "cereal", "seasalt", "sea salt",
            "salt", "stock cube", "gravy", "vinegar", "olive oil", "oil",
            "honey", "jam", "peanut butter", "chocolate", "biscuit", "crisps",
            "crackers", "sugar", "flour", "rice", "pasta", "fusilli", "spaghetti",
            "penne", "macaroni", "noodles", "couscous", "lentils", "chickpeas",
            "kidney beans", "baked beans", "sweetcorn", "ajvar", "mayonnaise",
            // Bare "sauce" matters: "Peppers & Walnut Sauce" is a jar, not a
            // salad vegetable, and "pepper" would otherwise claim it.
            "sauce", "pesto", "chutney", "pickle", "relish",
            "mustard", "soy sauce", "spice", "herbs dried", "water", "cola",
            "juice", "squash drink", "wine", "beer",
        ),

        // --- proteins ----------------------------------------------------------
        rule(
            FoodCategory.FISH, 2,
            "salmon", "cod", "haddock", "tuna steak", "prawn", "prawns", "mackerel",
            "sea bass", "seabass", "trout", "sardine", "fish fillet", "fish",
        ),
        rule(
            FoodCategory.MEAT, 3,
            "chicken", "beef", "pork", "lamb", "turkey", "mince", "steak",
            "sausage", "sausages", "bacon", "gammon", "duck", "fillets",
        ),

        // --- dairy and chilled --------------------------------------------------
        rule(
            FoodCategory.CHEESE, 21,
            "cheese", "cheddar", "brie", "feta", "halloumi", "mozzarella",
            "parmesan", "machego", "manchego", "gouda", "camembert", "cottage",
        ),
        rule(
            FoodCategory.EGGS, 21,
            "eggs", "egg",
        ),
        rule(
            FoodCategory.DAIRY, 7,
            "milk", "yogurt", "yoghurt", "cream", "creme fraiche", "butter",
            "custard", "mousse", "pudding",
        ),
        rule(
            FoodCategory.CHILLED_READY, 6,
            "pastry", "shortcrust", "puff", "hummus", "houmous", "dip", "pate",
            "ham", "chorizo", "salami", "coleslaw", "tofu", "falafel",
            "ready meal", "lasagne", "pizza fresh", "soup fresh",
        ),
        rule(
            FoodCategory.BAKERY, 4,
            "bread", "loaf", "roll", "rolls", "bagel", "baguette", "croissant",
            "pain au chocolat", "brioche", "wrap", "wraps", "pitta", "naan",
            "muffin", "doughnut", "cake",
        ),

        // --- fresh produce -------------------------------------------------------
        rule(
            FoodCategory.HERBS, 5,
            "rosemary", "thyme", "basil", "coriander", "parsley", "mint", "dill",
            "chives", "sage", "oregano fresh",
        ),
        rule(
            FoodCategory.LEAFY_GREENS, 4,
            "salad", "lettuce", "iceberg", "spinach", "rocket", "kale",
            "pak choi", "watercress", "mixed leaves",
        ),
        rule(
            FoodCategory.SOFT_FRUIT, 4,
            "strawberries", "strawberry", "raspberries", "raspberry",
            "blueberries", "blueberry", "blackberries", "cherries", "figs",
            "banana", "bananas", "avocado", "avocados",
        ),
        rule(
            FoodCategory.SALAD_VEG, 6,
            "tomato", "tomatoes", "cucumber", "pepper", "peppers", "mushroom",
            "mushrooms", "courgette", "aubergine", "spring onions", "celery",
            "mangetout", "sugar snap", "green beans", "asparagus",
        ),
        rule(
            FoodCategory.BRASSICA, 7,
            "broccoli", "cauliflower", "cabbage", "brussels", "leek", "leeks",
            "peas", "sprouts",
        ),
        rule(
            FoodCategory.FRUIT, 8,
            "plum", "plums", "grapes", "grape", "peach", "nectarine", "nectarines",
            "pear", "pears", "kiwi", "mango", "melon", "pineapple", "persimmon",
            "apricot", "papaya", "passion fruit",
        ),
        rule(
            FoodCategory.FRUIT, 14,
            "apple", "apples", "orange", "oranges", "lemon", "lemons", "lime",
            "limes", "clementine", "satsuma", "grapefruit",
        ),
        rule(
            FoodCategory.ROOT_VEG, 25,
            "potato", "potatoes", "onion", "onions", "garlic", "ginger",
            "carrot", "carrots", "parsnip", "parsnips", "swede", "turnip",
            "beetroot", "squash", "pumpkin", "sweet potato",
        ),
    )

    /**
     * Classify a raw receipt description.
     *
     * @param raw the description exactly as printed
     * @param artId the line's article id, used to validate the loose-produce code
     */
    fun classify(raw: String, artId: String? = null): Classification {
        val normalised = ProductNormaliser.normalise(raw)

        rules.firstOrNull { it.matches(normalised) }?.let { hit ->
            return Classification(hit.category, hit.days, Confidence.HIGH)
        }

        // Nothing matched by name. If Lidl tagged it with a loose-produce code
        // we still know it is fresh produce, which is the fact that matters.
        if (ProductNormaliser.looseProduceCode(raw, artId) != null) {
            return Classification(
                FoodCategory.UNKNOWN,
                FoodCategory.SALAD_VEG.defaultShelfLifeDays,
                Confidence.MEDIUM,
            )
        }

        return Classification(
            FoodCategory.UNKNOWN,
            FoodCategory.UNKNOWN.defaultShelfLifeDays,
            Confidence.LOW,
        )
    }
}

/** Applies a [Classification] to a purchase date to get an estimated use-by. */
object ExpiryEstimator {
    fun estimate(purchasedOn: LocalDate, classification: Classification): LocalDate =
        purchasedOn.plusDays(classification.shelfLifeDays.toLong())
}
