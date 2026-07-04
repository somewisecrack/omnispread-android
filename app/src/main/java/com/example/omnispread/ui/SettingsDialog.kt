package com.example.omnispread.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.omnispread.data.ApiClientProvider
import com.example.omnispread.ui.theme.AccentBlue
import com.example.omnispread.ui.theme.BgCard
import com.example.omnispread.ui.theme.BgSecondary
import com.example.omnispread.ui.theme.Border
import com.example.omnispread.ui.theme.TextMuted
import com.example.omnispread.ui.theme.TextPrimary
import com.example.omnispread.ui.theme.TextSecondary

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf(ApiClientProvider.getSavedUrl(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Text("Server Settings", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column {
                Text(
                    "Backend URL (FastAPI server)",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = {
                        Text(
                            "http://192.168.x.x:8000",
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = BgSecondary,
                        unfocusedContainerColor = BgSecondary,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Border,
                        cursorColor = AccentBlue,
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Default: http://10.0.2.2:8000 (emulator)\nFor physical device, use your Mac's LAN IP.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ApiClientProvider.saveUrl(context, urlInput.trim())
                onDismiss()
            }) {
                Text("Save", color = AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}
