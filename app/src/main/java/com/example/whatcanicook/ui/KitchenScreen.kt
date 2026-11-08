package com.example.whatcanicook.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatcanicook.ai.AiTarget
import com.example.whatcanicook.domain.FoodCategory
import com.example.whatcanicook.domain.KitchenItem
import com.example.whatcanicook.domain.KitchenSnapshot

/** A glyph per category, so sections are scannable without reading. */
private fun FoodCategory.glyph(): String = when (this) {
    FoodCategory.LEAFY_GREENS -> "🥬"
    FoodCategory.SOFT_FRUIT -> "🍓"
    FoodCategory.FRUIT -> "🍎"
    FoodCategory.SALAD_VEG -> "🥒"
    FoodCategory.BRASSICA -> "🥦"
    FoodCategory.ROOT_VEG -> "🥕"
    FoodCategory.HERBS -> "🌿"
    FoodCategory.MEAT -> "🥩"
    FoodCategory.FISH -> "🐟"
    FoodCategory.DAIRY -> "🥛"
    FoodCategory.CHEESE -> "🧀"
    FoodCategory.EGGS -> "🥚"
    FoodCategory.BAKERY -> "🍞"
    FoodCategory.CHILLED_READY -> "🥙"
    FoodCategory.FROZEN -> "🧊"
    FoodCategory.PANTRY -> "🥫"
    FoodCategory.NON_FOOD -> "🧼"
    FoodCategory.UNKNOWN -> "📦"
}

@Composable
fun KitchenScreen(
    snapshot: KitchenSnapshot,
    status: String?,
    isSyncing: Boolean,
    isConnectedToLidl: Boolean,
    onConnectLidl: () -> Unit,
    onChooseShops: () -> Unit,
    onScan: () -> Unit,
    onAddManually: () -> Unit,
    onAskAi: (AiTarget) -> Unit,
    onImportSample: () -> Unit,
    onClear: () -> Unit,
    onEaten: (KitchenItem) -> Unit,
    onBinned: (KitchenItem) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                KitchenHeader(
                    snapshot = snapshot,
                    status = status,
                    isSyncing = isSyncing,
                    isConnectedToLidl = isConnectedToLidl,
                    onConnectLidl = onConnectLidl,
                    onChooseShops = onChooseShops,
                    onScan = onScan,
                    onAddManually = onAddManually,
                    onAskAi = onAskAi,
                    onImportSample = onImportSample,
                    onClear = onClear,
                )
            }

            snapshot.byCategory.forEach { (category, entries) ->
                item(key = "header-${category.name}") {
                    SectionHeader(category, entries.size)
                }
                items(entries, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        onEaten = { onEaten(item) },
                        onBinned = { onBinned(item) },
                    )
                }
            }

            if (snapshot.active.isEmpty()) {
                item { EmptyKitchen() }
            }
        }
    }
}

@Composable
private fun EmptyKitchen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🥣", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Your kitchen is empty",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Scan a few things, or import a Lidl shop.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun KitchenHeader(
    snapshot: KitchenSnapshot,
    status: String?,
    isSyncing: Boolean,
    isConnectedToLidl: Boolean,
    onConnectLidl: () -> Unit,
    onChooseShops: () -> Unit,
    onScan: () -> Unit,
    onAddManually: () -> Unit,
    onAskAi: (AiTarget) -> Unit,
    onImportSample: () -> Unit,
    onClear: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear the whole kitchen?") },
            text = {
                Text(
                    "This deletes all ${snapshot.active.size} items, including " +
                        "everything you scanned in by hand. It cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClear()
                }) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Keep it") }
            },
        )
    }

    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Kitchen",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${snapshot.active.size} items · ${snapshot.categoryCount} categories",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )

        // The payoff of the whole app, so it gets to be the hero card.
        if (snapshot.active.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "What should I eat today?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Send your kitchen over and get a plan for today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiTarget.entries.forEach { target ->
                            Button(
                                onClick = { onAskAi(target) },
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(12.dp),
                            ) { Text(target.label) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // Scan hard left, the heavier Lidl import hard right, so a reach for
        // one cannot land on the other.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onScan,
                enabled = !isSyncing,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) { Text("Scan items") }

            if (isConnectedToLidl) {
                OutlinedButton(
                    onClick = onChooseShops,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(12.dp),
                ) { Text(if (isSyncing) "Working…" else "Import shops") }
            } else {
                OutlinedButton(
                    onClick = onConnectLidl,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Connect Lidl") }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onAddManually,
                enabled = !isSyncing,
                shape = RoundedCornerShape(12.dp),
            ) { Text("Add by hand") }
            TextButton(onClick = { showClearConfirm = true }, enabled = !isSyncing) {
                Text("Clear kitchen", color = MaterialTheme.colorScheme.error)
            }
        }

        if (isSyncing) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }

        if (status != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(2.dp))
        TextButton(
            onClick = onImportSample,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        ) {
            Text(
                "Add sample shop (offline test data)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun SectionHeader(category: FoodCategory, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    ) {
        Text(category.glyph(), fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            category.label.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .background(
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 7.dp, vertical = 1.dp),
        ) {
            Text(
                "$count",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun ItemRow(
    item: KitchenItem,
    onEaten: () -> Unit,
    onBinned: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp),
            )
            TextButton(onClick = onEaten) {
                Text("Used", fontSize = 13.sp)
            }
            TextButton(onClick = onBinned) {
                Text(
                    "Remove",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
        }
    }
}
