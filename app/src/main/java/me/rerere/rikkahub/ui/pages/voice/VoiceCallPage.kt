package me.rerere.rikkahub.ui.pages.voice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.MicOff01
import me.rerere.hugeicons.stroke.Voice
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalTTSState
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

/**
 * 语音通话页面
 *
 * 类似微信通话 / ChatGPT Voice 的全屏沉浸式语音对话界面.
 * 进入页面后自动开始监听, 实现 ASR -> AI -> TTS 的连续对话循环.
 */
@Composable
fun VoiceCallPage(
    conversationId: Uuid,
    onBack: () -> Unit,
) {
    val vm: VoiceCallVM = koinViewModel(
        parameters = { parametersOf(conversationId.toString()) }
    )
    val asr = LocalASRState.current
    val tts = LocalTTSState.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val asrState by asr.state.collectAsStateWithLifecycle()

    // 录音权限
    val asrPermission = rememberPermissionState(PermissionRecordAudio)

    // 当 ASR 振幅更新时, 同步到 UI
    LaunchedEffect(asrState.amplitudes) {
        vm.updateAmplitudes(asrState.amplitudes)
    }

    // 页面启动: 请求权限并开始通话
    LaunchedEffect(Unit) {
        if (asrPermission.allRequiredPermissionsGranted) {
            vm.startCall(asr, tts)
        } else {
            asrPermission.requestPermissions()
        }
    }

    // 权限授予后自动开始
    LaunchedEffect(asrPermission.allRequiredPermissionsGranted) {
        if (asrPermission.allRequiredPermissionsGranted && uiState.status == VoiceCallStatus.Idle) {
            vm.startCall(asr, tts)
        }
    }

    // 处理返回键 = 挂断
    BackHandler {
        vm.endCall(asr, tts)
        onBack()
    }

    // 退出时清理
    DisposableEffect(Unit) {
        onDispose {
            vm.endCall(asr, tts)
        }
    }

    // 渐变背景 - 更鲜艳、更有呼吸感
    val backgroundColor = when (uiState.status) {
        VoiceCallStatus.Listening -> Color(0xFF2E7D32)
        VoiceCallStatus.Speaking -> Color(0xFF1565C0)
        VoiceCallStatus.Processing -> Color(0xFF4527A0)
        VoiceCallStatus.Error -> Color(0xFFC62828)
        VoiceCallStatus.Idle -> Color(0xFF0D1117)
    }

    val statusPulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = 0.7f),
                        Color.Black.copy(alpha = 0.9f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部: 标题和状态
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 60.dp)
            ) {
                Text(
                    text = "语音通话",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 状态指示器 - 带脉冲动画
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 状态脉冲点
                    val dotColor = when (uiState.status) {
                        VoiceCallStatus.Listening -> Color(0xFF69F0AE)
                        VoiceCallStatus.Speaking -> Color(0xFF82B1FF)
                        VoiceCallStatus.Processing -> Color(0xFFB388FF)
                        VoiceCallStatus.Error -> Color(0xFFFF8A80)
                        VoiceCallStatus.Idle -> Color.White.copy(alpha = 0.5f)
                    }
                    val dotAlpha = if (uiState.status == VoiceCallStatus.Idle) 1f else statusPulse
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = dotColor.copy(alpha = dotAlpha),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = statusText(uiState.status),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            }

            // 中部: 声波球 + 实时文字
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // 声波球
                VoiceOrb(
                    amplitudes = uiState.amplitudes,
                    status = uiState.status,
                    size = 240.dp
                )

                Spacer(modifier = Modifier.height(40.dp))

                // 实时转写/回复文字
                AnimatedVisibility(
                    visible = uiState.userTranscript.isNotBlank() || uiState.assistantText.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        // 用户转写
                        if (uiState.userTranscript.isNotBlank()) {
                            Text(
                                text = uiState.userTranscript,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 3
                            )
                        }
                        // AI 回复
                        if (uiState.assistantText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = uiState.assistantText,
                                color = Color.White,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 8
                            )
                        }
                    }
                }

                // 错误信息
                uiState.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            // 底部: 控制按钮
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                // 自动发送模式切换
                Surface(
                    onClick = { vm.toggleAutoSend() },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = if (uiState.autoSendEnabled) 0.2f else 0.1f)
                ) {
                    Text(
                        text = if (uiState.autoSendEnabled) "自动发送: 开" else "自动发送: 关",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 主控制按钮组
                Row(
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 静音按钮
                    ControlButton(
                        icon = if (uiState.isMuted) HugeIcons.MicOff01 else HugeIcons.Mic01,
                        contentDescription = "静音",
                        onClick = { vm.toggleMute(asr) },
                        backgroundColor = Color.White.copy(alpha = 0.2f),
                        iconTint = Color.White
                    )

                    // 中间: 根据状态显示不同按钮
                    when (uiState.status) {
                        VoiceCallStatus.Listening -> {
                            // 手动发送按钮 (自动模式关闭时显示)
                            if (!uiState.autoSendEnabled) {
                                ControlButton(
                                    icon = HugeIcons.Voice,
                                    contentDescription = "发送",
                                    onClick = { vm.manualSend(asr, tts) },
                                    backgroundColor = MaterialTheme.colorScheme.primary,
                                    iconTint = Color.White,
                                    size = 72.dp
                                )
                            } else {
                                // 自动模式: 显示正在监听的状态
                                Box(
                                    modifier = Modifier.size(72.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White.copy(alpha = 0.5f),
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }

                        VoiceCallStatus.Speaking -> {
                            // 打断按钮
                            ControlButton(
                                icon = HugeIcons.Mic01,
                                contentDescription = "打断",
                                onClick = { vm.interruptSpeaking(asr, tts) },
                                backgroundColor = MaterialTheme.colorScheme.primary,
                                iconTint = Color.White,
                                size = 72.dp
                            )
                        }

                        VoiceCallStatus.Processing -> {
                            // 处理中: 加载动画
                            Box(
                                modifier = Modifier.size(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        else -> {
                            // 开始通话
                            ControlButton(
                                icon = HugeIcons.Voice,
                                contentDescription = "开始",
                                onClick = { vm.startCall(asr, tts) },
                                backgroundColor = MaterialTheme.colorScheme.primary,
                                iconTint = Color.White,
                                size = 72.dp
                            )
                        }
                    }

                    // 挂断按钮
                    ControlButton(
                        icon = HugeIcons.Cancel01,
                        contentDescription = "挂断",
                        onClick = {
                            vm.endCall(asr, tts)
                            onBack()
                        },
                        backgroundColor = MaterialTheme.colorScheme.error,
                        iconTint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 控制按钮
 */
@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    iconTint: Color,
    size: Dp = 56.dp,
) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = backgroundColor,
        modifier = Modifier.size(size)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(size * 0.4f)
            )
        }
    }
}

private fun statusText(status: VoiceCallStatus): String = when (status) {
    VoiceCallStatus.Idle -> "准备就绪"
    VoiceCallStatus.Listening -> "正在聆听..."
    VoiceCallStatus.Processing -> "正在思考..."
    VoiceCallStatus.Speaking -> "正在说话..."
    VoiceCallStatus.Error -> "出错了"
}