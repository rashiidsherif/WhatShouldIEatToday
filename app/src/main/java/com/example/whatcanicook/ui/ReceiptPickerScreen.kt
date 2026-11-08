package com.example.whatcanicook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

/**
 * Lets the user pick which shops to pull in. Receipts go back years, and an
 * old one is shopping history rather than food still in the kitchen, so the
 * choice has to be theirs. Recent, not-yet-imported shops arrive pre-ticked.
 */
@Composable
fun ReceiptPickerScreen(
    options: List<ReceiptOption>,
    isBusy: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    val selectedCount = options.count { it.selected && !it.alreadyImported }
    val today = LocalDate.now()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Choose shops",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "${options.size} receipts on your Lidl account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSelectAll(true) }) { Text("Select all") }
                TextButton(onClick = { onSelectAll(false) }) { Text("Select none") }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options, key = { it.id }) { option ->
                    ReceiptRow(option, today, onToggle)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onImport,
                    enabled = !isBusy && selectedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (selectedCount == 0) "Import" else "Import $selectedCount " +
                            if (selectedCount == 1) "shop" else "shops",
                    )
                }
                OutlinedButton(onClick = onCancel, enabled = !isBusy) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    option: ReceiptOption,
    today: LocalDate,
    onToggle: (String) -> Unit,
) {
    val enabled = !option.alreadyImported
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle(option.id) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = option.selected && enabled,
                onCheckedChange = if (enabled) ({ onToggle(option.id) }) else null,
                enabled = enabled,
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    option.date?.format(DATE_FORMAT) ?: option.summary.isoDate.take(10),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    buildString {
                        option.summary.storeName?.let { append(it).append(" · ") }
                        append("${option.summary.articlesCount} items")
                        option.summary.totalPence?.let {
                            append(" · £%.2f".format(it / 100.0))
                        }
                        option.date?.let {
                            val days = ChronoUnit.DAYS.between(it, today)
                            append(" · ")
                            append(
                                when {
                                    days <= 0L -> "today"
                                    days == 1L -> "yesterday"
                                    days < 31L -> "$days days ago"
                                    else -> {
                                        val months = days / 30
                                        "$months month${if (months == 1L) "" else "s"} ago"
                                    }
                                },
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                if (option.alreadyImported) {
                    Text(
                        "Already in your kitchen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
