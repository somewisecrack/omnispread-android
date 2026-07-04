package com.example.omnispread.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omnispread.data.BacktestRequest
import com.example.omnispread.data.PairResult
import com.example.omnispread.ui.theme.AccentBlue
import com.example.omnispread.ui.theme.AccentCyan
import com.example.omnispread.ui.theme.AccentGreen
import com.example.omnispread.ui.theme.AccentRed
import com.example.omnispread.ui.theme.AccentYellow
import com.example.omnispread.ui.theme.BgCard
import com.example.omnispread.ui.theme.BgSecondary
import com.example.omnispread.ui.theme.Border
import com.example.omnispread.ui.theme.TextMuted
import com.example.omnispread.ui.theme.TextPrimary
import com.example.omnispread.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairDetailSheet(
    pair: PairResult,
    interval: String,
    endDate: String,
    onDismiss: () -> Unit,
    onBacktest: (BacktestRequest) -> Unit,
) {
    val isShortSpread = pair.direction in listOf("SHORT_SPREAD", "long_x_short_y")
    val xSym = pair.pair.split("/").getOrElse(0) { pair.x }
    val ySym = pair.pair.split("/").getOrElse(1) { pair.y }
    val signalText  = if (isShortSpread) "Buy $xSym — Sell $ySym" else "Sell $xSym — Buy $ySym"
    val signalColor = if (isShortSpread) AccentRed else AccentGreen

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = BgCard,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = Border) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(pair.pair, color = TextPrimary, fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${pair.method} • ${pair.half_life}d half-life • ${if (pair.same_sector == "Yes") "Same Sector" else "Cross Sector"}",
                        color = TextMuted, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(pair.combo, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = TextSecondary)
                }
            }

            HorizontalDivider(color = Border.copy(alpha = 0.5f))

            // Stats grid — 2 per row, 4 rows
            val stats = listOf(
                Triple("Z-Score",    "${if (pair.z_score > 0) "+" else ""}${String.format("%.2f", pair.z_score)}",
                    if (pair.z_score > 0) AccentRed else AccentGreen),
                Triple("P(Profit)",  "${String.format("%.2f", pair.prob_profit)}%\n${String.format("%.2f", pair.prob_profit_low)}–${String.format("%.2f", pair.prob_profit_high)}%",
                    AccentBlue),
                Triple("Half-Life",  "${pair.half_life}d",   TextPrimary),
                Triple("Hurst",      String.format("%.2f", pair.hurst),
                    if (pair.hurst < 0.35) AccentGreen else AccentCyan),
                Triple("Exp. Return","${String.format("%.2f", pair.exp_return)}%", AccentYellow),
                Triple("Move/Mean",  String.format("%.2f", pair.move_to_mean), TextSecondary),
                Triple("Unit Price", "₹${String.format("%.2f", pair.unit_price)}", TextSecondary),
                Triple("ρ Price",    String.format("%.2f", pair.price_corr),
                    if (pair.price_corr > 0.7) AccentGreen else TextSecondary),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, value, color) ->
                            StatCard(label = label, value = value, color = color, modifier = Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Extreme Z info
            if (pair.extreme_z_detail.isNotEmpty()) {
                Surface(color = BgSecondary, border = BorderStroke(1.dp, Border), shape = MaterialTheme.shapes.small) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Ext.Z in HL: ", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            "${pair.extreme_z_in_hl} (${pair.extreme_z_detail})",
                            color      = if (pair.extreme_z_in_hl == "Yes") AccentRed else TextPrimary,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Z-Score Chart section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Z-SCORE HISTORY", color = TextSecondary, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(AccentBlue, "Z-Score")
                    LegendDot(AccentRed.copy(alpha = 0.7f), "+2σ")
                    LegendDot(AccentGreen.copy(alpha = 0.7f), "-2σ")
                }
                Surface(color = BgSecondary, border = BorderStroke(1.dp, Border), shape = MaterialTheme.shapes.medium) {
                    ZScoreChart(dataPoints = pair.historical_z_scores, modifier = Modifier.padding(8.dp))
                }
            }

            // Signal banner
            Surface(
                color  = signalColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, signalColor.copy(alpha = 0.3f)),
                shape  = MaterialTheme.shapes.small,
            ) {
                Text(
                    "Signal: $signalText",
                    modifier   = Modifier.fillMaxWidth().padding(12.dp),
                    color      = signalColor,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = TextAlign.Center,
                )
            }

            // Backtest button (only when end date is available)
            if (endDate.isNotEmpty()) {
                Button(
                    onClick = {
                        onBacktest(BacktestRequest(
                            x         = pair.x,
                            y         = pair.y,
                            qty       = pair.qty,
                            direction = pair.direction,
                            interval  = interval,
                            half_life = pair.half_life,
                            end_date  = endDate,
                        ))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue.copy(alpha = 0.2f),
                        contentColor   = AccentCyan,
                    ),
                    border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                ) {
                    Text("Run Forward Half-Life Backtest", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color    = BgSecondary,
        border   = BorderStroke(1.dp, Border),
        shape    = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier              = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment   = Alignment.CenterHorizontally,
        ) {
            Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                color      = color,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign  = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(modifier = Modifier.size(10.dp, 2.dp), color = color) {}
        Text(label, color = TextMuted, fontSize = 10.sp)
    }
}
