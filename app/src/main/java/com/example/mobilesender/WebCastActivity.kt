package com.example.mobilesender

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.mobilesender.discovery.DeviceDiscoveryManager
import com.example.mobilesender.protocol.ProtocolType
import com.example.mobilesender.sender.PlaybackAction
import com.example.mobilesender.sender.UnifiedPlaybackController
import com.example.mobilesender.sender.UnifiedVideoSender
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class WebCastActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var webUrlInput: AutoCompleteTextView
    private lateinit var webStatusText: TextView
    private lateinit var floatingCastButton: Button
    private lateinit var currentTvText: TextView

    private var targetTv: TvDevice? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var lastHasPlayingState: Boolean? = null
    private val unifiedSender = UnifiedVideoSender()
    private val unifiedPlaybackController = UnifiedPlaybackController()

    @Volatile
    private var lastMediaUrlFromNetwork: String? = null

    private lateinit var discoveryManager: DeviceDiscoveryManager
    private val foundDevices = linkedMapOf<String, TvDevice>()
    private var pendingSendAfterBind = false

    private val videoDetectTicker = object : Runnable {
        override fun run() {
            detectVideoAndToggleFloatingButton()
            uiHandler.postDelayed(this, 1200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_cast)
        Log.i(TAG, "WebCastActivity created, logger-ready")

        webView = findViewById(R.id.webView)
        webUrlInput = findViewById(R.id.webUrlInput)
        webStatusText = findViewById(R.id.webStatusText)
        floatingCastButton = findViewById(R.id.floatingCastButton)
        currentTvText = findViewById(R.id.currentTvText)

        bindHistoryInput()
        setupDiscovery()

        restoreDeviceFromIntent()
        updateCurrentTvText()

        setupWebView()

        val initialUrl = normalizeUrl(intent.getStringExtra(EXTRA_INITIAL_URL).orEmpty())
        if (initialUrl.isNotBlank()) {
            webUrlInput.setText(initialUrl)
            UrlHistoryStore.save(this, initialUrl)
            webView.loadUrl(initialUrl)
        }

        findViewById<Button>(R.id.openWebButton).setOnClickListener {
            val raw = webUrlInput.text.toString().trim()
            if (raw.isBlank()) {
                webStatusText.text = "状态：请输入网页地址"
                return@setOnClickListener
            }
            val url = normalizeUrl(raw)
            webUrlInput.setText(url)
            UrlHistoryStore.save(this, url)
            bindHistoryInput()
            webView.loadUrl(url)
        }

        findViewById<Button>(R.id.scanTvButton).setOnClickListener {
            startDiscovery(manual = true)
        }

        findViewById<Button>(R.id.sendWebVideoButton).setOnClickListener {
            sendCurrentWebVideoToTv()
        }
        findViewById<Button>(R.id.webPlayControlButton).setOnClickListener {
            controlPlayback(PlaybackAction.PLAY)
        }
        findViewById<Button>(R.id.webPauseControlButton).setOnClickListener {
            controlPlayback(PlaybackAction.PAUSE)
        }
        findViewById<Button>(R.id.webStopControlButton).setOnClickListener {
            controlPlayback(PlaybackAction.STOP)
        }

        floatingCastButton.setOnClickListener {
            sendCurrentWebVideoToTv()
        }

        uiHandler.post(videoDetectTicker)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacks(videoDetectTicker)
        discoveryManager.stop()
        webView.destroy()
    }

    private fun bindHistoryInput() {
        val history = UrlHistoryStore.load(this)
        webUrlInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, history)
        )
    }

    private fun setupDiscovery() {
        discoveryManager = DeviceDiscoveryManager(this) { device ->
            if (!foundDevices.containsKey(device.id)) {
                foundDevices[device.id] = device
            }
        }
    }

    private fun restoreDeviceFromIntent() {
        val host = intent.getStringExtra(EXTRA_TV_HOST)
        val port = intent.getIntExtra(EXTRA_TV_PORT, -1)
        if (host.isNullOrBlank() || port <= 0) {
            webStatusText.text = "状态：未绑定设备，点击“扫描电视”后可直接投屏"
            return
        }
        val protocol = runCatching {
            ProtocolType.valueOf(intent.getStringExtra(EXTRA_TV_PROTOCOL).orEmpty())
        }.getOrElse { ProtocolType.CUSTOM }

        targetTv = TvDevice(
            id = intent.getStringExtra(EXTRA_TV_ID) ?: "${protocol.name.lowercase()}-$host:$port",
            name = intent.getStringExtra(EXTRA_TV_NAME) ?: "TV",
            host = host,
            port = port,
            protocol = protocol,
            locationUrl = intent.getStringExtra(EXTRA_TV_LOCATION),
            avTransportControlUrl = intent.getStringExtra(EXTRA_TV_CONTROL_URL)
        )
        webStatusText.text = "状态：已连接设备 ${targetTv?.name}，打开网页后可发送"
    }

    private fun startDiscovery(manual: Boolean) {
        foundDevices.clear()
        discoveryManager.stop()
        discoveryManager.start()
        webStatusText.text = "状态：正在扫描设备（自定义 + DLNA）..."
        uiHandler.postDelayed({
            val list = foundDevices.values.toList()
            if (list.isEmpty()) {
                pendingSendAfterBind = false
                webStatusText.text = "状态：未发现设备，请确认同一局域网"
            } else {
                showDevicePicker(list)
            }
        }, 2200)

        if (manual) {
            Log.i(TAG, "manual discovery started")
        }
    }

    private fun showDevicePicker(list: List<TvDevice>) {
        val labels = list.map { "[${it.protocol.displayName}] ${it.name} (${it.host}:${it.port})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择投屏设备")
            .setItems(labels) { _, which ->
                targetTv = list[which]
                updateCurrentTvText()
                webStatusText.text = "状态：已绑定 ${list[which].name}"
                if (pendingSendAfterBind) {
                    pendingSendAfterBind = false
                    sendCurrentWebVideoToTv()
                }
            }
            .setNegativeButton("取消") { _, _ -> pendingSendAfterBind = false }
            .show()
    }

    private fun updateCurrentTvText() {
        val tv = targetTv
        currentTvText.text = if (tv == null) {
            "当前设备：未绑定"
        } else {
            "当前设备：[${tv.protocol.displayName}] ${tv.name}"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.i(
                    TAG,
                    "WebConsole ${consoleMessage.messageLevel()} ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}"
                )
                return super.onConsoleMessage(consoleMessage)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                request?.url?.toString()?.let { url ->
                    recordMediaCandidate(url, "intercept")
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                webStatusText.text = "状态：网页加载中..."
                floatingCastButton.visibility = View.GONE
                lastHasPlayingState = null
                lastMediaUrlFromNetwork = null
                Log.i(TAG, "onPageStarted url=$url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (targetTv != null) {
                    webStatusText.text = "状态：网页已加载，播放视频后可发送"
                }
                Log.i(TAG, "onPageFinished url=$url")
            }
        }
    }

    private fun recordMediaCandidate(url: String, source: String) {
        if (!isLikelyMediaUrl(url)) return
        if (url.startsWith("blob:", ignoreCase = true)) return

        lastMediaUrlFromNetwork = url
        Log.i(TAG, "media candidate[$source] $url")
    }

    private fun isLikelyMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") ||
            lower.contains(".mp4") ||
            lower.contains(".flv") ||
            lower.contains(".mpd") ||
            lower.contains(".m4s") ||
            lower.contains("mime=video") ||
            lower.contains("type=video") ||
            lower.contains("video/")
    }

    private fun detectVideoAndToggleFloatingButton() {
        if (targetTv == null) {
            floatingCastButton.visibility = View.GONE
            return
        }

        webView.evaluateJavascript(JS_HAS_PLAYING_VIDEO) { jsValue ->
            val json = decodeJsJson(jsValue)
            val hasPlaying = json?.optBoolean("hasPlaying") == true
            val fallbackReady = !lastMediaUrlFromNetwork.isNullOrBlank()
            val show = hasPlaying || fallbackReady
            floatingCastButton.visibility = if (show) View.VISIBLE else View.GONE

            if (lastHasPlayingState != show) {
                lastHasPlayingState = show
                Log.i(TAG, "detect stateChanged show=$show url=${webView.url}")
            }
        }
    }

    private fun sendCurrentWebVideoToTv() {
        val tv = targetTv
        if (tv == null) {
            pendingSendAfterBind = true
            startDiscovery(manual = false)
            return
        }

        webStatusText.text = "状态：正在获取网页视频地址..."
        webView.evaluateJavascript(JS_FIND_VIDEO_URL) { jsValue ->
            val result = decodeJsJson(jsValue)
            val found = result?.optBoolean("found", false) == true
            val videoUrl = result?.optString("url", "").orEmpty().trim()
            val reason = result?.optString("reason", "unknown").orEmpty()

            val finalUrl = when {
                found && videoUrl.isNotBlank() -> videoUrl
                !lastMediaUrlFromNetwork.isNullOrBlank() -> lastMediaUrlFromNetwork!!
                else -> ""
            }

            if (finalUrl.isBlank()) {
                webStatusText.text = "状态：未找到可发送的视频($reason)"
                return@evaluateJavascript
            }

            sendUrlToTv(tv, finalUrl)
        }
    }

    private fun sendUrlToTv(tv: TvDevice, videoUrl: String) {
        webStatusText.text = "状态：发送中..."
        thread {
            val (ok, msg) = unifiedSender.send(tv, videoUrl)
            runOnUiThread {
                webStatusText.text = if (ok) "状态：发送成功" else "状态：发送失败 ($msg)"
            }
            Log.i(TAG, "send result ok=$ok msg=$msg")
        }
    }

    private fun controlPlayback(action: PlaybackAction) {
        val tv = targetTv
        if (tv == null) {
            webStatusText.text = "状态：请先绑定设备"
            return
        }
        webStatusText.text = "状态：控制中..."
        thread {
            val (ok, msg) = unifiedPlaybackController.control(tv, action)
            runOnUiThread {
                webStatusText.text = if (ok) {
                    "状态：控制成功 (${action.name})"
                } else {
                    val suffix = if (tv.protocol == ProtocolType.DLNA) "（该电视可能不支持此控制）" else ""
                    "状态：控制失败 ($msg)$suffix"
                }
            }
        }
    }

    private fun normalizeUrl(raw: String): String {
        if (raw.isBlank()) return ""
        return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }

    private fun decodeJsString(value: String): String {
        return runCatching { JSONArray("[$value]").getString(0) }.getOrDefault("")
    }

    private fun decodeJsJson(value: String): JSONObject? {
        val decoded = decodeJsString(value)
        if (decoded.isBlank()) return null
        return runCatching { JSONObject(decoded) }.getOrNull()
    }

    companion object {
        private const val TAG = "WebCast"

        const val EXTRA_INITIAL_URL = "extra_initial_url"
        const val EXTRA_TV_ID = "extra_tv_id"
        const val EXTRA_TV_NAME = "extra_tv_name"
        const val EXTRA_TV_HOST = "extra_tv_host"
        const val EXTRA_TV_PORT = "extra_tv_port"
        const val EXTRA_TV_PROTOCOL = "extra_tv_protocol"
        const val EXTRA_TV_LOCATION = "extra_tv_location"
        const val EXTRA_TV_CONTROL_URL = "extra_tv_control_url"

        private const val JS_FIND_VIDEO_URL = """
            (function() {
                var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
                if (!videos || videos.length === 0) {
                    return JSON.stringify({ found: false, url: '', reason: 'no_video_element' });
                }
                var pick = function(v) {
                    var sourceNode = v.querySelector('source');
                    return v.currentSrc || v.src || (sourceNode ? (sourceNode.src || '') : '') || '';
                };
                var playing = videos.find(function(v){ return !v.paused && !v.ended && !!pick(v); });
                var target = playing || videos.find(function(v){ return !!pick(v); });
                var url = target ? pick(target) : '';
                return JSON.stringify({ found: !!url, url: url, reason: url ? 'ok' : 'url_empty' });
            })();
        """

        private const val JS_HAS_PLAYING_VIDEO = """
            (function() {
                var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
                return JSON.stringify({ hasPlaying: videos.some(function(v){ return !v.paused && !v.ended; }) });
            })();
        """
    }
}
