package com.glucoguide.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null

    private val hcPermissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    private val voiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val text = res.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: ""
            runJs("window.onVoiceResult(" + JSONObject.quote(text) + ")")
        }

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val cb = filePathCallback ?: return@registerForActivityResult
            var uris: Array<Uri>? = null
            if (res.resultCode == RESULT_OK) {
                val picked = res.data?.data
                uris = when {
                    picked != null -> arrayOf(picked)
                    cameraUri != null -> arrayOf(cameraUri!!)
                    else -> null
                }
            }
            cb.onReceiveValue(uris)
            filePathCallback = null
            cameraUri = null
        }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result handled by OS */ }

    private val hcPermLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.containsAll(hcPermissions)) {
                readStepsToday()
            } else {
                runJs("window.onNativeMessage('Health Connect permission was not granted')")
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // localStorage persistence
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true   // lets the local app call the AI API
        }
        webView.addJavascriptInterface(Bridge(), "Android")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                launchPhotoChooser()
                return true
            }
        }
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun launchPhotoChooser() {
        val gallery = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        var camera: Intent? = null
        try {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Photos").apply { mkdirs() }
            val photo = File(dir, "meal_" + System.currentTimeMillis() + ".jpg")
            cameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photo)
            camera = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri)
        } catch (_: Exception) {
            cameraUri = null
        }
        val chooser = Intent.createChooser(gallery, "Add a photo")
        if (camera != null && camera.resolveActivity(packageManager) != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
        }
        try {
            fileLauncher.launch(chooser)
        } catch (_: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    private fun runJs(js: String) {
        webView.post { webView.evaluateJavascript(js, null) }
    }

    private fun syncSteps() {
        val status = HealthConnectClient.getSdkStatus(this)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            runJs("window.onNativeMessage('Health Connect app is not available on this phone — install it from the Play Store')")
            return
        }
        val client = HealthConnectClient.getOrCreate(this)
        lifecycleScope.launch {
            try {
                val granted = client.permissionController.getGrantedPermissions()
                if (granted.containsAll(hcPermissions)) {
                    readStepsToday()
                } else {
                    hcPermLauncher.launch(hcPermissions)
                }
            } catch (e: Exception) {
                runJs("window.onNativeMessage('Health Connect error: could not check permissions')")
            }
        }
    }

    private fun readStepsToday() {
        val client = HealthConnectClient.getOrCreate(this)
        lifecycleScope.launch {
            try {
                val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
                val resp = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start, Instant.now())
                    )
                )
                val steps = resp[StepsRecord.COUNT_TOTAL] ?: 0L
                runJs("window.onStepsSynced($steps)")
            } catch (e: Exception) {
                runJs("window.onNativeMessage('Could not read steps from Health Connect')")
            }
        }
    }

    inner class Bridge {

        @JavascriptInterface
        fun startVoiceInput() {
            runOnUiThread {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say e.g. \"sugar 145\" or \"walked 30 minutes\"")
                try {
                    voiceLauncher.launch(intent)
                } catch (_: Exception) {
                    runJs("window.onNativeMessage('Voice recognition is not available on this phone')")
                }
            }
        }

        @JavascriptInterface
        fun requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33) {
                runOnUiThread { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }

        @JavascriptInterface
        fun scheduleReminder(id: Int, hour: Int, minute: Int, title: String, message: String) {
            Reminders.schedule(this@MainActivity, id, hour, minute, title, message)
        }

        @JavascriptInterface
        fun cancelReminder(id: Int) {
            Reminders.cancel(this@MainActivity, id)
        }

        @JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(send, "Share summary"))
            }
        }

        @JavascriptInterface
        fun syncHealthConnectSteps() {
            runOnUiThread { syncSteps() }
        }
    }
}
