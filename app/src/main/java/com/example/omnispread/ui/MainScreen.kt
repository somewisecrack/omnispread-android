package com.example.omnispread.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.omnispread.ui.theme.BgCard
import com.example.omnispread.ui.theme.BgPrimary
import com.example.omnispread.ui.theme.Border
import com.example.omnispread.ui.theme.TextMuted
import com.example.omnispread.ui.theme.TextSecondary
import com.example.omnispread.viewmodel.MainViewModel
import com.example.omnispread.viewmodel.ScanState

private enum class SortField(val label: String) {
    CADF("CADF p"), Z_SCORE("|Z|"),
    HALF_LIFE("Half-Life"), SENTIMENT("Sentiment Δ"), EXP_RETURN("Exp.Ret"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToBacktest: () -> Unit,
    onNavigateToTrade: () -> Unit,
) {
    val scanState   by viewModel.scanState.collectAsState()
    val selectedPair by viewModel.selectedPair.collectAsState()
    val interval    by viewModel.currentInterval.collectAsState()
    val endDate     by viewModel.currentEndDate.collectAsState()
    val insights    by viewModel.insights.collectAsState()
    val enrichStatus by viewModel.enrichStatus.collectAsState()
    val config      by viewModel.config.collectAsState()

    var showSettings  by remember { mutableStateOf(false) }
    var sortField     by remember { mutableStateOf(SortField.CADF) }
    var sortAsc       by remember { mutableStateOf(true) }

    val isScanning = scanState is ScanState.Scanning
    val results    = (scanState as? ScanState.Success)?.results ?: emptyList()

    val sorted = remember(results, sortField, sortAsc, insights) {
        // Sentiment Δ = how much the news favours the signal: long-leg score minus short-leg score.
        fun sentimentEdge(r: PairResult): Double {
            val sx = insights[r.x]?.sentiment?.score ?: 0.0
            val sy = insights[r.y]?.sentiment?.score ?: 0.0
            return if (r.direction == "SHORT_SPREAD") sx - sy else sy - sx
        }
        val cmp: Comparator<PairResult> = when (sortField) {
            SortField.CADF        -> compareBy { it.cadf_pvalue }
            SortField.Z_SCORE     -> compareBy { kotlin.math.abs(it.z_score) }
            SortField.HALF_LIFE   -> compareBy { it.half_life }
            SortField.SENTIMENT   -> compareBy { sentimentEdge(it) }
            SortField.EXP_RETURN  -> compareBy { it.exp_return }
        }
        if (sortAsc) results.sortedWith(cmp) else results.sortedWith(cmp).reversed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "Settings", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary),
            )
        },
        containerColor = BgPrimary,
    ) { padding ->
        LazyColumn(
            state           = rememberLazyListState(),
            modifier        = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // App header
            item {
                AppHeader()
            }

            // Scan form
            item {
                ScanForm(
                    isScanning = isScanning,
                    onScan     = { tickers, period, intv, start, end, basis ->
                        viewModel.startScan(tickers, period, intv, start, end, basis)
                    },
                    onReset    = { viewModel.reset() },
                )
            }

            // Status / Error
            when (val s = scanState) {
                is ScanState.Scanning -> item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(14.dp),
                            color       = AccentBlue,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(s.status, color = TextSecondary, fontSize = 13.sp)
                    }
                }
                is ScanState.Success -> item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("✓", color = AccentGreen, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(s.message, color = TextSecondary, fontSize = 13.sp)
                    }
                    enrichStatus?.let {
                        Text(it, color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    if (!config.isConfigured) {
                        Text("Connect tastytrade (⚙) for IV data and option trades.", color = TextMuted, fontSize = 11.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                is ScanState.Error -> item {
                    Surface(
                        color    = AccentRed.copy(alpha = 0.08f),
                        border   = BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f)),
                        shape    = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(s.message, modifier = Modifier.padding(12.dp),
                            color = AccentRed, fontSize = 13.sp)
                    }
                }
                else -> {}
            }

            // Sort chips + results
            if (sorted.isNotEmpty()) {
                item {
                    SortBar(
                        sortField = sortField,
                        sortAsc   = sortAsc,
                        onSort    = { field ->
                            if (sortField == field) sortAsc = !sortAsc
                            else { sortField = field; sortAsc = field == SortField.CADF || field == SortField.HALF_LIFE }
                        },
                    )
                }

                itemsIndexed(sorted, key = { _, r -> r.pair }) { index, result ->
                    ResultCard(
                        index      = index + 1,
                        result     = result,
                        insightX   = insights[result.x],
                        insightY   = insights[result.y],
                        interval   = interval,
                        hasEndDate = endDate.isNotEmpty(),
                        onClick    = { viewModel.selectPair(result) },
                        onBacktest = {
                            viewModel.setPendingBacktest(BacktestRequest(
                                x = result.x, y = result.y, qty = result.qty,
                                direction = result.direction, interval = interval,
                                half_life = result.half_life, end_date = endDate,
                            ))
                            onNavigateToBacktest()
                        },
                    )
                }
            }

            // Footer
            item {
                Text(
                    "OMNISPREAD — For educational and research purposes only. Not financial advice.",
                    color     = TextMuted,
                    fontSize  = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            }
        }
    }

    // Pair detail bottom sheet
    selectedPair?.let { pair ->
        PairDetailSheet(
            pair      = pair,
            insightX  = insights[pair.x],
            insightY  = insights[pair.y],
            interval  = interval,
            endDate   = endDate,
            canTrade  = config.isConfigured,
            onDismiss = { viewModel.selectPair(null) },
            onBuildTrade = {
                viewModel.buildTrade(pair)
                viewModel.selectPair(null)
                onNavigateToTrade()
            },
            onBacktest = { request ->
                viewModel.setPendingBacktest(request)
                viewModel.selectPair(null)
                onNavigateToBacktest()
            },
        )
    }

    // Settings dialog
    if (showSettings) {
        SettingsDialog(viewModel = viewModel, onDismiss = { showSettings = false })
    }
}

@Composable
private fun AppHeader() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "⇌  OMNISPREAD",
                color      = AccentCyan,
                fontSize   = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            )
            Text("Statistical Pairs Trading Scanner", color = TextSecondary, fontSize = 14.sp)
            Text(
                "CADF + Johansen cointegration  •  tastytrade option spreads  •  news sentiment",
                color    = TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SortBar(
    sortField: SortField,
    sortAsc: Boolean,
    onSort: (SortField) -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text("Sort:", color = TextMuted, fontSize = 11.sp)
        SortField.entries.forEach { field ->
            val active = sortField == field
            FilterChip(
                selected = active,
                onClick  = { onSort(field) },
                label    = {
                    Text(
                        field.label + if (active) if (sortAsc) " ↑" else " ↓" else "",
                        fontSize = 10.sp,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                    selectedLabelColor     = AccentCyan,
                    containerColor         = BgCard,
                    labelColor             = TextMuted,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled             = true,
                    selected            = active,
                    borderColor         = Border,
                    selectedBorderColor = AccentBlue,
                    borderWidth         = 1.dp,
                    selectedBorderWidth = 1.dp,
                ),
            )
        }
    }
}
