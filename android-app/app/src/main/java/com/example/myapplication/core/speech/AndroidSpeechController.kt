package com.example.myapplication.core.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import java.util.Locale

class AndroidSpeechController(
    private val context: Context,
    private val onPartialText: (String) -> Unit,
    private val onFinalText: (String) -> Unit,
    private val onRecoverableError: () -> Unit,
    private val onError: (String) -> Unit,
) : RecognitionListener,
    TextToSpeech.OnInitListener {

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context.applicationContext).also {
                it.setRecognitionListener(this)
            }
        } else {
            null
        }
    private val textToSpeech = TextToSpeech(context.applicationContext, this)
    private var isListening = false
    private var ttsReady = false
    private var pendingSpeech: String? = null

    fun startListening() {
        val currentRecognizer = recognizer
        if (currentRecognizer == null) {
            onError("当前设备不可用语音识别")
            return
        }
        if (!hasRecordAudioPermission()) {
            onError("请先授予麦克风权限")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出问题")
        }
        runCatching {
            isListening = true
            currentRecognizer.startListening(intent)
        }.onFailure {
            isListening = false
            onError("语音识别启动失败")
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        runCatching {
            recognizer?.stopListening()
        }
    }

    fun speak(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        if (!ttsReady) {
            pendingSpeech = trimmedText
            return
        }
        textToSpeech.speak(trimmedText, TextToSpeech.QUEUE_FLUSH, null, "analyze-answer")
    }

    fun destroy() {
        stopListening()
        recognizer?.destroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.getDefault()
            ttsReady = true
            pendingSpeech?.let(::speak)
            pendingSpeech = null
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        readSpeechText(results)?.let(onFinalText)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        readSpeechText(partialResults)?.let(onPartialText)
    }

    override fun onError(error: Int) {
        if (!isListening) return
        isListening = false
        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        ) {
            onRecoverableError()
        }
        onError(error.toUserMessage())
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    private fun readSpeechText(results: Bundle?): String? =
        results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun Int.toUserMessage(): String = when (this) {
        SpeechRecognizer.ERROR_AUDIO -> "录音失败，请重试"
        SpeechRecognizer.ERROR_CLIENT -> "语音识别启动失败"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "请先授予麦克风权限"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> "语音识别网络不可用"
        SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到有效语音"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别正忙，请稍后再试"
        SpeechRecognizer.ERROR_SERVER -> "语音识别服务暂不可用"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到语音"
        else -> "语音识别失败"
    }
}
