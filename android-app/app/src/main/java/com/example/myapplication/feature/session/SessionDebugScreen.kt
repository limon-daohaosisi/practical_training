package com.example.myapplication.feature.session

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.core.accessibility.CaptureAccessibilityService
import com.example.myapplication.core.overlay.OverlayEntryService
import com.example.myapplication.core.session.AnalyzeRuntime
import com.example.myapplication.core.model.CaptureResult

@Composable
fun SessionDebugScreen(
    context: Context,
    isAccessibilityEnabled: State<Boolean>
) {
    val enabled = isAccessibilityEnabled.value
    val capture by CaptureAccessibilityService.capture.collectAsState()
    val screenshotPath by CaptureAccessibilityService.screenshotPath.collectAsState()
    val events by CaptureAccessibilityService.events.collectAsState()
    val analyzeState by AnalyzeRuntime.state(context).collectAsState()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (enabled) "已开启" else "未开启",
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                )
                            }) {
                                Text(if (enabled) "辅助功能设置" else "去开启")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                if (Settings.canDrawOverlays(context)) {
                                    context.startService(Intent(context, OverlayEntryService::class.java))
                                } else {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            }) {
                                Text("悬浮按钮")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "采集结果",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Analyze 状态: ${analyzeState.renderLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val c = capture
                        if (c == null) {
                            Column {
                                Text(
                                    text = "暂无外部 App 采集数据",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "试试切到：系统设置 / 微信 / 浏览器，再切回来",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            CaptureResultView(c, screenshotPath)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Text(
                    text = "事件日志（${events.size}）",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        text = if (enabled) "等待窗口事件..." else "请先开启辅助功能服务",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(events.reversed()) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

private fun AnalyzeRequestState.renderLabel(): String = when (this) {
    AnalyzeRequestState.Idle -> "idle"
    AnalyzeRequestState.Capturing -> "capturing"
    AnalyzeRequestState.Uploading -> "uploading"
    is AnalyzeRequestState.Success -> "success: $answer"
    is AnalyzeRequestState.Error -> "error: $message"
}

@Composable
private fun CaptureResultView(result: CaptureResult, screenshotPath: String? = null) {
    val clickableNodes = result.nodes.filter { it.clickable }
    val otherNodes = result.nodes.filter { !it.clickable }

    Column {
        Row {
            Text(
                text = "包名: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = result.packageName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
        Row {
            Text(
                text = "页面: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = result.activityName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "节点: ${result.nodes.size} 个 (可点击 ${clickableNodes.size} / 可编辑 ${result.nodes.count { it.editable }})",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
        if (result.screenWidth > 0) {
            Text(
                text = "屏幕: ${result.screenWidth}x${result.screenHeight}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
        if (result.imageWidth > 0) {
            Text(
                text = "截图: ${result.imageWidth}x${result.imageHeight} | ${result.screenshotBytes?.size?.div(1024) ?: 0}KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            if (result.screenWidth > 0 && result.imageWidth != result.screenWidth) {
                Text(
                    text = "缩放: x${"%.2f".format(result.scaleX)} / y${"%.2f".format(result.scaleY)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (screenshotPath != null) {
                Text(
                    text = "文件: $screenshotPath",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                )
            }
        }

        if (clickableNodes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "可点击:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            clickableNodes.take(6).forEach { node ->
                val label = node.text.ifEmpty { node.contentDescription.ifEmpty { "[${node.className}]" } }
                val b = node.bounds
                Text(
                    text = "  $label${if (node.editable) " [editable]" else ""}  (${b.left},${b.top})-(${b.right},${b.bottom})",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (otherNodes.isNotEmpty() && otherNodes.size <= 4) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "其他节点:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            otherNodes.forEach { node ->
                val label = node.text.ifEmpty { node.contentDescription.ifEmpty { "[${node.className}]" } }
                Text(
                    text = "  $label${if (node.editable) " [editable]" else ""}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
