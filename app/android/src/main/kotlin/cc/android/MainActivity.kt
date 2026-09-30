/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 SnapKitty Collective
 */
package cc.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.android.ui.ChatScreen
import cc.android.ui.ServerAdminScreen
import cc.core.CommandCenterConfig

/**
 * Entry point. In production the [CommandCenterConfig] comes from
 * EncryptedSharedPreferences (see android/README.md); here it is passed in
 * so previews and tests stay secret-free.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: load from EncryptedSharedPreferences via a settings screen.
        val config = CommandCenterConfig.fromEnv()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CommandCenterApp(config)
                }
            }
        }
    }
}

@Composable
fun CommandCenterApp(config: CommandCenterConfig) {
    val vm: CommandCenterViewModel = viewModel { CommandCenterViewModel(config) }
    val state by vm.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Agent") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Server") })
        }
        when (tab) {
            0 -> ChatScreen(
                messages = state.chat,
                busy = state.chatBusy,
                onSend = vm::sendChat,
            )
            1 -> ServerAdminScreen(
                output = state.sshOutput,
                busy = state.sshBusy,
                onRun = vm::runSshCommand,
            )
        }
        state.error?.let { err ->
            Text("Error: $err", color = MaterialTheme.colorScheme.error)
        }
    }
}
