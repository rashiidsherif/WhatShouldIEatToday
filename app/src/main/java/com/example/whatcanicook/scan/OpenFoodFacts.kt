package com.example.whatcanicook.scan

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** What a barcode lookup told us about a product. */
data class ProductLookup(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val quantity: String?,
    /** Open Food Facts category tags, e.g. "en:fresh-vegetables". */
    val categoryTags: List<String>,
) {
    /** Best display name we can offer, or null if the lookup found nothing. */
    val displayName: String?
        get() {
            val base = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val b = brand?.trim()?.takeIf { it.isNotEmpty() }
            return if (b != null && !base.contains(b, ignoreCase = true)) "$b $base" else base
        }

    val found: Boolean get() = displayName != null
}

/**
 * Barcode lookup against Open Food Facts.
 *
 * Free, open data, no API key, good UK/EU grocery coverage. Own-brand
 * supermarket lines are patchier than national brands, so callers must handle
 * a miss by letting the user name the item themselves.
 *
 * Call from a background thread.
 */
object OpenFoodFacts {

    private const val BASE = "https://world.openfoodfacts.org/api/v2/product"
    private const val FIELDS = "product_name,product_name_en,brands,quantity,categories_tags"

    // Open Food Facts asks clients to identify themselves. Deliberately
    // generic: no user identity is sent to a third party.
    private const val USER_AGENT = "WhatCanICook/1.0 (Android)"

    /** Looks up [barcode]; returns null when the request itself failed. */
    fun lookup(barcode: String): ProductLookup? {
        val clean = barcode.trim().filter { it.isDigit() }
        if (clean.isEmpty()) return null

        var connection: HttpURLConnection? = null
        return try {
            val url = "$BASE/${URLEncoder.encode(clean, "UTF-8")}.json?fields=$FIELDS"
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            when (connection.responseCode) {
                200 -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8)
                        .use(BufferedReader::readText)
                    parse(clean, body)
                }
                // 404 is a normal "we don't have this product".
                404 -> ProductLookup(clean, null, null, null, emptyList())
                else -> null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    internal fun parse(barcode: String, body: String): ProductLookup {
        return try {
            val root = JSONObject(body)
            if (root.optInt("status", 0) != 1) {
                return ProductLookup(barcode, null, null, null, emptyList())
            }
            val p = root.optJSONObject("product")
                ?: return ProductLookup(barcode, null, null, null, emptyList())
            val tagsArray = p.optJSONArray("categories_tags")
            val tags = buildList {
                for (i in 0 until (tagsArray?.length() ?: 0)) {
                    add(tagsArray!!.optString(i))
                }
            }
            ProductLookup(
                barcode = barcode,
                name = p.optString("product_name_en").takeIf { it.isNotEmpty() }
                    ?: p.optString("product_name").takeIf { it.isNotEmpty() },
                brand = p.optString("brands").takeIf { it.isNotEmpty() }
                    ?.split(",")?.firstOrNull()?.trim(),
                quantity = p.optString("quantity").takeIf { it.isNotEmpty() },
                categoryTags = tags,
            )
        } catch (e: Exception) {
            ProductLookup(barcode, null, null, null, emptyList())
        }
    }
}
