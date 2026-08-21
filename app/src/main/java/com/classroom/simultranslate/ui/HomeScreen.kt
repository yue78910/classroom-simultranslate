package com.classroom.simultranslate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classroom.simultranslate.HomeUiState
import com.classroom.simultranslate.data.EngineStatus
import com.classroom.simultranslate.data.SubtitlePair
import com.classroom.simultranslate.data.TranslationDirection
import com.classroom.simultranslate.data.TranslationSessionState
import kotlin.math.sin

@Composable
fun HomeScreen(
    ui: HomeUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 120.dp),
    ) {
        HeaderRow(
            ui = ui,
            onOpenModels = onOpenModels,
            onOpenSettings = onOpenSettings,
        )
        Spacer(Modifier.height(8.dp))
        StatusBar(status = ui.status, sessionState = ui.sessionState)
        Spacer(Modifier.height(10.dp))
        SubtitlePanel(
            modifier = Modifier.weight(1f),
            ui = ui,
        )
        Spacer(Modifier.height(10.dp))
        AudioSpectrum(
            rms = ui.rms,
            modifier = Modifier
                .fillMaxWidth(0.875f)
                .height(28.dp)
                .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(10.dp))
        ControlBar(
            sessionState = ui.sessionState,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onEnd = onEnd,
            onClear = onClear,
        )
    }
}

@Composable
private fun HeaderRow(
    ui: HomeUiState,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${ui.direction.label} · ${ui.mode.label.take(4)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            IconButton(onClick = onOpenModels, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "离线模型")
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }
    }
}

@Composable
private fun StatusBar(
    status: EngineStatus,
    sessionState: TranslationSessionState,
) {
    val (label, color) = when {
        sessionState == TranslationSessionState.PAUSED -> "已暂停" to Color(0xFFB26A00)
        status == EngineStatus.Idle -> "未连接" to MaterialTheme.colorScheme.onSurfaceVariant
        status == EngineStatus.Starting -> "启动中" to MaterialTheme.colorScheme.tertiary
        status == EngineStatus.Online -> "在线翻译" to Color(0xFF0E8A7D)
        status == EngineStatus.Offline -> "离线翻译" to Color(0xFFB26A00)
        status is EngineStatus.Error -> "错误" to MaterialTheme.colorScheme.error
        else -> "翻译中" to Color(0xFF0E8A7D)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        if (status is EngineStatus.Error) {
            Spacer(Modifier.width(10.dp))
            Text(
                status.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SubtitlePanel(
    modifier: Modifier,
    ui: HomeUiState,
) {
    val current = ui.snapshot
    val currentPair = SubtitlePair(
        source = current.sourcePartial,
        target = current.targetPartial,
    )
    val allPairs = (current.history + currentPair).takeLast(6)
    val hasContent = allPairs.any { it.source.isNotBlank() || it.target.isNotBlank() }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (!hasContent) {
                    item {
                        SubtitleLine(
                            aux = "",
                            main = "等待语音…",
                            fontScale = ui.fontScale,
                            mainColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                } else {
                    items(allPairs) { pair ->
                        SubtitleLine(
                            aux = pair.auxFor(ui.direction),
                            main = pair.mainFor(ui.direction),
                            fontScale = ui.fontScale,
                            mainColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (hasContent && allPairs.size > 1) {
                Text(
                    "↑ 向上滑动查看更多",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
        }
    }
}

@Composable
private fun SubtitleLine(
    aux: String,
    main: String,
    fontScale: Float,
    mainColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (aux.isNotBlank()) {
            Text(
                aux,
                fontSize = (11 * fontScale).sp,
                lineHeight = (14 * fontScale).sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(3.dp))
        }
        Text(
            main,
            fontSize = (16 * fontScale).sp,
            lineHeight = (21 * fontScale).sp,
            fontWeight = FontWeight.Bold,
            color = mainColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AudioSpectrum(
    rms: Float,
    modifier: Modifier,
) {
    val level = (rms / 0.08f).coerceIn(0f, 1f)
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos -> phase = nanos / 1_000_000_000f }
        }
    }
    Canvas(modifier = modifier) {
        val bars = 24
        val gap = 5.dp.toPx()
        val barWidth = 4.5.dp.toPx()
        val totalWidth = barWidth * bars + gap * (bars - 1)
        val startX = (size.width - totalWidth) / 2f
        val baseHeight = 3.dp.toPx()
        val maxHeight = size.height
        val activeLevel = if (level < 0.01f) 0.08f else level
        for (i in 0 until bars) {
            val wave = (sin(phase * 2.4 + i * 0.55) * 0.5 + 0.5)
            val envelope = (sin(i * 0.37 + 1.3) * 0.5 + 0.5)
            val h = (
                baseHeight +
                    (maxHeight - baseHeight) *
                    activeLevel *
                    (0.30f + 0.70f * wave) *
                    (0.45f + 0.55f * envelope)
                ).toFloat()
            val x = startX + i * (barWidth + gap)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF0F6CBD), Color(0xFF0E8A7D))),
                topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
private fun ControlBar(
    sessionState: TranslationSessionState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = {
                if (sessionState == TranslationSessionState.PAUSED) {
                    onResume()
                } else {
                    onStart()
                }
            },
            enabled = sessionState != TranslationSessionState.RUNNING,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF16A34A),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF16A34A).copy(alpha = 0.45f),
                disabledContentColor = Color.White,
            ),
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                if (sessionState == TranslationSessionState.PAUSED) "继续" else "开始",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Button(
            onClick = {
                if (sessionState == TranslationSessionState.PAUSED) {
                    onEnd()
                } else {
                    onPause()
                }
            },
            enabled = sessionState != TranslationSessionState.IDLE,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFDC2626),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFDC2626).copy(alpha = 0.45f),
                disabledContentColor = Color.White,
            ),
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                if (sessionState == TranslationSessionState.PAUSED) "结束" else "停止",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("清空", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun SubtitlePair.mainFor(direction: TranslationDirection): String = when (direction) {
    TranslationDirection.EN_TO_ZH -> target.ifBlank { source }
    TranslationDirection.ZH_TO_EN -> source.ifBlank { target }
}

private fun SubtitlePair.auxFor(direction: TranslationDirection): String = when (direction) {
    TranslationDirection.EN_TO_ZH -> source.ifBlank { target }
    TranslationDirection.ZH_TO_EN -> target.ifBlank { source }
}
