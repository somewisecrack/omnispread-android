package com.example.omnispread.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omnispread.data.PairResult
import com.example.omnispread.ui.theme.AccentBlue
import com.example.omnispread.ui.theme.AccentCyan
import com.example.omnispread.ui.theme.AccentGreen
import com.example.omnispread.ui.theme.AccentPurple
import com.example.omnispread.ui.theme.AccentRed
import com.example.omnispread.ui.theme.AccentYellow
import com.example.omnispread.ui.theme.BgCard
import com.example.omnispread.ui.theme.Border
import com.example.omnispread.ui.theme.TextMuted
import com.example.omnispread.ui.theme.TextPrimary
import com.example.omnispread.ui.theme.TextSecondary

@Composable
fun ResultCard(
    index: Int,
    result: PairResult,
    interval: String,
    hasEndDate: Boolean,
    onClick: () -> Unit,
    onBacktest: () -> Unit,
) {
    val isShortSpread = result.direction in listOf("SHORT_SPREAD", "long_x_short_y")

    Card(
        onClick  = onClick,
        colors   = CardDefaults.cardColors(containerColor = BgCard),
        border   = BorderStroke(1.dp, Border),
        shape    = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            // Header: index + pair name + method badge
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("$index", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        result.pair,
                        color      = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val comboDisplay = if (result.combo.length > 72) result.combo.take(72) + "…" else result.combo
                    Text(comboDisplay, color = TextMuted, fontSize = 10.sp, lineHeight = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                MethodBadge(result.method)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Border.copy(alpha = 0.5f))

            // Stats: 2-column layout
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatRow("Z-Score",
                        "${if (result.z_score > 0) "+" else ""}${String.format("%.2f", result.z_score)}",
                        if (result.z_score > 0) AccentRed else AccentGreen)
                    StatRow("Half-Life", formatHl(result.half_life, interval), TextSecondary)
                    StatRow("Move/Mean", String.format("%.2f", result.move_to_mean), TextSecondary)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pColor = if (result.prob_profit >= 70) AccentGreen
                    else if (result.prob_profit >= 50) AccentBlue else AccentYellow
                    StatRow("P(Profit)", "${String.format("%.1f", result.prob_profit)}%", pColor)
                    val hColor = if (result.hurst < 0.35) AccentGreen
                    else if (result.hurst < 0.45) AccentCyan else AccentYellow
                    StatRow("Hurst", String.format("%.2f", result.hurst), hColor)
                    StatRow("Exp.Ret", "${String.format("%.2f", result.exp_return)}%", AccentYellow)
                }
            }

            Row(
                verticalAlignment    = Alignment.CenterVertically,
                modifier             = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (result.extreme_z_in_hl == "Yes") MiniTag("Ext.Z", AccentRed)
                if (result.same_sector == "Yes") MiniTag("Same Sector", AccentGreen)
                Text(
                    if (isShortSpread) "▲ Short Spread" else "▼ Long Spread",
                    color      = if (isShortSpread) AccentRed else AccentGreen,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick        = onBacktest,
                    enabled        = hasEndDate,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors         = ButtonDefaults.outlinedButtonColors(
                        contentColor        = if (hasEndDate) AccentCyan else TextMuted,
                        disabledContentColor = TextMuted,
                    ),
                    border = BorderStroke(1.dp, if (hasEndDate) AccentBlue.copy(alpha = 0.5f) else Border),
                ) {
                    Text("Backtest", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(72.dp))
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun MethodBadge(method: String) {
    val color = when (method) {
        "Both"     -> AccentGreen
        "Johansen" -> AccentPurple
        else       -> AccentBlue
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(method, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MiniTag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.extraSmall) {
        Box(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

fun formatHl(hl: Int, interval: String): String = when (interval) {
    "15m" -> "${String.format("%.1f", hl * 15.0 / 60)}h"
    "30m" -> "${String.format("%.1f", hl * 30.0 / 60)}h"
    "1h", "60m" -> "${hl}h"
    else  -> "${hl}d"
}
