package com.example.whatcanicook.lidl

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** A receipt as it appears in the list endpoint. */
data class TicketSummary(
    val id: String,
    val isoDate: String,
    val totalPence: Int?,
    val storeName: String?,
    val articlesCount: Int,
)

sealed class LidlResult<out T> {
    data class Ok<T>(val value: T) : LidlResult<T>()

    /** The session cookies are gone or stale; the user has to log in again. */
    data object NeedsLogin : LidlResult<Nothing>()
    data class Failed(val message: String) : LidlResult<Nothing>()
}

/**
 * Client for Lidl's cookie-authenticated receipt API.
 *
 *   GET /mre/api/v1/tickets?country=GB&page=N
 *   GET /mre/api/v1/tickets/{id}?country=GB&languageCode=en-GB
 *
 * Authentication is the first-party cookie jar from a signed-in
 * www.lidl.co.uk session, captured by [LidlLoginActivity].
 *
 * This endpoint returns the receipt as HTML, which [LidlReceiptParser] handles.
 * The Lidl Plus mobile API (tickets.lidlplus.com) returns structured JSON and
 * would be nicer, but it needs the app's own OAuth redirect scheme, which
 * collides with the real Lidl Plus app if it is installed.
 *
 * Call from a background thread.
 */
class LidlApi(
    private val cookieHeader: String,
    private val country: String = "GB",
    private val languageCode: String = "en-GB",
) {

    private fun get(url: String): LidlResult<String> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Cookie", cookieHeader)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Accept-Language", languageCode)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
            }
            when (val code = connection.responseCode) {
                // Explicit UTF-8: receipts carry non-ASCII product names
                // (SPÜLTUCH, crème…) and the JVM default charset mangles them.
                200 -> LidlResult.Ok(
                    connection.inputStream.bufferedReader(Charsets.UTF_8)
                        .use(BufferedReader::readText),
                )
                // A redirect here means we were bounced to the login page.
                401, 403, 302, 301 -> LidlResult.NeedsLogin
                else -> LidlResult.Failed("HTTP $code")
            }
        } catch (e: Exception) {
            LidlResult.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    private fun poundsToPence(value: Double): Int = Math.round(value * 100).toInt()

    /** One page of receipt summaries, newest first. Pages are 1-based. */
    fun fetchSummaryPage(page: Int): LidlResult<Pair<List<TicketSummary>, Int>> {
        val url = "$BASE/mre/api/v1/tickets?country=$country&page=$page"
        return when (val raw = get(url)) {
            is LidlResult.NeedsLogin -> LidlResult.NeedsLogin
            is LidlResult.Failed -> raw
            is LidlResult.Ok -> try {
                val root = JSONObject(raw.value)
                val items = root.optJSONArray("items")
                val out = ArrayList<TicketSummary>(items?.length() ?: 0)
                for (i in 0 until (items?.length() ?: 0)) {
                    val o = items!!.getJSONObject(i)
                    out.add(
                        TicketSummary(
                            id = o.optString("id"),
                            isoDate = o.optString("date"),
                            totalPence = if (o.has("totalAmount")) {
                                poundsToPence(o.optDouble("totalAmount", 0.0))
                            } else null,
                            storeName = o.optString("store").takeIf { it.isNotEmpty() },
                            articlesCount = o.optInt("articlesCount", 0),
                        ),
                    )
                }
                LidlResult.Ok(out to root.optInt("totalCount", out.size))
            } catch (e: Exception) {
                LidlResult.Failed("Bad summary JSON: ${e.message}")
            }
        }
    }

    /** A single receipt, parsed from its HTML body. */
    fun fetchReceipt(summary: TicketSummary): LidlResult<ParsedReceipt> {
        val url = "$BASE/mre/api/v1/tickets/${summary.id}" +
            "?country=$country&languageCode=$languageCode"
        return when (val raw = get(url)) {
            is LidlResult.NeedsLogin -> LidlResult.NeedsLogin
            is LidlResult.Failed -> raw
            is LidlResult.Ok -> try {
                val root = JSONObject(raw.value)
                val ticket = root.optJSONObject("ticket") ?: root
                val html = ticket.optString("htmlPrintedReceipt")
                    .ifEmpty { ticket.optString("htmlReceipt") }
                    .ifEmpty { root.optString("htmlPrintedReceipt") }
                if (html.isEmpty()) {
                    return LidlResult.Failed("Receipt ${summary.id} had no HTML body")
                }
                val storeName = ticket.optJSONObject("store")?.optString("name")
                    ?.takeIf { it.isNotEmpty() }
                    ?: summary.storeName
                LidlResult.Ok(
                    LidlReceiptParser.parse(
                        id = summary.id,
                        isoDate = ticket.optString("date").ifEmpty { summary.isoDate },
                        html = html,
                        storeNameFallback = storeName,
                        // ticket.totalAmount is always 0 on this endpoint, so the
                        // list response is the trustworthy source.
                        totalPenceFallback = summary.totalPence,
                    ),
                )
            } catch (e: Exception) {
                LidlResult.Failed("Bad receipt JSON: ${e.message}")
            }
        }
    }

    /** Summaries across pages, newest first, capped by [maxReceipts]. */
    fun fetchRecentSummaries(maxReceipts: Int = 20): LidlResult<List<TicketSummary>> {
        val collected = ArrayList<TicketSummary>()
        var page = 1
        while (collected.size < maxReceipts && page <= MAX_PAGES) {
            when (val result = fetchSummaryPage(page)) {
                is LidlResult.NeedsLogin -> return LidlResult.NeedsLogin
                is LidlResult.Failed -> return if (collected.isEmpty()) result
                else LidlResult.Ok(collected.take(maxReceipts))
                is LidlResult.Ok -> {
                    val (items, total) = result.value
                    if (items.isEmpty()) break
                    collected.addAll(items)
                    if (collected.size >= total) break
                    page++
                }
            }
        }
        return LidlResult.Ok(collected.take(maxReceipts))
    }

    companion object {
        const val BASE = "https://www.lidl.co.uk"
        const val ACCOUNT_URL = "$BASE/mla/"
        const val MAX_PAGES = 12
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/140.0.0.0 Mobile Safari/537.36"
    }
}
