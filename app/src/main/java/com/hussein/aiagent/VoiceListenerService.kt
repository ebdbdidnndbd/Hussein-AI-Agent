package com.hussein.aiagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.*

class VoiceListenerService : Service(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var listenLoopActive = false // يمنع تشغيل أكثر من حلقة استماع بنفس الوقت
    private var lastProcessedCommand: String = ""
    private var lastProcessedTime: Long = 0

    // ---- ضوضاء بيضاء/صوت تركيز مولّد محلياً (بدون نت ولا ملفات صوت) ----
    private var noiseTrack: AudioTrack? = null
    private var noiseThread: Thread? = null
    @Volatile private var noisePlaying = false

    companion object {
        var instance: VoiceListenerService? = null
        private var ttsInstance: TextToSpeech? = null
        var lastRecognizedText: ((String, String) -> Unit)? = null

        fun speakOut(message: String) {
            ttsInstance?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        private val unknownCommandReplies = listOf(
            "ما فهمت الأمر يا حسين، جرب تگول أمر ثاني.",
            "الإشارة مو واضحة يا قائد، عيدها بصيغة ثانية.",
            "صگر ما يعرف هذا الأمر، جرب چلمة أوضح."
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AgentPrefs.init(this)
        startForegroundNotification()
        ttsInstance = TextToSpeech(this, this)
        initRecognizer()
    }

    // ---- توليد ضوضاء بيضاء هادئة بالكود مباشرة (بدون ملفات صوت ولا إنترنت) ----
    fun startWhiteNoise() {
        if (noisePlaying) return
        noisePlaying = true
        serviceScope.launch {
            try {
                val sampleRate = 44100
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = if (minBuf > 0) minBuf else 4096
                noiseTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                noiseTrack?.play()

                val buffer = ShortArray(bufferSize / 2)
                val random = Random()
                var lastSample = 0f
                while (noisePlaying) {
                    for (i in buffer.indices) {
                        // فلترة بسيطة تحول الضوضاء البيضاء الحادة لصوت هادئ شبيه بالمطر
                        val white = (random.nextFloat() * 2f - 1f)
                        lastSample = lastSample * 0.95f + white * 0.05f
                        buffer[i] = (lastSample * 6000).toInt().toShort()
                    }
                    noiseTrack?.write(buffer, 0, buffer.size)
                }
            } catch (_: Exception) {
                noisePlaying = false
            }
        }
    }

    fun stopWhiteNoise() {
        noisePlaying = false
        mainHandler.postDelayed({
            try {
                noiseTrack?.stop()
                noiseTrack?.release()
            } catch (_: Exception) {
            }
            noiseTrack = null
        }, 150)
    }

    fun isNoisePlaying(): Boolean = noisePlaying

    private fun startForegroundNotification() {
        val channelId = "SagerAIChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "صگر الفضائي بالخلفية",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("🛸 صگر شغال بالخلفية")
            .setContentText("أسمعك يا حسين.. صيح (يا صگر) أو اضغط هنا")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = ttsInstance?.setLanguage(Locale("ar"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsInstance?.language = Locale.getDefault()
            }
            speakOut("أني صگر يا حسين، هبطت من الفضاء وشغال بالخلفية وجاهز لأوامرك.")
        }
    }

    private fun initRecognizer() {
        if (listenLoopActive) return // الحلقة شغالة أصلاً، ما نبدأ وحدة ثانية فوگها
        listenLoopActive = true
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) return@post
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }

                override fun onError(error: Int) {
                    isListening = false
                    scheduleNextListen(1200)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0]
                        processCommand(spokenText)
                    }
                    scheduleNextListen(1000)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListenNow()
        }
    }

    private fun scheduleNextListen(delayMs: Long) {
        mainHandler.postDelayed({
            if (!isListening) startListenNow()
        }, delayMs)
    }

    private fun startListenNow() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-IQ")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            scheduleNextListen(2000)
        }
    }

    // توحيد الأحرف العراقية المختلفة حتى يفهم الأمر مهما كانت صيغته
    private fun normalizeCommand(raw: String): String {
        return raw.trim()
            .replace("گ", "ك")
            .replace("چ", "ج")
            .replace("ژ", "ز")
            .replace("ة", "ه")
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ى", "ي")
            .replace(Regex("\\s+"), " ")
    }

    fun processCommand(command: String) {
        mainHandler.post {
            val normalized = normalizeCommand(command)
            val now = System.currentTimeMillis()

            // حماية من معالجة نفس الأمر مرتين خلال وقت قصير (تمنع التكرار)
            if (normalized == lastProcessedCommand && (now - lastProcessedTime) < 2500) {
                return@post
            }
            lastProcessedCommand = normalized
            lastProcessedTime = now

            // 1. نفحص إذا الأمر يتنفذ فوراً بدون تأخير (Zero Latency)
            val handledFast = AgentAccessibilityService.instance?.handleFastCommand(normalized) ?: false
            if (handledFast) {
                lastRecognizedText?.invoke(command, "⚡ تم التنفيذ الفوري!")
                return@post
            }

            // 2. إذا مو أمر معروف من القائمة الجاهزة
            val reply = unknownCommandReplies.random()
            speakOut(reply)
            lastRecognizedText?.invoke(command, reply)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ما نبدأ حلقة استماع جديدة هنا؛ initRecognizer() بـ onCreate هو المسؤول الوحيد عن بدء الحلقة.
        // هذا يمنع تراكم عدة حلقات استماع متوازية تسبب تكرار الكلام والتعليق.
        if (speechRecognizer == null) initRecognizer()
        return START_STICKY // يضمن بقاء الخدمة بالخلفية دائماً
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        listenLoopActive = false
        noisePlaying = false
        try {
            noiseTrack?.stop()
            noiseTrack?.release()
        } catch (_: Exception) {
        }
        mainHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        ttsInstance?.stop()
        ttsInstance?.shutdown()
        serviceScope.cancel()
    }
}
