package com.example.whatcanicook.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.whatcanicook.domain.Confidence
import com.example.whatcanicook.domain.DateSource
import com.example.whatcanicook.domain.FoodCategory
import com.example.whatcanicook.domain.ItemOrigin
import com.example.whatcanicook.domain.KitchenItem
import java.time.LocalDate

/**
 * Storage for the kitchen.
 *
 * Uses the framework SQLite API rather than Room: Room needs an annotation
 * processor, and pinning a KSP build that matches this toolchain is a moving
 * target. The schema below is deliberately Room-shaped so it can be swapped
 * later without a data migration.
 */
class KitchenDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_ITEMS (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                name            TEXT    NOT NULL,
                normalised_name TEXT    NOT NULL,
                category        TEXT    NOT NULL,
                quantity        REAL    NOT NULL DEFAULT 1.0,
                purchased_on    TEXT    NOT NULL,
                expires_on      TEXT    NOT NULL,
                date_source     TEXT    NOT NULL,
                confidence      TEXT    NOT NULL,
                origin          TEXT    NOT NULL,
                art_id          TEXT,
                price_pence     INTEGER,
                store_name      TEXT,
                consumed        INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_items_expires ON $TABLE_ITEMS (expires_on)")
        db.execSQL("CREATE INDEX idx_items_consumed ON $TABLE_ITEMS (consumed)")
        db.execSQL(
            """
            CREATE TABLE $TABLE_RECEIPTS (
                receipt_id  TEXT PRIMARY KEY,
                imported_at TEXT NOT NULL,
                store_name  TEXT,
                total_pence INTEGER,
                line_count  INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // NEVER drop these tables. They hold items the user scanned in by
        // hand, which cannot be re-fetched from anywhere. Any future schema
        // change must be an additive ALTER TABLE here, keyed off oldVersion.
        // An unknown upgrade path leaves the data untouched rather than
        // rebuilding the schema.
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Installing an older build must not destroy data either.
    }

    companion object {
        const val DB_NAME = "kitchen.db"
        const val DB_VERSION = 1
        const val TABLE_ITEMS = "kitchen_items"
        const val TABLE_RECEIPTS = "imported_receipts"
    }
}

internal fun KitchenItem.toContentValues(): ContentValues = ContentValues().apply {
    put("name", name)
    put("normalised_name", normalisedName)
    put("category", category.name)
    put("quantity", quantity)
    put("purchased_on", purchasedOn.toString())
    put("expires_on", expiresOn.toString())
    put("date_source", dateSource.name)
    put("confidence", confidence.name)
    put("origin", origin.name)
    put("art_id", artId)
    if (pricePence != null) put("price_pence", pricePence) else putNull("price_pence")
    put("store_name", storeName)
    put("consumed", if (consumed) 1 else 0)
}

internal fun Cursor.toKitchenItem(): KitchenItem = KitchenItem(
    id = getLong(getColumnIndexOrThrow("id")),
    name = getString(getColumnIndexOrThrow("name")),
    normalisedName = getString(getColumnIndexOrThrow("normalised_name")),
    category = enumOrDefault(getString(getColumnIndexOrThrow("category")), FoodCategory.UNKNOWN),
    quantity = getDouble(getColumnIndexOrThrow("quantity")),
    purchasedOn = LocalDate.parse(getString(getColumnIndexOrThrow("purchased_on"))),
    expiresOn = LocalDate.parse(getString(getColumnIndexOrThrow("expires_on"))),
    dateSource = enumOrDefault(getString(getColumnIndexOrThrow("date_source")), DateSource.ESTIMATED),
    confidence = enumOrDefault(getString(getColumnIndexOrThrow("confidence")), Confidence.LOW),
    origin = enumOrDefault(getString(getColumnIndexOrThrow("origin")), ItemOrigin.MANUAL),
    artId = getColumnIndex("art_id").takeIf { it >= 0 && !isNull(it) }?.let { getString(it) },
    pricePence = getColumnIndex("price_pence").takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) },
    storeName = getColumnIndex("store_name").takeIf { it >= 0 && !isNull(it) }?.let { getString(it) },
    consumed = getInt(getColumnIndexOrThrow("consumed")) == 1,
)

private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
