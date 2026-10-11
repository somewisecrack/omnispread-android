package com.example.omnispread.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omnispread.ui.theme.AccentBlue
import com.example.omnispread.ui.theme.AccentCyan
import com.example.omnispread.ui.theme.AccentGreen
import com.example.omnispread.ui.theme.AccentRed
import com.example.omnispread.ui.theme.BgCard
import com.example.omnispread.ui.theme.Border
import com.example.omnispread.ui.theme.TextMuted
import com.example.omnispread.ui.theme.TextPrimary
import com.example.omnispread.ui.theme.TextSecondary
import com.example.omnispread.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val saved by viewModel.config.collectAsState()
    var secret by remember { mutableStateOf(saved.clientSecret) }
    var refresh by remember { mutableStateOf(saved.refreshToken) }
    var sandbox by remember { mutableStateOf(saved.sandbox) }
    var account by remember { mutableStateOf(saved.accountNumber) }
    var accounts by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()

    fun draft() = saved.copy(clientSecret = secret.trim(), refreshToken = refresh.trim(), sandbox = sandbox, accountNumber = account)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
        focusedBorderColor = AccentBlue, unfocusedBorderColor = Border,
        focusedLabelColor = AccentCyan, unfocusedLabelColor = TextMuted,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("tastytrade connection", color = TextPrimary, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Create an OAuth app and a personal grant at my.tastytrade.com → Manage → My Profile → API. " +
                        "Use the read scope for scanning; add trade only if you want to place orders. " +
                        "Secrets are encrypted with the Android Keystore and sent only to tastytrade.",
                    color = TextMuted, fontSize = 12.sp,
                )
                OutlinedTextField(secret, { secret = it }, label = { Text("Client secret") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), colors = fieldColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(refresh, { refresh = it }, label = { Text("Refresh token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), colors = fieldColors, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sandbox (api.cert)", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = sandbox, onCheckedChange = { sandbox = it; accounts = emptyList() })
                }
                TextButton(onClick = {
                    status = "Connecting..." to true
                    scope.launch {
                        viewModel.testConnection(draft()).fold(
                            onSuccess = { list ->
                                accounts = list
                                if (account !in list) account = list.firstOrNull() ?: ""
                                status = "Connected • ${list.size} account${if (list.size == 1) "" else "s"}" to true
                            },
                            onFailure = { status = (it.message ?: "Connection failed") to false },
                        )
                    }
                }, enabled = secret.isNotBlank() && refresh.isNotBlank()) { Text("Test connection", color = AccentCyan) }
                status?.let { (msg, ok) -> Text(msg, color = if (ok) AccentGreen else AccentRed, fontSize = 12.sp) }
                if (accounts.isNotEmpty()) {
                    Text("Account for orders", color = TextSecondary, fontSize = 12.sp)
                    accounts.forEach { a ->
                        TextButton(onClick = { account = a }) {
                            Text((if (a == account) "● " else "○ ") + a, color = if (a == account) AccentCyan else TextSecondary)
                        }
                    }
                } else if (account.isNotBlank()) {
                    Text("Account: $account", color = TextSecondary, fontSize = 12.sp)
                }
                Text(
                    "Prices: Yahoo Finance. Vol data, chains, quotes & orders: tastytrade. News: Google News RSS (personal use).",
                    color = TextMuted, fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.saveConfig(draft()); onDismiss() }) { Text("Save", color = AccentBlue) }
        },
        dismissButton = {
            Row {
                if (saved.isConfigured) TextButton(onClick = { viewModel.clearConfig(); onDismiss() }) { Text("Disconnect", color = AccentRed) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
            }
        },
    )
}
