package com.example.whatcanicook.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.whatcanicook.data.KitchenRepository
import com.example.whatcanicook.domain.DateSource
import com.example.whatcanicook.domain.ItemOrigin
import com.example.whatcanicook.domain.KitchenItem
import com.example.whatcanicook.domain.ProductNormaliser
import com.example.whatcanicook.domain.ShelfLifeTable
import com.example.whatcanicook.ui.theme.WhatCanICookTheme
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Continuous barcode scanning.
 *
 * The camera stays open for a whole session so a kitchen can be worked through
 * in one go. Each recognised item is saved immediately and the preview keeps
 * running; the only way out is the X.
 *
 * When Open Food Facts doesn't know a barcode, the name prompt appears as a
 * dialog *over* the live preview rather than as a separate screen, so
 * cancelling it drops straight back into scanning.
 */
class ScannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhatCanICookTheme {
                ScannerRoot(
                    onFinished = { count ->
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(EXTRA_ADDED_COUNT, count),
                        )
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_ADDED_COUNT = "added_count"
        fun intent(context: Context) = Intent(context, ScannerActivity::class.java)
    }
}

/** A barcode waiting for the user to name it. */
private data class UnknownBarcode(val barcode: String, var typed: String = "")

@Composable
private fun ScannerRoot(onFinished: (Int) -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var denied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        denied = !granted
    }

    // The request must be a side effect. Launching it from the composable
    // body runs it during composition, where it can be discarded and
    // re-triggered, and the system prompt never reliably appears.
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Re-check on resume, so granting the permission in Settings and coming
    // back starts the camera without another trip through this screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = context.checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) {
                    hasPermission = true
                    denied = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        hasPermission -> ScannerScreen(onFinished)

        else -> Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    if (denied) {
                        "Camera access was turned down. Android won't ask again, " +
                            "so it has to be switched on in Settings."
                    } else {
                        "Waiting for camera permission…"
                    },
                    color = Color.White,
                )
                Spacer(Modifier.height(16.dp))
                if (denied) {
                    Button(onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    }) { Text("Open settings") }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = { onFinished(0) }) {
                    Text("Go back", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun ScannerScreen(onFinished: (Int) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val repository = remember { KitchenRepository(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val lookupExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeClient = remember { BarcodeScanning.getClient() }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }

    var addedCount by remember { mutableStateOf(0) }
    var lastAdded by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var unknown by remember { mutableStateOf<UnknownBarcode?>(null) }
    var typedName by remember { mutableStateOf("") }

    // Guards against the same barcode being read on dozens of consecutive
    // frames. Re-scanning the same product after the cooldown adds a second
    // item, which is what you want for two tins of the same beans.
    val lastSeen = remember { HashMap<String, Long>() }

    fun save(name: String, barcode: String?, tags: List<String> = emptyList()) {
        // Open Food Facts knows whether something is tinned, frozen or dried;
        // the name alone does not. Prefer its categories and fall back to
        // keyword matching only when it has nothing useful.
        val fromTags = OpenFoodFactsCategories.categoryFor(tags)
        val classification = ShelfLifeTable.classify(name, null)
        val today = LocalDate.now()
        repository.insert(
            KitchenItem(
                name = name.trim(),
                normalisedName = ProductNormaliser.normalise(name),
                category = fromTags ?: classification.category,
                purchasedOn = today,
                expiresOn = today,
                dateSource = DateSource.ESTIMATED,
                confidence = if (fromTags != null) {
                    com.example.whatcanicook.domain.Confidence.HIGH
                } else {
                    classification.confidence
                },
                origin = if (barcode != null) ItemOrigin.BARCODE else ItemOrigin.MANUAL,
                artId = barcode,
            ),
        )
    }

    fun handleBarcode(raw: String) {
        val now = System.currentTimeMillis()
        val previous = lastSeen[raw]
        if (previous != null && now - previous < COOLDOWN_MS) return
        lastSeen[raw] = now
        busy = true

        lookupExecutor.execute {
            val result = OpenFoodFacts.lookup(raw)
            val name = result?.displayName
            if (name != null) save(name, raw, result.categoryTags)
            android.os.Handler(context.mainLooper).post {
                if (name != null) {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
                    addedCount += 1
                    lastAdded = name
                    busy = false
                } else {
                    // Two short tones: recognised the barcode, not the product.
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
                    typedName = ""
                    unknown = UnknownBarcode(raw)
                    busy = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            lookupExecutor.shutdown()
            barcodeClient.close()
            tone.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val input = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees,
                        )
                        barcodeClient.process(input)
                            .addOnSuccessListener { codes ->
                                val code = codes.firstNotNullOfOrNull { b ->
                                    b.rawValue?.takeIf { it.isNotBlank() }
                                }
                                if (code != null) {
                                    android.os.Handler(ctx.mainLooper).post {
                                        if (unknown == null && !busy) handleBarcode(code)
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    } catch (_: Exception) {
                        // Nothing useful to do; the preview simply stays blank.
                    }
                }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        // Top bar: running count and the only exit.
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .background(Color(0xCC000000), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    if (addedCount == 0) "Scanning…" else "$addedCount added",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(
                Modifier
                    .background(Color(0xCC000000), RoundedCornerShape(20.dp)),
            ) {
                TextButton(onClick = { onFinished(addedCount) }) {
                    Text("✕  Done", color = Color.White, fontSize = 16.sp)
                }
            }
        }

        // Bottom status strip.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Text(
                    lastAdded?.let { "Added: $it" }
                        ?: "Point at a barcode. Keep scanning — tap Done when finished.",
                    color = Color.White,
                )
            }
        }

        // Naming prompt, over the live preview so cancelling resumes scanning.
        unknown?.let { pendingUnknown ->
            AlertDialog(
                onDismissRequest = { unknown = null },
                title = { Text("What is this?") },
                text = {
                    Column {
                        Text(
                            "Barcode ${pendingUnknown.barcode} isn't in Open Food Facts.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = typedName,
                            onValueChange = { typedName = it },
                            singleLine = true,
                            label = { Text("Name") },
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = typedName.isNotBlank(),
                        onClick = {
                            val name = typedName.trim()
                            lookupExecutor.execute { save(name, pendingUnknown.barcode) }
                            addedCount += 1
                            lastAdded = name
                            unknown = null
                        },
                    ) { Text("Add") }
                },
                // Cancel returns to the camera, it does not leave scanning.
                dismissButton = {
                    TextButton(onClick = { unknown = null }) { Text("Skip") }
                },
            )
        }
    }
}

private const val COOLDOWN_MS = 2500L
