package com.example.omnispread.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omnispread.data.NIFTY50_TICKERS
import com.example.omnispread.ui.theme.AccentBlue
import com.example.omnispread.ui.theme.AccentCyan
import com.example.omnispread.ui.theme.AccentRed
import com.example.omnispread.ui.theme.BgCard
import com.example.omnispread.ui.theme.Border
import com.example.omnispread.ui.theme.TextMuted
import com.example.omnispread.ui.theme.TextPrimary
import com.example.omnispread.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PERIODS = listOf(
    "60d" to "60d", "6mo" to "6mo", "1y" to "1y",
    "2y" to "2y", "3y" to "3y", "5y" to "5y", "custom" to "Custom",
)

private fun intervalOptionsFor(period: String, spanDays: Int?): List<String> = when {
    period == "60d"    -> listOf("15m", "30m")
    period == "6mo"    -> listOf("1h", "1d")
    period == "1y"     -> listOf("1d")
    period == "custom" -> when {
        spanDays == null -> listOf("1d")
        spanDays <= 60   -> listOf("15m", "30m")
        spanDays < 365   -> listOf("1h", "1d")
        else             -> listOf("1d")
    }
    else -> listOf("1d")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanForm(
    isScanning: Boolean,
    onScan: (tickers: List<String>, period: String, interval: String, startDate: String?, endDate: String?) -> Unit,
    onReset: () -> Unit,
) {
    var period   by remember { mutableStateOf("3y") }
    var interval by remember { mutableStateOf("1d") }
    var startMs  by remember { mutableStateOf<Long?>(null) }
    var endMs    by remember { mutableStateOf<Long?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker   by remember { mutableStateOf(false) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val spanDays = if (startMs != null && endMs != null)
        ((endMs!! - startMs!!) / 86_400_000L).toInt() + 1 else null

    val intervalOptions = intervalOptionsFor(period, spanDays)
    LaunchedEffect(period, spanDays) {
        if (interval !in intervalOptions) interval = intervalOptions.first()
    }

    val showIntervals = period in listOf("60d", "6mo", "1y", "custom")

    Card(
        colors   = CardDefaults.cardColors(containerColor = BgCard),
        border   = BorderStroke(1.dp, Border),
        shape    = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // NIFTY 50 badge
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color  = AccentBlue.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                    shape  = MaterialTheme.shapes.small,
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("NIFTY 50", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text("${NIFTY50_TICKERS.size} stocks", color = TextMuted, fontSize = 12.sp)
            }

            // Period selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LOOKBACK PERIOD", color = TextSecondary, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PERIODS.forEach { (value, label) ->
                        ToggleChip(label = label, selected = period == value, onClick = { period = value })
                    }
                }
            }

            // Custom date pickers
            AnimatedVisibility(visible = period == "custom") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showStartPicker = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = BorderStroke(1.dp, Border),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text(if (startMs != null) sdf.format(Date(startMs!!)) else "Start Date", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { showEndPicker = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = BorderStroke(1.dp, Border),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text(if (endMs != null) sdf.format(Date(endMs!!)) else "End Date", fontSize = 12.sp)
                    }
                }
            }

            // Interval selector
            AnimatedVisibility(visible = showIntervals) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("INTERVAL", color = TextSecondary, fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        intervalOptions.forEach { intVal ->
                            ToggleChip(label = intVal, selected = interval == intVal, onClick = { interval = intVal })
                        }
                    }
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        period = "3y"; interval = "1d"; startMs = null; endMs = null
                        onReset()
                    },
                    enabled = !isScanning,
                    colors  = ButtonDefaults.buttonColors(
                        containerColor         = AccentRed.copy(alpha = 0.9f),
                        contentColor           = TextPrimary,
                        disabledContainerColor = AccentRed.copy(alpha = 0.3f),
                        disabledContentColor   = TextPrimary.copy(alpha = 0.4f),
                    ),
                ) {
                    Text("RESET", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick  = {
                        val startStr = if (startMs != null && period == "custom") sdf.format(Date(startMs!!)) else null
                        val endStr   = if (endMs != null && period == "custom") sdf.format(Date(endMs!!)) else null
                        val finalInterval = if (period in listOf("60d", "6mo", "1y", "custom")) interval else "1d"
                        onScan(NIFTY50_TICKERS, period, finalInterval, startStr, endStr)
                    },
                    enabled  = !isScanning,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = AccentBlue,
                        contentColor           = TextPrimary,
                        disabledContainerColor = AccentBlue.copy(alpha = 0.3f),
                        disabledContentColor   = TextPrimary.copy(alpha = 0.5f),
                    ),
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = TextPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Scanning...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Run Scan", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        DatePickerModal(initialMs = startMs, onSelected = { startMs = it }, onDismiss = { showStartPicker = false })
    }
    if (showEndPicker) {
        DatePickerModal(initialMs = endMs, onSelected = { endMs = it }, onDismiss = { showEndPicker = false })
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick        = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors         = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) AccentBlue.copy(alpha = 0.2f) else Color.Transparent,
            contentColor   = if (selected) AccentCyan else TextMuted,
        ),
        border = BorderStroke(1.dp, if (selected) AccentBlue else Border),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(initialMs: Long?, onSelected: (Long?) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMs)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSelected(state.selectedDateMillis); onDismiss() }) {
                Text("OK", color = AccentBlue)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        colors = DatePickerDefaults.colors(containerColor = BgCard),
    ) {
        DatePicker(state = state)
    }
}
