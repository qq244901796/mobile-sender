package com.example.mobilesender

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mobilesender.discovery.DeviceDiscoveryManager
import com.example.mobilesender.protocol.ProtocolType
import com.example.mobilesender.sender.PlaybackAction
import com.example.mobilesender.sender.UnifiedPlaybackController
import com.example.mobilesender.sender.UnifiedVideoSender
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var selectedDeviceText: TextView
    private lateinit var resultText: TextView
    private lateinit var controlHintText: TextView
    private lateinit var linkInput: EditText
    private lateinit var h5Input: AutoCompleteTextView

    private lateinit var discoveryManager: DeviceDiscoveryManager
    private lateinit var adapter: DeviceAdapter
    private val unifiedSender = UnifiedVideoSender()
    private val unifiedPlaybackController = UnifiedPlaybackController()

    private val devices = linkedMapOf<String, TvDevice>()
    private var selectedDevice: TvDevice? = null
    private var fileShareServer: FileShareServer? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        sendLocalFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectedDeviceText = findViewById(R.id.selectedDeviceText)
        resultText = findViewById(R.id.resultText)
        controlHintText = findViewById(R.id.controlHintText)
        linkInput = findViewById(R.id.linkInput)
        h5Input = findViewById(R.id.h5Input)

        bindHistoryInput()
        updateControlHint(null)

        val recycler = findViewById<RecyclerView>(R.id.deviceRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter { device ->
            selectedDevice = device
            selectedDeviceText.text = "已选设备: [${device.protocol.displayName}] ${device.name}"
            updateControlHint(device)
        }
        recycler.adapter = adapter

        discoveryManager = DeviceDiscoveryManager(this) { device ->
            if (!devices.containsKey(device.id)) {
                devices[device.id] = device
                runOnUiThread { adapter.submit(devices.values.toList()) }
            }
        }

        findViewById<Button>(R.id.discoverButton).setOnClickListener {
            devices.clear()
            adapter.submit(emptyList())
            resultText.text = "状态：正在扫描设备（自定义 + DLNA）..."
            discoveryManager.start()
        }

        findViewById<Button>(R.id.sendLinkButton).setOnClickListener {
            val url = linkInput.text.toString().trim()
            if (url.isBlank()) {
                resultText.text = "状态：请先输入视频链接"
                return@setOnClickListener
            }
            sendToTv(url)
        }

        findViewById<Button>(R.id.selectFileButton).setOnClickListener {
            filePicker.launch(arrayOf("video/*"))
        }

        findViewById<Button>(R.id.playControlButton).setOnClickListener {
            controlPlayback(PlaybackAction.PLAY)
        }
        findViewById<Button>(R.id.pauseControlButton).setOnClickListener {
            controlPlayback(PlaybackAction.PAUSE)
        }
        findViewById<Button>(R.id.stopControlButton).setOnClickListener {
            controlPlayback(PlaybackAction.STOP)
        }

        findViewById<Button>(R.id.openH5Button).setOnClickListener {
            openH5Page()
        }
    }

    override fun onResume() {
        super.onResume()
        bindHistoryInput()
    }

    private fun bindHistoryInput() {
        val history = UrlHistoryStore.load(this)
        h5Input.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, history)
        )
    }

    private fun updateControlHint(device: TvDevice?) {
        controlHintText.text = when (device?.protocol) {
            ProtocolType.CUSTOM -> "控制能力：完整控制（播放/暂停/退出播放）"
            ProtocolType.DLNA -> "控制能力：基础控制（播放/暂停/停止，兼容性依电视）"
            null -> "控制能力：未选择设备"
        }
    }

    private fun openH5Page() {
        val input = h5Input.text.toString().trim()
        val defaultUrl = "https://www.bilibili.com"
        val pageUrl = normalizeUrl(if (input.isBlank()) defaultUrl else input)
        UrlHistoryStore.save(this, pageUrl)

        val intent = Intent(this, WebCastActivity::class.java).apply {
            putExtra(WebCastActivity.EXTRA_INITIAL_URL, pageUrl)
            selectedDevice?.let { tv ->
                putExtra(WebCastActivity.EXTRA_TV_ID, tv.id)
                putExtra(WebCastActivity.EXTRA_TV_NAME, tv.name)
                putExtra(WebCastActivity.EXTRA_TV_HOST, tv.host)
                putExtra(WebCastActivity.EXTRA_TV_PORT, tv.port)
                putExtra(WebCastActivity.EXTRA_TV_PROTOCOL, tv.protocol.name)
                putExtra(WebCastActivity.EXTRA_TV_LOCATION, tv.locationUrl)
                putExtra(WebCastActivity.EXTRA_TV_CONTROL_URL, tv.avTransportControlUrl)
            }
        }
        startActivity(intent)

        if (selectedDevice == null) {
            resultText.text = "状态：已打开H5，网页内可直接扫描并绑定设备"
        }
    }

    private fun sendLocalFile(uri: Uri) {
        val tv = selectedDevice
        if (tv == null) {
            resultText.text = "状态：请先选择设备"
            return
        }

        val localIp = NetworkUtils.localIpv4(this)
        if (localIp.isNullOrBlank()) {
            resultText.text = "状态：无法获取手机局域网 IP"
            return
        }

        if (fileShareServer == null) {
            fileShareServer = FileShareServer(this, uri).also { it.start() }
        } else {
            fileShareServer?.updateUri(uri)
        }

        val url = "http://$localIp:${FileShareServer.PORT}/video"
        sendToTv(url)
    }

    private fun sendToTv(videoUrl: String) {
        val tv = selectedDevice
        if (tv == null) {
            resultText.text = "状态：请先选择设备"
            return
        }

        resultText.text = "状态：发送中..."
        thread {
            val (ok, msg) = unifiedSender.send(tv, videoUrl)
            runOnUiThread {
                resultText.text = if (ok) {
                    "状态：发送成功 ($msg)"
                } else {
                    "状态：发送失败 ($msg)"
                }
            }
        }
    }

    private fun controlPlayback(action: PlaybackAction) {
        val tv = selectedDevice
        if (tv == null) {
            resultText.text = "状态：请先选择设备"
            return
        }

        resultText.text = "状态：控制中..."
        thread {
            val (ok, msg) = unifiedPlaybackController.control(tv, action)
            runOnUiThread {
                resultText.text = if (ok) {
                    "状态：控制成功 (${action.name})"
                } else {
                    val suffix = if (tv.protocol == ProtocolType.DLNA) "（该电视可能不支持此控制）" else ""
                    "状态：控制失败 ($msg)$suffix"
                }
            }
        }
    }

    private fun normalizeUrl(raw: String): String {
        return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }

    override fun onDestroy() {
        super.onDestroy()
        discoveryManager.stop()
        fileShareServer?.stop()
    }
}
