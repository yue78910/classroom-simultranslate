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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.classroom.simultranslate.HomeUiState
import com.classroom.simultranslate.data.EngineMode
import com.classroom.simultranslate.data.TranslationDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    ui: HomeUiState,
    onBack: () -> Unit,
    onDirectionChange: (TranslationDirection) -> Unit,
    onModeChange: (EngineMode) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onMirrorChange: (Boolean) -> Unit,
    onOpenModels: () -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("OpenAI API Key", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { value ->
                            apiKey = value
                            onApiKeyChange(value)
                        },
                        placeholder = { Text(if (ui.hasApiKey) "已保存，输入新 Key 可替换" else "sk-…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "显示或隐藏 Key",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("翻译方向", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TranslationDirection.entries.forEach { direction ->
                            FilterChip(
                                selected = ui.direction == direction,
                                onClick = { onDirectionChange(direction) },
                                label = { Text(direction.label) },
                            )
                        }
                    }
                }
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("引擎模式", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    EngineMode.entries.forEach { mode ->
                        FilterChip(
                            selected = ui.mode == mode,
                            onClick = { onModeChange(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("字幕字号", style = MaterialTheme.typography.titleMedium)
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("小", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = ui.fontScale,
                            onValueChange = onFontScaleChange,
                            valueRange = 1f..2.2f,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text("大", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "%.1fx".format(ui.fontScale),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("镜像加速下载", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Hugging Face 走 hf-mirror，GitHub 走 ghfast，失败自动回退官方源",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = ui.useMirror,
                            onCheckedChange = onMirrorChange,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onOpenModels,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("离线模型")
            }
        }
    }
}
