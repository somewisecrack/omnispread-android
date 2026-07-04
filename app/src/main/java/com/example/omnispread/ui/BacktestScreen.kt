package com.example.omnispread.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.omnispread.data.BacktestRequest
import com.example.omnispread.ui.theme.AccentBlue
import com.example.omnispread.ui.theme.AccentCyan
import com.example.omnispread.ui.theme.AccentGreen
import com.example.omnispread.ui.theme.AccentRed
import com.example.omnispread.ui.theme.BgCard
import com.example.omnispread.ui.theme.BgPrimary
import com.example.omnispread.ui.theme.Border
import com.example.omnispread.ui.theme.TextMuted
import com.example.omnispread.ui.theme.TextPrimary
import com.example.omnispread.ui.theme.TextSecondary
import com.example.omnispread.viewmodel.BacktestState
import com.example.omnispread.viewmodel.BacktestViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(
    request: BacktestRequest,
    onBack: () -> Unit,
    viewModel: BacktestViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val pairLabel = "${request.x.replace(".NS", "").replace(".BO", "")}/" +
            request.y.replace(".NS", "").replace(".BO", "")

    LaunchedEffect(request) { viewModel.runBacktest(request) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Forward Backtest", color = TextMuted, fontSize = 10.sp,
                            fontWeight = FontWeight.Medium, letterSpacing = 0.08.sp)
                        Text(pairLabel, color = AccentCyan, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary),
            )
        },
        containerColor = BgPrimary,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                is BacktestState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = AccentBlue)
                            Text("Loading forward prices…", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }

                is BacktestState.Error -> {
                    Surface(
                        color  = AccentRed.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f)),
                        shape  = MaterialTheme.shapes.medium,
                    ) {
                        Text(s.message, modifier = Modifier.padding(16.dp), color = AccentRed, fontSize = 13.sp)
                    }
                }

                is BacktestState.Success -> {
                    val result    = s.result
                    val pnl       = result.final_pnl_pct ?: 0.0
                    val maxProfit = result.max_profit_pct ?: 0.0
                    val pnlColor  = if (pnl >= 0) AccentGreen else AccentRed
                    val sdf       = SimpleDateFormat("dd MMM yy HH:mm", Locale.US)
                    val points    = result.points ?: emptyList()

                    // Summary stats row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("Actual PnL",
                                "${if (pnl >= 0) "+" else ""}${String.format("%.2f", pnl)}%",
                                if (pnl >= 0) AccentGreen else AccentRed),
                            Triple("Max Profit",
                                "${if (maxProfit >= 0) "+" else ""}${String.format("%.2f", maxProfit)}%",
                                if (maxProfit >= 0) AccentGreen else AccentRed),
                        ).forEach { (label, value, color) ->
                            StatBox(label = label, value = value, color = color, modifier = Modifier.weight(1f))
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBox("Half-Life", "${result.half_life} bars", TextPrimary, Modifier.weight(1f))
                        StatBox("Interval", result.interval ?: request.interval, AccentCyan, Modifier.weight(1f))
                    }

                    // Entry / exit times
                    val entryStr = result.entry_time?.let { sdf.format(Date(it * 1000)) } ?: "N/A"
                    val exitStr  = result.exit_time?.let { sdf.format(Date(it * 1000)) } ?: "N/A"
                    Text(
                        "Entry: $entryStr  •  Last bar: $exitStr",
                        color    = TextMuted,
                        fontSize = 11.sp,
                    )

                    // PnL chart
                    ChartCard(
                        title    = "Actual PnL",
                        subtitle = "Max profit: ${if (maxProfit >= 0) "+" else ""}${String.format("%.2f", maxProfit)}%",
                    ) {
                        PnLChart(points = points, lineColor = pnlColor)
                    }

                    // Spread chart
                    ChartCard(title = "Spread") {
                        SpreadChart(points = points)
                    }

                    result.note?.let { note ->
                        Text(note, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = BgCard, border = BorderStroke(1.dp, Border), shape = MaterialTheme.shapes.medium) {
        Column(
            Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Surface(color = BgCard, border = BorderStroke(1.dp, Border), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = TextMuted, fontSize = 11.sp) }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
