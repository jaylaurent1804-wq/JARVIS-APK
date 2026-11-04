package com.kimmuel.jarvis

import android.app.*
import android.content.*
import android.os.*
import android.speech.*
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Foreground-service wake phrase listener. Android SpeechRecognizer performs the
 * recognition; this is not a dedicated always-on DSP wake-word engine.
 * Start it from the visible Activity after granting microphone permission.
 */
class WakeWordService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    override fun onBind(intent: Intent?) = null
    override fun onCreate() { super.onCreate(); createChannel(); startForeground(NOTIFICATION_ID, notification("Listening for Hey JARVIS")); beginListening() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { if (!listening) beginListening(); return START_NOT_STICKY }
    private fun beginListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { stopSelf(); return }
        try {
            recognizer?.destroy(); recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }
                override fun onError(error: Int) { listening = false; if (Build.VERSION.SDK_INT >= 26) Handler(Looper.getMainLooper()).postDelayed({ beginListening() }, 900) else beginListening() }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (phrases.any { it.lowercase(Locale.ROOT).contains("hey jarvis") || it.lowercase(Locale.ROOT).contains("hey j.a.r.v.i.s") }) {
                        sendBroadcast(Intent(ACTION_WAKE).setPackage(packageName))
                        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
                        val pending = PendingIntent.getActivity(this@WakeWordService, 12, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        val n = NotificationCompat.Builder(this@WakeWordService, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("JARVIS heard you").setContentText("Tap to open your assistant and give a command.").setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
                        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(13, n)
                    }
                    Handler(Looper.getMainLooper()).postDelayed({ beginListening() }, 500)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault()); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false); putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) }
            recognizer?.startListening(intent)
        } catch (_: Exception) { stopSelf() }
    }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) { val channel = NotificationChannel(CHANNEL, "JARVIS wake word", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(channel) } }
    private fun notification(message: String) = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("J.A.R.V.I.S. is active").setContentText(message).setOngoing(true).build()
    override fun onDestroy() { recognizer?.destroy(); recognizer = null; listening = false; super.onDestroy() }
    companion object { const val ACTION_WAKE = "com.kimmuel.jarvis.WAKE_DETECTED"; private const val CHANNEL = "jarvis_wake"; private const val NOTIFICATION_ID = 12 }
}
