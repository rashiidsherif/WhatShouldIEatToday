package com.example.whatcanicook.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.whatcanicook.ai.AiHandoff
import com.example.whatcanicook.ai.AiTarget
import com.example.whatcanicook.ai.HandoffResult
import com.example.whatcanicook.ai.MealPlanPrompt
import com.example.whatcanicook.data.KitchenRepository
import com.example.whatcanicook.domain.Confidence
import com.example.whatcanicook.domain.DateSource
import com.example.whatcanicook.domain.FoodCategory
import com.example.whatcanicook.domain.ItemOrigin
import com.example.whatcanicook.domain.KitchenItem
import com.example.whatcanicook.domain.KitchenSnapshot
import com.example.whatcanicook.domain.ProductNormaliser
import com.example.whatcanicook.domain.ShelfLifeTable
import com.example.whatcanicook.scan.OpenFoodFacts
import com.example.whatcanicook.lidl.LidlApi
import com.example.whatcanicook.lidl.LidlReceiptParser
import com.example.whatcanicook.lidl.LidlResult
import com.example.whatcanicook.lidl.LidlSession
import com.example.whatcanicook.lidl.SampleShop
import com.example.whatcanicook.lidl.TicketSummary
import java.time.LocalDate
import java.util.concurrent.Executors

/** An item being added by scan or by hand, before it is saved. */
data class PendingItem(
    val barcode: String?,
    val name: String,
    val category: FoodCategory,
    val confidence: Confidence,
    val addedOn: LocalDate,
    val note: String?,
)

/** A receipt offered in the import picker. */
data class ReceiptOption(
    val summary: TicketSummary,
    val date: LocalDate?,
    val alreadyImported: Boolean,
    val selected: Boolean,
) {
    val id: String get() = summary.id
}

class KitchenViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = KitchenRepository(app)
    private val session = LidlSession(app)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var snapshot by mutableStateOf(KitchenSnapshot(LocalDate.now(), emptyList()))
        private set

    var status by mutableStateOf<String?>(null)
        private set

    var isBusy by mutableStateOf(false)
        private set

    var isConnectedToLidl by mutableStateOf(false)
        private set

    var needsLogin by mutableStateOf(false)
        private set

    /** Receipts offered for import; empty means the picker is closed. */
    var receiptOptions by mutableStateOf<List<ReceiptOption>>(emptyList())
        private set

    var pickerVisible by mutableStateOf(false)
        private set

    init {
        isConnectedToLidl = session.isConnected
        refresh()
    }

    fun refresh() {
        snapshot = repository.snapshot()
    }

    // ------------------------------------------------------------------ login

    fun onLoginResult(connected: Boolean) {
        isConnectedToLidl = session.isConnected
        needsLogin = false
        if (connected && isConnectedToLidl) {
            status = "Connected to Lidl"
            openReceiptPicker()
        } else {
            status = "Lidl login cancelled"
        }
    }

    fun disconnectLidl() {
        session.clear()
        isConnectedToLidl = false
        status = "Disconnected from Lidl"
    }

    private fun handleExpiredSession() {
        needsLogin = true
        session.clear()
        isConnectedToLidl = false
        status = "Lidl session expired — log in again"
    }

    // ----------------------------------------------------------------- picker

    /**
     * Fetches the receipt list and shows the picker. Nothing is imported until
     * the user chooses — a receipt from months ago is shopping history, not
     * food still in the kitchen, so this has to be their call.
     */
    fun openReceiptPicker(maxReceipts: Int = 30, preselectWithinDays: Long = 21) {
        if (isBusy) return
        val cookie = session.cookieHeader
        if (cookie == null) {
            needsLogin = true
            status = "Connect to Lidl first"
            return
        }

        isBusy = true
        status = "Loading your shops…"

        io.execute {
            val api = LidlApi(cookie)
            when (val result = api.fetchRecentSummaries(maxReceipts)) {
                is LidlResult.NeedsLogin -> main.post {
                    isBusy = false
                    handleExpiredSession()
                }
                is LidlResult.Failed -> main.post {
                    isBusy = false
                    status = "Could not load shops: ${result.message}"
                }
                is LidlResult.Ok -> {
                    val cutoff = LocalDate.now().minusDays(preselectWithinDays)
                    val options = result.value.map { summary ->
                        val date = runCatching {
                            LidlReceiptParser.parseDate(summary.isoDate)
                        }.getOrNull()
                        val imported = repository.isReceiptImported(summary.id)
                        ReceiptOption(
                            summary = summary,
                            date = date,
                            alreadyImported = imported,
                            // Pre-tick only recent, not-yet-imported shops.
                            selected = !imported && date != null && !date.isBefore(cutoff),
                        )
                    }
                    main.post {
                        isBusy = false
                        receiptOptions = options
                        pickerVisible = true
                        status = if (options.isEmpty()) "No shops found" else null
                    }
                }
            }
        }
    }

    fun toggleReceipt(id: String) {
        receiptOptions = receiptOptions.map {
            if (it.id == id) it.copy(selected = !it.selected) else it
        }
    }

    fun selectAll(selected: Boolean) {
        receiptOptions = receiptOptions.map {
            if (it.alreadyImported) it else it.copy(selected = selected)
        }
    }

    fun closePicker() {
        pickerVisible = false
        receiptOptions = emptyList()
    }

    /** Downloads and imports exactly the receipts the user ticked. */
    fun importSelected() {
        if (isBusy) return
        val chosen = receiptOptions.filter { it.selected && !it.alreadyImported }
        if (chosen.isEmpty()) {
            status = "Nothing selected"
            return
        }
        val cookie = session.cookieHeader
        if (cookie == null) {
            needsLogin = true
            return
        }

        isBusy = true
        pickerVisible = false
        status = "Importing ${chosen.size} shops…"

        io.execute {
            val api = LidlApi(cookie)
            var imported = 0
            var receipts = 0
            var failure: String? = null
            var expired = false

            for (option in chosen) {
                when (val receipt = api.fetchReceipt(option.summary)) {
                    is LidlResult.NeedsLogin -> {
                        expired = true
                        break
                    }
                    is LidlResult.Failed -> failure = receipt.message
                    is LidlResult.Ok -> {
                        imported += repository.importReceipt(receipt.value)
                        receipts++
                    }
                }
            }

            main.post {
                isBusy = false
                receiptOptions = emptyList()
                when {
                    expired -> handleExpiredSession()
                    imported > 0 -> {
                        session.lastSyncedAt = System.currentTimeMillis()
                        status = "Imported $imported items from $receipts " +
                            if (receipts == 1) "shop" else "shops"
                    }
                    failure != null -> status = "Import failed: $failure"
                    else -> status = "Nothing new to import"
                }
                refresh()
            }
        }
    }

    // ------------------------------------------------------------- scan / add

    /**
     * A scanned or hand-entered item awaiting confirmation. Shown before it is
     * saved so the user can correct the name and, crucially, replace our
     * estimated date with the one printed on the packet.
     */
    var pending by mutableStateOf<PendingItem?>(null)
        private set

    var isLookingUp by mutableStateOf(false)
        private set

    private fun buildPending(
        name: String,
        barcode: String?,
        note: String?,
        today: LocalDate = LocalDate.now(),
    ): PendingItem {
        val c = ShelfLifeTable.classify(name, null)
        return PendingItem(
            barcode = barcode,
            name = name,
            category = c.category,
            confidence = c.confidence,
            addedOn = today,
            note = note,
        )
    }

    /**
     * Hands the kitchen to ChatGPT or Claude with a ready-written prompt.
     * Prefilled, never auto-sent, so it can be edited first.
     */
    fun askAssistant(context: android.content.Context, target: AiTarget) {
        if (snapshot.active.isEmpty()) {
            status = "Nothing in the kitchen to cook with yet"
            return
        }
        val prompt = MealPlanPrompt.build(snapshot)
        status = when (AiHandoff.launch(context, target, prompt)) {
            HandoffResult.OPENED_APP -> null
            HandoffResult.SHARED -> null
            HandoffResult.COPIED ->
                "${target.label.removePrefix("Ask ")} isn't installed — prompt copied instead"
        }
    }

    /** Called when the continuous scanner session ends. */
    fun onScanSessionFinished(added: Int) {
        status = when (added) {
            0 -> null
            1 -> "Added 1 item"
            else -> "Added $added items"
        }
        refresh()
    }

    /** Called with the raw barcode once a one-shot scan returns. */
    fun onBarcodeScanned(barcode: String) {
        isLookingUp = true
        status = null
        io.execute {
            val result = OpenFoodFacts.lookup(barcode)
            val name = result?.displayName
            main.post {
                isLookingUp = false
                pending = when {
                    name != null -> buildPending(name, barcode, "Found on Open Food Facts")
                    result != null -> buildPending(
                        "", barcode,
                        "Barcode $barcode isn't in Open Food Facts — name it yourself",
                    )
                    else -> buildPending(
                        "", barcode,
                        "Lookup failed — name it yourself",
                    )
                }
            }
        }
    }

    fun startManualAdd() {
        pending = buildPending("", null, "Type what you're adding")
    }

    fun updatePendingName(name: String) {
        val current = pending ?: return
        val c = ShelfLifeTable.classify(name, null)
        pending = current.copy(
            name = name,
            category = c.category,
            confidence = c.confidence,
        )
    }

    fun savePending() {
        val current = pending ?: return
        val name = current.name.trim()
        if (name.isEmpty()) {
            status = "Give the item a name first"
            return
        }
        repository.insert(
            KitchenItem(
                name = name,
                normalisedName = ProductNormaliser.normalise(name),
                category = current.category,
                purchasedOn = current.addedOn,
                // Unused by the UI; the column is NOT NULL. See KitchenItem.
                expiresOn = current.addedOn,
                dateSource = DateSource.ESTIMATED,
                confidence = current.confidence,
                origin = if (current.barcode != null) ItemOrigin.BARCODE else ItemOrigin.MANUAL,
                artId = current.barcode,
            ),
        )
        pending = null
        status = "Added $name"
        refresh()
    }

    fun cancelPending() {
        pending = null
    }

    fun onScanFailed(message: String) {
        status = message
    }

    // ------------------------------------------------------------------ local

    fun importSampleShop() {
        val receipt = SampleShop.recentShop()
        val added = repository.importReceipt(receipt)
        status = if (added > 0) {
            "Added $added sample items (not from Lidl)"
        } else {
            "Sample shop is already in the kitchen"
        }
        refresh()
    }

    fun clearKitchen() {
        repository.clear()
        status = "Kitchen cleared"
        refresh()
    }

    fun markEaten(item: KitchenItem) {
        repository.markConsumed(item.id, true)
        status = null
        refresh()
    }

    fun throwAway(item: KitchenItem) {
        repository.delete(item.id)
        status = null
        refresh()
    }

    override fun onCleared() {
        io.shutdown()
        super.onCleared()
    }
}
