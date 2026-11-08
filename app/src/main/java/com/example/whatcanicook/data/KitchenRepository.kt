package com.example.whatcanicook.data

import android.content.ContentValues
import android.content.Context
import com.example.whatcanicook.domain.KitchenItem
import com.example.whatcanicook.domain.KitchenSnapshot
import com.example.whatcanicook.lidl.ParsedReceipt
import com.example.whatcanicook.lidl.ReceiptImporter
import java.time.LocalDate

/**
 * Reads and writes the kitchen.
 *
 * Calls are synchronous. The working set is a few hundred rows, so this is
 * fine for now; when the kitchen grows or sync arrives, move these behind
 * coroutines (and probably Room) without changing the call sites.
 */
class KitchenRepository(context: Context) {

    private val helper = KitchenDatabase(context)

    fun allItems(includeConsumed: Boolean = false): List<KitchenItem> {
        val where = if (includeConsumed) null else "consumed = 0"
        helper.readableDatabase.query(
            KitchenDatabase.TABLE_ITEMS, null, where, null, null, null, "expires_on ASC",
        ).use { cursor ->
            val out = ArrayList<KitchenItem>(cursor.count)
            while (cursor.moveToNext()) out.add(cursor.toKitchenItem())
            return out
        }
    }

    fun snapshot(today: LocalDate = LocalDate.now()): KitchenSnapshot =
        KitchenSnapshot(today = today, items = allItems())

    fun insert(item: KitchenItem): Long =
        helper.writableDatabase.insert(KitchenDatabase.TABLE_ITEMS, null, item.toContentValues())

    fun insertAll(items: List<KitchenItem>): Int {
        if (items.isEmpty()) return 0
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            items.forEach { db.insert(KitchenDatabase.TABLE_ITEMS, null, it.toContentValues()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return items.size
    }

    fun markConsumed(id: Long, consumed: Boolean = true) {
        val values = ContentValues().apply { put("consumed", if (consumed) 1 else 0) }
        helper.writableDatabase.update(
            KitchenDatabase.TABLE_ITEMS, values, "id = ?", arrayOf(id.toString()),
        )
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete(KitchenDatabase.TABLE_ITEMS, "id = ?", arrayOf(id.toString()))
    }

    fun clear() {
        helper.writableDatabase.delete(KitchenDatabase.TABLE_ITEMS, null, null)
        helper.writableDatabase.delete(KitchenDatabase.TABLE_RECEIPTS, null, null)
    }

    fun isReceiptImported(receiptId: String): Boolean {
        helper.readableDatabase.query(
            KitchenDatabase.TABLE_RECEIPTS, arrayOf("receipt_id"),
            "receipt_id = ?", arrayOf(receiptId), null, null, null, "1",
        ).use { return it.moveToFirst() }
    }

    /**
     * Imports a receipt, skipping it if it has been imported before. Returns
     * the number of kitchen items added (non-food lines are dropped).
     */
    fun importReceipt(receipt: ParsedReceipt): Int {
        if (isReceiptImported(receipt.id)) return 0
        val items = ReceiptImporter.toKitchenItems(receipt)
        insertAll(items)
        helper.writableDatabase.insert(
            KitchenDatabase.TABLE_RECEIPTS, null,
            ContentValues().apply {
                put("receipt_id", receipt.id)
                put("imported_at", LocalDate.now().toString())
                put("store_name", receipt.storeName)
                if (receipt.totalPence != null) put("total_pence", receipt.totalPence)
                put("line_count", items.size)
            },
        )
        return items.size
    }

    fun itemCount(): Int =
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${KitchenDatabase.TABLE_ITEMS}", null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
