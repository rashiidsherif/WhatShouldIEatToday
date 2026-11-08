package com.example.whatcanicook.lidl

import com.example.whatcanicook.domain.ItemOrigin
import com.example.whatcanicook.domain.KitchenItem

/** Turns a parsed receipt into kitchen items, dropping anything that isn't food. */
object ReceiptImporter {

    fun toKitchenItems(receipt: ParsedReceipt): List<KitchenItem> =
        receipt.lines.flatMap { line -> toKitchenItems(line, receipt) }

    private fun toKitchenItems(line: ReceiptLine, receipt: ParsedReceipt): List<KitchenItem> {
        // A line for 3 yogurts is 3 things in the kitchen that get eaten
        // separately, so split whole quantities out into their own items.
        val whole = line.quantity.takeIf { it >= 1.0 && it % 1.0 == 0.0 }?.toInt() ?: 1
        val perItemPence = if (whole > 0) line.linePricePence / whole else line.linePricePence

        return (1..whole).mapNotNull {
            KitchenItem.fromPurchase(
                name = line.description,
                purchasedOn = receipt.purchasedOn,
                origin = ItemOrigin.LIDL_RECEIPT,
                quantity = if (whole > 1) 1.0 else line.quantity,
                artId = line.artId.takeIf { id -> id.isNotEmpty() },
                pricePence = perItemPence,
                storeName = receipt.storeName,
            )
        }
    }
}
