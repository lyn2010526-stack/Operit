package com.cynosure.operit.ui.features.settings.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cynosure.operit.integrations.http.ExternalChatHttpNetworkInfo
import com.cynosure.operit.integrations.lansync.LanSyncCollections
import com.cynosure.operit.integrations.lansync.LanSyncManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanSyncSettingsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { LanSyncManager.getInstance(context) }
    val overview by manager.overview.collectAsState(
        initial = com.cynosure.operit.integrations.lansync.LanSyncOverview(
            emptyList(),
            0,
            com.cynosure.operit.integrations.lansync.LanSyncSnapshot(),
        )
    )
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("19876") }
    var pairingCode by remember { mutableStateOf("") }
    var selectedCollections by remember { mutableStateOf(LanSyncCollections.all.toSet()) }

    LaunchedEffect(Unit) {
        if (overview.snapshot.pairingCode.isBlank()) manager.rotatePairingCode()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("局域网同步") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本机服务", style = MaterialTheme.typography.titleMedium)
                    Text("地址：${ExternalChatHttpNetworkInfo.getLocalIpv4Addresses().joinToString()}  端口：${overview.snapshot.serverPort.takeIf { it > 0 } ?: port}")
                    Text("配对码：${overview.snapshot.pairingCode.ifBlank { "生成中" }}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val parsedPort = port.toIntOrNull()
                            if (parsedPort == null || parsedPort !in 1024..65535) {
                                Toast.makeText(context, "端口需位于 1024-65535", Toast.LENGTH_SHORT).show()
                            } else {
                                runCatching { manager.startServer(parsedPort) }
                                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("启动")
                        }
                        Button(onClick = manager::stopServer, enabled = overview.snapshot.serverRunning) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Text("停止")
                        }
                        Button(onClick = manager::rotatePairingCode) { Text("换码") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("添加设备", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(host, { host = it }, label = { Text("IP 地址") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(port, { port = it }, label = { Text("端口") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(pairingCode, { pairingCode = it }, label = { Text("配对码") }, modifier = Modifier.fillMaxWidth())
                    Text("同步范围", style = MaterialTheme.typography.labelLarge)
                    LanSyncCollections.all.forEach { collection ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = collection in selectedCollections,
                                onCheckedChange = { checked ->
                                    selectedCollections = if (checked) selectedCollections + collection else selectedCollections - collection
                                },
                            )
                            Text(collectionLabel(collection))
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    manager.client().pair(host, port.toInt(), pairingCode, selectedCollections)
                                }.onSuccess {
                                    Toast.makeText(context, "已配对 ${it.deviceName}", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = host.isNotBlank() && pairingCode.length == 6 && port.toIntOrNull() != null && selectedCollections.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Text("连接并配对")
                    }
                }
            }

            Text("已配对设备", style = MaterialTheme.typography.titleMedium)
            overview.peers.forEach { peer ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(peer.deviceName, style = MaterialTheme.typography.titleSmall)
                        Text("${peer.host}:${peer.port}")
                        Text("范围：${peer.enabledCollections.split(',').joinToString { collectionLabel(it) }}")
                        Text("上次同步：${if (peer.lastSyncAt > 0) java.text.DateFormat.getDateTimeInstance().format(peer.lastSyncAt) else "尚未同步"}")
                        peer.lastError?.let { Text("错误：$it", color = MaterialTheme.colorScheme.error) }
                        Button(onClick = {
                            scope.launch {
                                runCatching { manager.client().sync(peer.deviceId) }
                                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Text("立即同步")
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("运行状态", style = MaterialTheme.typography.titleMedium)
                    Text("状态：${overview.snapshot.state}")
                    Text("发送：${overview.snapshot.sentChangeCount}  接收：${overview.snapshot.receivedChangeCount}")
                    Text("待处理冲突：${overview.conflictCount}")
                    overview.snapshot.lastError?.let { Text("错误：$it", color = MaterialTheme.colorScheme.error) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun collectionLabel(collection: String): String = when (collection) {
    LanSyncCollections.MEMORIES -> "记忆"
    LanSyncCollections.SKILLS -> "Skill 文件"
    LanSyncCollections.CHARACTER_CARDS -> "角色卡"
    LanSyncCollections.CHARACTER_GROUPS -> "角色群组"
    LanSyncCollections.CHATS -> "聊天"
    LanSyncCollections.MESSAGES -> "消息"
    LanSyncCollections.MESSAGE_VARIANTS -> "消息变体"
    else -> collection
}
