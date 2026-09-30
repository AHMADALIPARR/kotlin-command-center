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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class UiChatMessage(val role: String, val content: String)

@Composable
fun ChatScreen(
    messages: List<UiChatMessage>,
    busy: Boolean,
    onSend: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages) { msg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${msg.role}: ${msg.content}",
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask the agent…") },
                singleLine = true,
            )
            Button(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                enabled = !busy && draft.isNotBlank(),
            ) {
                Text("Send")
            }
        }
        if (busy) CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
    }
}
