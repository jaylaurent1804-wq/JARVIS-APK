package com.kimmuel.jarvis

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale

private val cyan = Color(0xFF39DFFF)
private val bg = Color(0xFF050B12)
private val panel = Color(0xFF0B1B29)

class MainActivity : ComponentActivity() {
    private val client = OkHttpClient()
    private var tts: TextToSpeech? = null
    private var speech: SpeechRecognizer? = null
    private var onRecognized: ((String) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Android may not allow an app to open itself over another app.
            // The foreground notification is the supported hand-off in that case.
            if (intent?.action == WakeWordService.ACTION_WAKE) {
                getSharedPreferences("jarvis", MODE_PRIVATE).edit().putBoolean("wakeDetected", true).apply()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US }
        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        ContextCompat.registerReceiver(this, wakeReceiver, IntentFilter(WakeWordService.ACTION_WAKE), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            var wakeOn by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            var listening by remember { mutableStateOf(false) }
            var input by remember { mutableStateOf("") }
            var reply by remember { mutableStateOf("Good day. All primary interface modules are standing by.") }
            var status by remember { mutableStateOf("SYSTEM READY") }
            var history by remember { mutableStateOf(listOf<Pair<String,String>>()) }
            val scope = rememberCoroutineScope()
            MaterialTheme(colorScheme = darkColorScheme(primary = cyan, background = bg, surface = panel)) {
                Surface(Modifier.fillMaxSize(), color = bg) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text("J.A.R.V.I.S.", color = cyan, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp); Text("PERSONAL INTELLIGENCE SYSTEM", color = Color(0xFF78A7B9), fontSize = 9.sp, letterSpacing = 2.sp) }
                            Text("● ONLINE", color = Color(0xFF50E6B2), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(20.dp))
                        Reactor(status = status)
                        Spacer(Modifier.height(18.dp))
                        Text(reply, color = Color(0xFFD5EAF2), fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(panel).padding(16.dp))
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HudButton("TALK", "MICROPHONE", Modifier.weight(1f), enabled = !busy) {
                                startCommandRecognition({ listening = true; status = "LISTENING FOR COMMAND" }, { phrase -> listening = false; input = phrase; status = "PROCESSING"; scope.launch { send(phrase, history, client, { busy = true }, { answer -> busy = false; reply = answer; history = history + (phrase to answer); status = "SYSTEM READY"; tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply") }, { error -> busy = false; status = "CONNECTION ERROR"; reply = error }) } }, { listening = false; status = "VOICE UNAVAILABLE"; reply = it })
                            }
                            HudButton(if (wakeOn) "STOP" else "ENABLE", "HEY JARVIS", Modifier.weight(1f), enabled = true) {
                                if (!wakeOn) {
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, WakeWordService::class.java)); wakeOn = true; status = "WAKE WORD ACTIVE"
                                    } else { permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
                                } else { stopService(Intent(this@MainActivity, WakeWordService::class.java)); wakeOn = false; status = "SYSTEM READY" }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("COMMAND CONSOLE", color = cyan, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.Start))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Ask JARVIS anything...", color = Color.Gray) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = cyan, unfocusedBorderColor = Color(0xFF245064), focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                        Spacer(Modifier.height(8.dp))
                        HudButton(if (busy) "PROCESSING..." else "SEND COMMAND", "OPENAI INTELLIGENCE", Modifier.fillMaxWidth(), enabled = !busy && input.isNotBlank()) {
                            val q = input.trim(); input = ""; status = "CONTACTING AI CORE"
                            scope.launch { send(q, history, client, { busy = true }, { answer -> busy = false; reply = answer; history = history + (q to answer); status = "SYSTEM READY"; tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply") }, { error -> busy = false; status = "CONNECTION ERROR"; reply = error }) }
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("AI CORE", "CONNECTED"); Metric("VOICE", if (listening) "LISTENING" else "STANDBY"); Metric("WAKE WORD", if (wakeOn) "ON" else "OFF") }
                        Spacer(Modifier.height(20.dp))
                        Text("JARVIS // ANDROID BUILD 1.0", color = Color(0xFF52798A), fontSize = 9.sp, letterSpacing = 2.sp)
                    }
                }
            }
        }
    }

    private fun startCommandRecognition(onStart: () -> Unit, onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { onError("Speech recognition is not available on this device."); return }
        speech?.destroy(); speech = SpeechRecognizer.createSpeechRecognizer(this)
        speech?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onStart()
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { onError("Voice recognition error ($error). Check microphone permission and speech service.") }
            override fun onResults(results: Bundle?) { val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); if (text.isNullOrBlank()) onError("I didn't catch that. Please try again.") else onResult(text) }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault()); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false) }
        speech?.startListening(intent)
    }

    override fun onDestroy() { super.onDestroy(); try { unregisterReceiver(wakeReceiver) } catch (_: Exception) {}; speech?.destroy(); tts?.shutdown() }
}

@Composable private fun Reactor(status: String) {
    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            drawCircle(Color(0xFF123D50), radius = size.minDimension * .47f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawCircle(cyan.copy(alpha=.8f), radius = size.minDimension * .40f, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(16f,8f))))
            drawCircle(Color(0xFF1B7897), radius = size.minDimension * .30f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8BF3FF), Color(0xFF147D9B), Color(0xFF071725)), c, size.minDimension*.25f), radius = size.minDimension*.25f)
            drawCircle(cyan, radius = size.minDimension*.055f)
            drawCircle(Color.White, radius = size.minDimension*.022f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("J", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Light); Text(status, color = cyan, fontSize = 9.sp, letterSpacing = 1.5.sp) }
    }
}
@Composable private fun HudButton(label: String, sub: String, modifier: Modifier = Modifier, enabled: Boolean = true, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = modifier.height(58.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10364A), contentColor = cyan, disabledContainerColor = Color(0xFF14232C))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(sub, fontSize = 8.sp, letterSpacing = 1.sp) }
    }
}
@Composable private fun Metric(label: String, value: String) { Column(Modifier.clip(RoundedCornerShape(8.dp)).background(panel).padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(label, color = Color(0xFF78A7B9), fontSize = 8.sp); Text(value, color = cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }

private suspend fun send(prompt: String, history: List<Pair<String,String>>, client: OkHttpClient, loading: () -> Unit, success: (String) -> Unit, failure: (String) -> Unit) {
    loading()
    withContext(Dispatchers.IO) {
        try {
            val turns = mutableListOf<JSONObject>()
            history.takeLast(8).forEach { pair ->
                turns.add(JSONObject().put("role", "user").put("content", pair.first))
                turns.add(JSONObject().put("role", "assistant").put("content", pair.second))
            }
            turns.add(JSONObject().put("role", "user").put("content", prompt))
            val body = JSONObject().put("messages", org.json.JSONArray(turns)).toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(BuildConfigField.API_BASE_URL + "/chat").post(body).build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw Exception("Backend returned ${response.code}: ${raw.take(250)}")
                val answer = JSONObject(raw).optString("reply", "No response received.")
                withContext(Dispatchers.Main) { success(answer) }
            }
        } catch (e: Exception) { withContext(Dispatchers.Main) { failure("I couldn't reach the AI core. ${e.message ?: "Check your server connection."}") } }
    }
}
private object BuildConfigField { const val API_BASE_URL = BuildConfig.JARVIS_API_BASE_URL }
