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

    @Volatile
    private var lastMediaUrlFromNetwork: String? = null

    private lateinit var discovery: TvDiscovery
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

        val tvHost = intent.getStringExtra(EXTRA_TV_HOST)
        val tvPort = intent.getIntExtra(EXTRA_TV_PORT, -1)
        val tvName = intent.getStringExtra(EXTRA_TV_NAME).orEmpty()
        if (!tvHost.isNullOrBlank() && tvPort > 0) {
            targetTv = TvDevice(
                name = if (tvName.isBlank()) "TV" else tvName,
                host = tvHost,
                port = tvPort
            )
            webStatusText.text = "状态：已连接电视 ${targetTv?.name}，打开网页后可发送"
        } else {
            webStatusText.text = "状态：未绑定电视，点击“扫描电视”后可直接投屏"
        }
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

        floatingCastButton.setOnClickListener {
            sendCurrentWebVideoToTv()
        }

        uiHandler.post(videoDetectTicker)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacks(videoDetectTicker)
        discovery.stop()
        webView.destroy()
    }

    private fun bindHistoryInput() {
        val history = UrlHistoryStore.load(this)
        webUrlInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, history)
        )
    }

    private fun setupDiscovery() {
        discovery = TvDiscovery(this) { device ->
            val key = "${device.host}:${device.port}"
            if (!foundDevices.containsKey(key)) {
                foundDevices[key] = device
            }
        }
    }

    private fun startDiscovery(manual: Boolean) {
        foundDevices.clear()
        discovery.stop()
        discovery.start()
        webStatusText.text = "状态：正在扫描电视设备..."
        uiHandler.postDelayed({
            val list = foundDevices.values.toList()
            if (list.isEmpty()) {
                pendingSendAfterBind = false
                webStatusText.text = "状态：未发现电视，请确认同一局域网"
            } else {
                showDevicePicker(list)
            }
        }, 1800)

        if (manual) {
            Log.i(TAG, "manual tv discovery started")
        }
    }

    private fun showDevicePicker(list: List<TvDevice>) {
        val labels = list.map { "${it.name} (${it.host}:${it.port})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择电视设备")
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
            "当前电视：未绑定"
        } else {
            "当前电视：${tv.name}"
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
                    webStatusText.text = "状态：网页已加载，播放视频后可发送到电视"
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
            val videoCount = json?.optInt("videoCount") ?: -1
            val playingCount = json?.optInt("playingCount") ?: -1
            val fallbackReady = !lastMediaUrlFromNetwork.isNullOrBlank()

            val show = hasPlaying || fallbackReady
            floatingCastButton.visibility = if (show) View.VISIBLE else View.GONE

            if (lastHasPlayingState != show) {
                lastHasPlayingState = show
                Log.i(
                    TAG,
                    "detect stateChanged show=$show hasPlaying=$hasPlaying fallbackReady=$fallbackReady videoCount=$videoCount playingCount=$playingCount url=${webView.url}"
                )
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
            if (result == null) {
                val fallback = lastMediaUrlFromNetwork
                if (!fallback.isNullOrBlank()) {
                    Log.i(TAG, "send parse failed, use fallback=$fallback")
                    sendUrlToTv(tv, fallback)
                } else {
                    webStatusText.text = "状态：脚本返回异常，请查看日志"
                    Log.e(TAG, "send parse failed raw=$jsValue")
                }
                return@evaluateJavascript
            }

            val found = result.optBoolean("found", false)
            val videoUrl = result.optString("url", "").trim()
            val reason = result.optString("reason", "")
            val videoCount = result.optInt("videoCount", -1)
            val playingCount = result.optInt("playingCount", -1)

            Log.i(
                TAG,
                "send probe found=$found videoCount=$videoCount playingCount=$playingCount reason=$reason page=${webView.url} payload=$result"
            )

            val finalUrl = when {
                found && videoUrl.isNotBlank() -> videoUrl
                !lastMediaUrlFromNetwork.isNullOrBlank() -> lastMediaUrlFromNetwork!!
                else -> ""
            }

            if (finalUrl.isBlank()) {
                webStatusText.text = "状态：未找到可发送的视频($reason)，请看日志"
                return@evaluateJavascript
            }

            if (!found) {
                Log.i(TAG, "send use fallback media url=$finalUrl")
            }
            sendUrlToTv(tv, finalUrl)
        }
    }

    private fun sendUrlToTv(tv: TvDevice, videoUrl: String) {
        webStatusText.text = "状态：已获取视频地址，发送中..."
        Log.i(TAG, "send cast url=$videoUrl tv=${tv.host}:${tv.port}")
        thread {
            val (ok, msg) = CastClient.sendCast(tv, videoUrl)
            runOnUiThread {
                webStatusText.text = if (ok) {
                    "状态：发送成功"
                } else {
                    "状态：发送失败 ($msg)"
                }
            }
            Log.i(TAG, "send result ok=$ok msg=$msg")
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
        const val EXTRA_TV_NAME = "extra_tv_name"
        const val EXTRA_TV_HOST = "extra_tv_host"
        const val EXTRA_TV_PORT = "extra_tv_port"

        private const val JS_FIND_VIDEO_URL = """
            (function() {
                var collectVideos = function(doc) {
                    try {
                        return Array.prototype.slice.call(doc.querySelectorAll('video'));
                    } catch (e) {
                        return [];
                    }
                };

                var videos = collectVideos(document);
                var iframes = Array.prototype.slice.call(document.querySelectorAll('iframe'));
                var iframeVideos = [];
                iframes.forEach(function(f) {
                    try {
                        if (f.contentDocument) {
                            iframeVideos = iframeVideos.concat(collectVideos(f.contentDocument));
                        }
                    } catch (e) {
                    }
                });
                videos = videos.concat(iframeVideos);

                var details = videos.slice(0, 5).map(function(v, idx) {
                    var sourceNode = v.querySelector('source');
                    return {
                        index: idx,
                        paused: !!v.paused,
                        ended: !!v.ended,
                        readyState: v.readyState || 0,
                        currentSrc: v.currentSrc || '',
                        src: v.src || '',
                        sourceSrc: sourceNode ? (sourceNode.src || '') : ''
                    };
                });

                if (!videos || videos.length === 0) {
                    return JSON.stringify({
                        found: false,
                        url: '',
                        reason: 'no_video_element',
                        videoCount: 0,
                        playingCount: 0,
                        details: details
                    });
                }

                var pickUrl = function(v) {
                    if (!v) return '';
                    var sourceNode = v.querySelector('source');
                    return v.currentSrc || v.src || (sourceNode ? (sourceNode.src || '') : '') || '';
                };

                var playingVideos = videos.filter(function(v){
                    return !v.paused && !v.ended && !!pickUrl(v);
                });
                var playableVideos = videos.filter(function(v){
                    return !!pickUrl(v);
                });

                var target = playingVideos[0] || playableVideos[0] || null;
                var targetUrl = pickUrl(target);

                return JSON.stringify({
                    found: !!targetUrl,
                    url: targetUrl,
                    reason: targetUrl ? 'ok' : 'url_empty',
                    videoCount: videos.length,
                    playingCount: playingVideos.length,
                    details: details
                });
            })();
        """

        private const val JS_HAS_PLAYING_VIDEO = """
            (function() {
                var collectVideos = function(doc) {
                    try {
                        return Array.prototype.slice.call(doc.querySelectorAll('video'));
                    } catch (e) {
                        return [];
                    }
                };
                var videos = collectVideos(document);
                var iframes = Array.prototype.slice.call(document.querySelectorAll('iframe'));
                iframes.forEach(function(f) {
                    try {
                        if (f.contentDocument) {
                            videos = videos.concat(collectVideos(f.contentDocument));
                        }
                    } catch (e) {
                    }
                });

                var pickUrl = function(v) {
                    if (!v) return '';
                    var sourceNode = v.querySelector('source');
                    return v.currentSrc || v.src || (sourceNode ? (sourceNode.src || '') : '') || '';
                };
                var playingCount = videos.filter(function(v){
                    return !v.paused && !v.ended && !!pickUrl(v);
                }).length;
                return JSON.stringify({
                    hasPlaying: playingCount > 0,
                    videoCount: videos.length,
                    playingCount: playingCount
                });
            })();
        """
    }
}
