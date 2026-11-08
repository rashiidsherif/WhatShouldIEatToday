package com.example.whatcanicook.lidl

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One article line lifted off a Lidl receipt. */
data class ReceiptLine(
    val artId: String,
    val description: String,
    val quantity: Double,
    val unitPricePence: Int,
    val linePricePence: Int,
    val taxType: String?,
)

/** A parsed receipt. */
data class ParsedReceipt(
    val id: String,
    val purchasedOn: LocalDate,
    val storeName: String?,
    val totalPence: Int?,
    val lines: List<ReceiptLine>,
)

/**
 * Parses the `ticket.htmlPrintedReceipt` blob returned by
 * `GET /mre/api/v1/tickets/{id}`.
 *
 * Deliberately regex-based rather than DOM-based so it runs as a plain JVM unit
 * test with no Android dependency. The receipt is a flat `<pre>`-style list of
 * spans, so there is no nesting to worry about.
 *
 * Notes from the real data:
 *  - always read the `data-art-*` attributes, never the rendered text, which is
 *    padded and truncated for a 40-column printer
 *  - weighed goods emit a second span with the same `data-art-id` and no
 *    `data-unit-price` ("3.130 kg @ £0.79/kg"); skipping those avoids
 *    double-counting
 *  - `ticket.totalAmount` is always 0, so the total comes from the HTML or from
 *    the list endpoint
 */
object LidlReceiptParser {

    private val ARTICLE_SPAN = Regex(
        """<span\b([^>]*\bclass="[^"]*\barticle\b[^"]*"[^>]*)>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val TOTAL_SPAN = Regex(
        """<span\b[^>]*\bid="purchase_summary_\d+"[^>]*>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val HEADER_SPAN = Regex(
        """<span\b[^>]*\bid="header_line_1"[^>]*>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val TAG = Regex("""<[^>]*>""")
    private val TRAILING_AMOUNT = Regex("""(-?\d+[.,]\d{2})\s*[A-Z]?\s*$""")
    private val STRICT_PRICE = Regex("""^-?£?\s*-?\d+(\.\d{1,2})?$""")

    private fun attribute(attrs: String, name: String): String? =
        Regex("""\b${Regex.escape(name)}="([^"]*)"""", RegexOption.IGNORE_CASE)
            .find(attrs)?.groupValues?.get(1)

    private fun unescape(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")

    private fun text(html: String): String =
        unescape(TAG.replace(html, "")).replace(' ', ' ').trim()

    /**
     * Pence from a price string. Strict on purpose: discount labels such as
     * "£5 off £35 spend" must not be read as a number.
     */
    internal fun toPence(raw: String?): Int? {
        val s = raw?.trim()?.replace("£", "")?.trim() ?: return null
        if (!STRICT_PRICE.matches(s)) return null
        val value = s.toDoubleOrNull() ?: return null
        return Math.round(value * 100).toInt()
    }

    fun parseLines(html: String): List<ReceiptLine> =
        ARTICLE_SPAN.findAll(html).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val unitPrice = toPence(attribute(attrs, "data-unit-price"))
            // No unit price means a weight-continuation row; the real line
            // was already captured above it.
                ?: return@mapNotNull null
            val artId = attribute(attrs, "data-art-id")?.trim().orEmpty()
            val body = text(match.groupValues[2])
            val description = attribute(attrs, "data-art-description")
                ?.let { unescape(it).trim() }
                ?.takeIf { it.isNotEmpty() }
                ?: body.replace(TRAILING_AMOUNT, "").trim()
            val quantity = attribute(attrs, "data-art-quantity")
                ?.trim()?.toDoubleOrNull() ?: 1.0
            val lineTotal = TRAILING_AMOUNT.find(body)
                ?.groupValues?.get(1)?.replace(',', '.')
                ?.let { toPence(it) }
                ?: Math.round(unitPrice * quantity).toInt()

            ReceiptLine(
                artId = artId,
                description = description,
                quantity = quantity,
                unitPricePence = unitPrice,
                linePricePence = lineTotal,
                taxType = attribute(attrs, "data-tax-type"),
            )
        }.toList()

    /** The bold TOTAL from the summary block, in pence, or null. */
    fun parseTotalPence(html: String): Int? =
        TOTAL_SPAN.findAll(html)
            .map { text(it.groupValues[1]) }
            .mapNotNull { body -> TRAILING_AMOUNT.find(body)?.groupValues?.get(1) }
            .mapNotNull { toPence(it.replace(',', '.')) }
            .firstOrNull()

    fun parseStoreName(html: String): String? =
        HEADER_SPAN.find(html)?.let { text(it.groupValues[1]) }?.takeIf { it.isNotEmpty() }

    /**
     * @param isoDate the `date` field from the API, e.g. `2026-10-23T14:20:25`
     *                or `2026-10-23T14:20:25+00:00`
     */
    fun parseDate(isoDate: String): LocalDate = runCatching {
        LocalDateTime.parse(isoDate.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .toLocalDate()
    }.getOrElse { LocalDate.parse(isoDate.substring(0, 10)) }

    fun parse(
        id: String,
        isoDate: String,
        html: String,
        storeNameFallback: String? = null,
        totalPenceFallback: Int? = null,
    ): ParsedReceipt = ParsedReceipt(
        id = id,
        purchasedOn = parseDate(isoDate),
        storeName = parseStoreName(html) ?: storeNameFallback,
        totalPence = parseTotalPence(html) ?: totalPenceFallback,
        lines = parseLines(html),
    )
}
