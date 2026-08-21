package com.classroom.simultranslate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.classroom.simultranslate.DownloadUiState
import com.classroom.simultranslate.offline.ModelManifest
import com.classroom.simultranslate.offline.OfflineModelPack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadScreen(
    manifest: ModelManifest,
    installedPacks: Set<String>,
    downloadStates: Map<String, DownloadUiState>,
    onBack: () -> Unit,
    onDownload: (OfflineModelPack) -> Unit,
    onDelete: (OfflineModelPack) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("离线模型") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(manifest.packs) { pack ->
                ModelPackCard(
                    pack = pack,
                    installed = pack.id in installedPacks,
                    state = downloadStates[pack.id] ?: DownloadUiState(),
                    onDownload = { onDownload(pack) },
                    onDelete = { onDelete(pack) },
                )
            }
        }
    }
}

@Composable
private fun ModelPackCard(
    pack: OfflineModelPack,
    installed: Boolean,
    state: DownloadUiState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(pack.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            installed -> "已安装"
                            state.downloading -> state.detail
                            else -> "未安装"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (installed) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "已安装",
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            if (state.downloading || state.progress > 0f) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${(state.progress * 100).toInt()}% · ${state.detail}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (installed) {
                    OutlinedButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("删除")
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = !state.downloading,
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.downloading) "下载中" else "下载")
                    }
                }
            }
        }
    }
}
