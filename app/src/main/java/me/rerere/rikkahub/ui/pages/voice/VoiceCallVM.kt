package me.rerere.rikkahub.ui.pages.voice

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.hooks.CustomAsrState
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.tts.model.PlaybackStatus
import kotlin.uuid.Uuid

private const val TAG = "VoiceCallVM"

/**
 * 语音通话 ViewModel
 *
 * 编排 ASR -> ChatService -> TTS 的连续对话循环.
 *
 * 状态机:
 *   Idle -> Listening -> Processing -> Speaking -> Listening -> ...
 */
class VoiceCallVM(
    private val conversationId: String,
    private val context: Application,
    private val chatService: ChatService,
) : ViewModel() {

    private val _conversationId: Uuid = Uuid.parse(conversationId)

    private val _uiState = MutableStateFlow(VoiceCallUiState())
    val uiState: StateFlow<VoiceCallUiState> = _uiState.asStateFlow()

    val conversation: StateFlow<Conversation> = chatService.getConversationFlow(_conversationId)

    private var vadJob: Job? = null
    private var speakingMonitorJob: Job? = null
    private var conversationMonitorJob: Job? = null
    private var asrMonitorJob: Job? = null
    private var lastSpokenText: String = ""

    // 跟踪 AI 消息的增量, 用于流式 TTS
    private var lastAssistantText: String = ""
    private var hasSentCurrentMessage = false

    // 流式 TTS: 记录已发送给 TTS 的文本长度
    private var ttsSentLength: Int = 0

    /**
     * 开始语音通话
     */
    fun startCall(asr: CustomAsrState, tts: CustomTtsState) {
        if (_uiState.value.status != VoiceCallStatus.Idle) return
        lastAssistantText = ""
        lastSpokenText = ""
        hasSentCurrentMessage = false
        ttsSentLength = 0
        startListening(asr, tts)
        // 监听对话变化, 实现流式 TTS
        startConversationMonitor(asr, tts)
    }

    /**
     * 开始监听用户语音
     */
    fun startListening(asr: CustomAsrState, tts: CustomTtsState) {
        // 停止 TTS
        tts.stop()
        ttsSentLength = 0
        lastAssistantText = ""
        hasSentCurrentMessage = false

        _uiState.update {
            it.copy(
                status = VoiceCallStatus.Listening,
                userTranscript = "",
                errorMessage = null
            )
        }

        asr.start { transcript ->
            _uiState.update { it.copy(userTranscript = transcript) }
        }

        // 启动 VAD 自动检测（用于流式 ASR 如 OpenAI Realtime）
        startVadDetection(asr, tts)

        // 监听 ASR 状态变化（用于非流式 ASR 如 SiliconFlow）
        // 当 ASR 从 Recording -> Idle 且转写不为空时，立即发送
        startAsrMonitor(asr, tts)
    }

    /**
     * VAD: 检测用户停顿后自动发送
     * 优化: 更快的响应时间, 更灵敏的检测
     */
    private fun startVadDetection(asr: CustomAsrState, tts: CustomTtsState) {
        vadJob?.cancel()
        vadJob = viewModelScope.launch {
            var lastTranscript = ""
            var silenceStartTime: Long = 0L
            var lastAmplitudeTime: Long = System.currentTimeMillis()
            val silenceThresholdMs = 800L // 优化: 从 1500ms 降到 800ms, 更快响应
            val minTranscriptLength = 2 // 最少 2 个字符才发送
            val amplitudeTimeoutMs = 2000L // 音量持续低迷 2 秒也触发

            while (true) {
                delay(100) // 优化: 从 200ms 降到 100ms, 更频繁检测
                if (_uiState.value.status != VoiceCallStatus.Listening) break
                if (!_uiState.value.autoSendEnabled) continue

                val currentTranscript = _uiState.value.userTranscript
                val amplitudes = _uiState.value.amplitudes
                val recentAmplitude = amplitudes.takeLast(3).average().toFloat()

                // 检测音量活动 - 如果有声音就重置计时
                if (recentAmplitude > 0.05f) {
                    lastAmplitudeTime = System.currentTimeMillis()
                }

                if (currentTranscript != lastTranscript) {
                    // 转写还在变化, 重置静音计时
                    lastTranscript = currentTranscript
                    silenceStartTime = 0L
                } else if (currentTranscript.length >= minTranscriptLength) {
                    // 转写稳定且有内容, 开始/继续计时
                    if (silenceStartTime == 0L) {
                        silenceStartTime = System.currentTimeMillis()
                    }
                    val silentFor = System.currentTimeMillis() - silenceStartTime
                    val amplitudeSilentFor = System.currentTimeMillis() - lastAmplitudeTime

                    // 触发条件: 转写稳定且静音足够, 或音量持续低迷
                    if (silentFor >= silenceThresholdMs || amplitudeSilentFor >= amplitudeTimeoutMs) {
                        Log.d(TAG, "VAD triggered auto-send: $currentTranscript (silentFor=$silentFor, ampSilent=$amplitudeSilentFor)")
                        sendCurrentMessage(asr, tts)
                        break
                    }
                }
            }
        }
    }

    /**
     * 手动结束说话并发送
     */
    fun manualSend(asr: CustomAsrState, tts: CustomTtsState) {
        if (_uiState.value.status != VoiceCallStatus.Listening) return
        sendCurrentMessage(asr, tts)
    }

    /**
     * 发送当前转写的消息
     */
    private fun sendCurrentMessage(asr: CustomAsrState, tts: CustomTtsState) {
        val transcript = _uiState.value.userTranscript.trim()
        vadJob?.cancel()

        // 停止 ASR
        asr.stop()

        if (transcript.isBlank()) {
            // 没有有效内容, 回到监听
            startListening(asr, tts)
            return
        }

        _uiState.update {
            it.copy(
                status = VoiceCallStatus.Processing,
                assistantText = ""
            )
        }
        ttsSentLength = 0
        lastAssistantText = ""

        // 发送消息
        chatService.sendMessage(
            _conversationId,
            listOf(UIMessagePart.Text(transcript))
        )
    }

    /**
     * 监听对话流变化, 实现:
     * 1. 流式 TTS (检测到新句子即朗读)
     * 2. AI 开始输出时立即进入 Speaking 状态, 让用户可以打断
     * 3. AI 回复完成后回到 Listening
     */
    private fun startConversationMonitor(asr: CustomAsrState, tts: CustomTtsState) {
        conversationMonitorJob?.cancel()
        conversationMonitorJob = viewModelScope.launch {
            conversation.collect { conv ->
                if (_uiState.value.status != VoiceCallStatus.Processing && 
                    _uiState.value.status != VoiceCallStatus.Speaking) return@collect

                val lastMessage = conv.currentMessages.lastOrNull()
                if (lastMessage?.role != MessageRole.ASSISTANT) return@collect

                val currentText = lastMessage.toText()

                // 更新 UI 显示的 AI 回复
                _uiState.update { it.copy(assistantText = currentText) }

                // 流式 TTS: 只朗读新增的部分
                if (currentText.length > ttsSentLength) {
                    val newText = currentText.substring(ttsSentLength)
                    // 按句子分割, 朗读完整句子
                    val sentences = extractCompleteSentences(newText)
                    for (sentence in sentences) {
                        if (sentence.isNotBlank()) {
                            tts.enqueueText(sentence)
                            Log.d(TAG, "Streaming TTS: $sentence")
                        }
                    }
                    ttsSentLength = currentText.length - getPendingRemainder(newText).length
                }

                // 优化: 一旦 AI 有内容输出, 立即切换到 Speaking 状态
                // 这样用户随时可以打断, UI 反馈更即时
                if (_uiState.value.status == VoiceCallStatus.Processing && currentText.isNotBlank()) {
                    _uiState.update { it.copy(status = VoiceCallStatus.Speaking) }
                }

                lastAssistantText = currentText
            }
        }

        // 监听生成完成 -> 等待 TTS 播放完成 -> 回到 Listening
        speakingMonitorJob?.cancel()
        speakingMonitorJob = viewModelScope.launch {
            chatService.generationDoneFlow.collect { convId ->
                if (convId != _conversationId) return@collect
                onGenerationDone(tts, asr)
            }
        }
    }

    private suspend fun onGenerationDone(tts: CustomTtsState, asr: CustomAsrState) {
        // 朗读最后剩余的文本
        val finalText = _uiState.value.assistantText
        if (finalText.length > ttsSentLength) {
            val remaining = finalText.substring(ttsSentLength)
            if (remaining.isNotBlank()) {
                tts.enqueueText(remaining)
                ttsSentLength = finalText.length
            }
        }

        _uiState.update { it.copy(status = VoiceCallStatus.Speaking) }

        // 等待 TTS 播放完成
        waitForTtsToFinish(tts)

        // 回到监听
        if (_uiState.value.status == VoiceCallStatus.Speaking) {
            startListening(asr, tts)
        }
    }

    private suspend fun waitForTtsToFinish(tts: CustomTtsState) {
        // 等待 TTS 开始播放
        var waitStart = System.currentTimeMillis()
        while (!tts.isSpeaking.value && System.currentTimeMillis() - waitStart < 5000) {
            delay(100)
        }
        // 等待 TTS 播放完成
        while (tts.isSpeaking.value) {
            delay(200)
        }
        // 额外等待状态更新
        delay(300)
    }

    /**
     * 从增量文本中提取完整的句子(以句号/问号/感叹号/换行结尾)
     */
    private fun extractCompleteSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (char in text) {
            current.append(char)
            if (char == '。' || char == '？' || char == '！' || char == '.' ||
                char == '?' || char == '!' || char == '\n'
            ) {
                val sentence = current.toString().trim()
                if (sentence.isNotEmpty()) {
                    result.add(sentence)
                }
                current.clear()
            }
        }
        // 保存未完成的部分(不朗读, 等下次)
        return result
    }

    /**
     * 获取增量文本中未形成完整句子的剩余部分
     */
    private fun getPendingRemainder(text: String): String {
        val lastSentenceEnd = text.lastIndexOfAny(charArrayOf('。', '？', '！', '.', '?', '!', '\n'))
        return if (lastSentenceEnd >= 0 && lastSentenceEnd < text.length - 1) {
            text.substring(lastSentenceEnd + 1)
        } else if (lastSentenceEnd < 0) {
            text
        } else {
            ""
        }
    }

    /**
     * 用户打断 AI 说话 (Barge-in)
     */
    fun interruptSpeaking(asr: CustomAsrState, tts: CustomTtsState) {
        if (_uiState.value.status != VoiceCallStatus.Speaking) return
        speakingMonitorJob?.cancel()
        startListening(asr, tts)
    }

    /**
     * 监听 ASR 状态（用于非流式 ASR 如 SiliconFlow）
     * 当 ASR 从 Recording -> Idle 且转写不为空时，立即发送
     */
    private fun startAsrMonitor(asr: CustomAsrState, tts: CustomTtsState) {
        asrMonitorJob?.cancel()
        asrMonitorJob = viewModelScope.launch {
            var wasRecording = false
            asr.state.collect { asrState ->
                val isRecording = asrState.isRecording
                
                // 检测到从 Recording 变为非 Recording
                if (wasRecording && !isRecording && _uiState.value.status == VoiceCallStatus.Listening) {
                    val transcript = asrState.transcript.trim()
                    if (transcript.isNotEmpty() && _uiState.value.autoSendEnabled) {
                        Log.d(TAG, "ASR monitor: Auto-send after ASR completed: $transcript")
                        sendCurrentMessage(asr, tts)
                    }
                }
                
                wasRecording = isRecording
            }
        }
    }

    /**
     * 切换静音
     */
    fun toggleMute(asr: CustomAsrState) {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
        if (_uiState.value.isMuted) {
            asr.stop()
        } else if (_uiState.value.status == VoiceCallStatus.Listening) {
            asr.start { transcript ->
                _uiState.update { it.copy(userTranscript = transcript) }
            }
        }
    }

    /**
     * 切换自动发送模式
     */
    fun toggleAutoSend() {
        _uiState.update { it.copy(autoSendEnabled = !it.autoSendEnabled) }
    }

    /**
     * 挂断 / 结束通话
     */
    fun endCall(asr: CustomAsrState, tts: CustomTtsState) {
        vadJob?.cancel()
        speakingMonitorJob?.cancel()
        conversationMonitorJob?.cancel()
        asrMonitorJob?.cancel()
        asr.stop()
        tts.stop()
        _uiState.update {
            it.copy(status = VoiceCallStatus.Idle)
        }
    }

    /**
     * 更新振幅数据 (供 UI 动画使用)
     */
    fun updateAmplitudes(amplitudes: List<Float>) {
        _uiState.update { it.copy(amplitudes = amplitudes) }
    }

    override fun onCleared() {
        super.onCleared()
        vadJob?.cancel()
        speakingMonitorJob?.cancel()
        conversationMonitorJob?.cancel()
    }
}