/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SnapKitty Collective
 */
package cc.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** One-tap admin actions — the dangerous ones ask for the custom command. */
private val QUICK_COMMANDS = listOf(
    "System status" to "systemctl --no-pager --failed; uptime",
    "Ollama status" to "systemctl --no-pager status ollama",
    "Disk + memory" to "df -h / && free -h",
)

@Composable
fun ServerAdminScreen(
    output: String,
    busy: Boolean,
    onRun: (String) -> Unit,
) {
    var custom by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text("Quick actions", modifier = Modifier.padding(bottom = 4.dp))
        QUICK_COMMANDS.forEach { (label, cmd) ->
            Button(
                onClick = { onRun(cmd) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            ) {
                Text(label)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("custom command…") },
                singleLine = true,
            )
            Button(
                onClick = { onRun(custom) },
                enabled = !busy && custom.isNotBlank(),
            ) {
                Text("Run")
            }
        }
        if (busy) CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
        Text(
            text = output.ifBlank { "no output yet" },
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(top = 8.dp),
        )
    }
}
