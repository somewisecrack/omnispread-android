package com.example.omnispread.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.omnispread.ui.theme.AccentBlue
import com.example.omnispread.ui.theme.AccentCyan
import com.example.omnispread.ui.theme.BgCard
import com.example.omnispread.ui.theme.TextMuted
import com.example.omnispread.ui.theme.TextPrimary
import com.example.omnispread.ui.theme.TextSecondary

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Text("About OmniSpread", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column {
                Text("On-device statistical pairs trading scanner for NIFTY 50 stocks.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Text("Engine", color = AccentCyan, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "• CADF cointegration with Kalman-filtered beta\n" +
                    "• Johansen cointegration test\n" +
                    "• Hurst exponent (R/S analysis)\n" +
                    "• Ensemble Monte Carlo (80 × 2 000 paths)\n" +
                    "• Block-bootstrap residuals",
                    color = TextMuted, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Text("Data", color = AccentCyan, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                Text("Adjusted-close prices via Yahoo Finance chart API. Runs fully on-device — no server required.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Text("For educational and research purposes only. Not financial advice.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = AccentBlue) }
        },
    )
}
