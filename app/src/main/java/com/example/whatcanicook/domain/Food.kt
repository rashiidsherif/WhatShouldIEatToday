package com.example.whatcanicook.domain

/**
 * Broad food groups. Each carries a default shelf life used when we know the
 * group but have no more specific rule, and a flag for whether the packaging
 * normally carries a printed date (which the user can scan or type to override
 * our estimate).
 */
enum class FoodCategory(
    val label: String,
    val defaultShelfLifeDays: Int,
    val usuallyDated: Boolean,
) {
    LEAFY_GREENS("Leafy greens", 4, usuallyDated = true),
    SOFT_FRUIT("Soft fruit", 4, usuallyDated = false),
    FRUIT("Fruit", 8, usuallyDated = false),
    SALAD_VEG("Salad veg", 6, usuallyDated = false),
    BRASSICA("Greens", 7, usuallyDated = false),
    ROOT_VEG("Root veg", 25, usuallyDated = false),
    HERBS("Fresh herbs", 5, usuallyDated = false),
    MEAT("Meat", 3, usuallyDated = true),
    FISH("Fish", 2, usuallyDated = true),
    DAIRY("Dairy", 7, usuallyDated = true),
    CHEESE("Cheese", 21, usuallyDated = true),
    EGGS("Eggs", 21, usuallyDated = true),
    BAKERY("Bakery", 4, usuallyDated = true),
    CHILLED_READY("Chilled", 6, usuallyDated = true),
    FROZEN("Frozen", 180, usuallyDated = true),
    PANTRY("Cupboard", 365, usuallyDated = true),
    NON_FOOD("Not food", 0, usuallyDated = false),
    UNKNOWN("Unsorted", 7, usuallyDated = false);

    val isPerishable: Boolean
        get() = this != PANTRY && this != FROZEN && this != NON_FOOD
}

/** Where an item's expiry date came from. Drives how much we trust it. */
enum class DateSource {
    /** Read off the packaging by the user (scan or manual entry). Trusted. */
    PRINTED,

    /** Supplied by a product database lookup after a barcode scan. */
    LOOKUP,

    /** Derived from purchase date + shelf-life table. A guess. */
    ESTIMATED,
}

/** How the item got into the kitchen. */
enum class ItemOrigin { MANUAL, BARCODE, LIDL_RECEIPT }

/** How much faith to put in an estimate. */
enum class Confidence {
    /** Matched an explicit keyword rule. */
    HIGH,

    /** Lidl's loose-produce code says it's fresh produce, but we can't name it. */
    MEDIUM,

    /** Nothing matched; generic fallback. */
    LOW,
}

// A Freshness/urgency enum used to live here. Expiry tracking was removed:
// guessing a date for everything produced nonsense (a jar of Nutella "expiring"
// in seven days), and a wrong date is worse than no date. The Kitchen is now an
// inventory of what you have. The shelf-life table is still used, but only to
// sort items into categories.
