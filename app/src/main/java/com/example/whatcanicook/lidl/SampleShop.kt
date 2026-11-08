package com.example.whatcanicook.lidl

import java.time.LocalDate

/**
 * A real Lidl shop, used to seed the Kitchen before the network import exists.
 *
 * These are the exact article descriptions, codes and prices from a receipt in
 * the export, so the Kitchen shows the same awkward names the real importer
 * has to cope with. The purchase date is relative to today so the expiry
 * buckets are actually exercised.
 */
object SampleShop {

    private data class Line(val artId: String, val desc: String, val pence: Int)

    private val LINES = listOf(
        Line("0080417", "Plums 1kg 0080417", 199),
        Line("0080761", "Sweet Potatoes 1kg 0080761", 119),
        Line("0084535", "Organic Cucumber 0084535", 105),
        Line("0097378", "Tomato Puree", 118),
        Line("6014362", "Wholewheat Fusilli", 75),
        Line("0000245", "Chicken Breast Fillets 1kg", 669),
        Line("0091234", "Peppermint Tea", 135),
        Line("0004455", "15 Free Range Eggs", 285),
        Line("0083311", "Sweet & Crunchy Salad", 78),
        Line("0083006", "Chestnut Mushrooms 0083006", 95),
        Line("0092001", "Brownies Baking Mix", 125),
        Line("0083399", "Brown Onions 0083399", 95),
        Line("6011122", "Brown Rice", 139),
        Line("0083450", "Iceberg Lettuce", 64),
        Line("0082400", "Cherry Tomatoes 0082400", 62),
        Line("0081011", "Fresh Cut Rosemary 0081011", 50),
        Line("0082411", "Baby Plum Tomatoes 0082411", 135),
        Line("0080244", "Red Apples 0080244", 99),
        Line("0093322", "Fine Seasalt Shaker", 99),
        Line("0082792", "Parsnips 0082792", 74),
        Line("0005566", "Creamy Greek Style Yogurt 1k", 149),
        Line("0080686", "Gold Kiwi 0080686", 380),
        Line("0082706", "Garlic 0082706", 45),
        Line("0000999", "Carrier Bag", 10),
    )

    /** The shop as if it happened three days ago. */
    fun recentShop(purchasedOn: LocalDate = LocalDate.now().minusDays(3)): ParsedReceipt =
        ParsedReceipt(
            id = "sample-recent-shop",
            purchasedOn = purchasedOn,
            storeName = "Hutcheon",
            totalPence = LINES.sumOf { it.pence },
            lines = LINES.map {
                ReceiptLine(
                    artId = it.artId,
                    description = it.desc,
                    quantity = 1.0,
                    unitPricePence = it.pence,
                    linePricePence = it.pence,
                    taxType = "A",
                )
            },
        )
}
