package com.example.whatcanicook.domain

import java.time.LocalDate

/**
 * One thing in the kitchen.
 *
 * [expiresOn] and [dateSource] are still written, because the database columns
 * are NOT NULL and the data is cheap to keep, but nothing reads them any more.
 * Expiry tracking was removed: estimating a date for every product produced
 * nonsense for anything the classifier didn't recognise, and a confidently
 * wrong date is worse than no date at all. The columns stay so the feature can
 * come back without a migration.
 */
data class KitchenItem(
    val id: Long = 0L,
    val name: String,
    val normalisedName: String,
    val category: FoodCategory,
    val quantity: Double = 1.0,
    val purchasedOn: LocalDate,
    val expiresOn: LocalDate,
    val dateSource: DateSource,
    val confidence: Confidence,
    val origin: ItemOrigin,
    val artId: String? = null,
    val pricePence: Int? = null,
    val storeName: String? = null,
    val consumed: Boolean = false,
) {
    companion object {
        /**
         * Build an item from a purchase line. Returns null for non-food lines
         * such as carrier bags and cleaning products.
         */
        fun fromPurchase(
            name: String,
            purchasedOn: LocalDate,
            origin: ItemOrigin,
            quantity: Double = 1.0,
            artId: String? = null,
            pricePence: Int? = null,
            storeName: String? = null,
        ): KitchenItem? {
            val classification = ShelfLifeTable.classify(name, artId)
            if (!classification.isFood) return null
            return KitchenItem(
                name = name.trim(),
                normalisedName = ProductNormaliser.normalise(name),
                category = classification.category,
                quantity = quantity,
                purchasedOn = purchasedOn,
                expiresOn = purchasedOn,
                dateSource = DateSource.ESTIMATED,
                confidence = classification.confidence,
                origin = origin,
                artId = artId,
                pricePence = pricePence,
                storeName = storeName,
            )
        }
    }
}

/**
 * Everything in the kitchen, grouped into categories for display.
 *
 * Categories are alphabetical by label, and items within a category are
 * alphabetical by name, so the same thing is always in the same place.
 */
data class KitchenSnapshot(
    val today: LocalDate,
    val items: List<KitchenItem>,
) {
    val active: List<KitchenItem> = items.filterNot { it.consumed }

    val byCategory: List<Pair<FoodCategory, List<KitchenItem>>> =
        active.groupBy { it.category }
            .entries
            .sortedBy { it.key.label.lowercase() }
            .map { entry ->
                entry.key to entry.value.sortedBy { it.name.lowercase() }
            }

    val categoryCount: Int = byCategory.size
}
