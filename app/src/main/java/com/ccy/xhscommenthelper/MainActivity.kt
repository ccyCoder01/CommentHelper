package com.ccy.xhscommenthelper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.ccy.xhscommenthelper.data.RecentLeadStore
import com.ccy.xhscommenthelper.data.SettingsRepository
import com.ccy.xhscommenthelper.overlay.FloatingOverlayService
import com.ccy.xhscommenthelper.util.PermissionHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var recentLeadStore: RecentLeadStore

    private lateinit var overlayPermissionTextView: TextView
    private lateinit var accessibilityPermissionTextView: TextView
    private lateinit var fixedTextEditText: EditText
    private lateinit var recentCommentTextView: TextView
    private lateinit var recentMessageTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        settingsRepository = SettingsRepository(applicationContext)
        recentLeadStore = RecentLeadStore(applicationContext)
        bindViews()
        bindActions()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun bindViews() {
        overlayPermissionTextView = findViewById(R.id.overlayPermissionTextView)
        accessibilityPermissionTextView = findViewById(R.id.accessibilityPermissionTextView)
        fixedTextEditText = findViewById(R.id.fixedTextEditText)
        recentCommentTextView = findViewById(R.id.recentCommentTextView)
        recentMessageTextView = findViewById(R.id.recentMessageTextView)
    }

    private fun bindActions() {
        findViewById<Button>(R.id.saveFixedTextButton).setOnClickListener {
            lifecycleScope.launch {
                settingsRepository.saveFixedText(fixedTextEditText.text.toString())
                Toast.makeText(this@MainActivity, "话术已保存", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.startOverlayButton).setOnClickListener {
            if (!PermissionHelper.canDrawOverlays(this)) {
                Toast.makeText(this, "请先开启悬浮窗权限，否则无法显示工具面板。", Toast.LENGTH_SHORT).show()
                openOverlaySettings()
                return@setOnClickListener
            }
            startService(Intent(this, FloatingOverlayService::class.java))
        }

        findViewById<Button>(R.id.stopOverlayButton).setOnClickListener {
            stopService(Intent(this, FloatingOverlayService::class.java))
        }

        findViewById<Button>(R.id.openAccessibilitySettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.openOverlaySettingsButton).setOnClickListener {
            openOverlaySettings()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            fixedTextEditText.setText(settingsRepository.settingsFlow.first().fixedText)
        }

        lifecycleScope.launch {
            recentLeadStore.recentLeadFlow.collect { recent ->
                recentCommentTextView.text = "最近评论：${recent.comment.ifBlank { "暂无" }}"
                recentMessageTextView.text = "最近私信内容：${recent.message.ifBlank { "暂无" }}"
            }
        }
    }

    private fun updatePermissionStatus() {
        val overlayStatus = if (PermissionHelper.canDrawOverlays(this)) "已开启" else "未开启"
        val accessibilityStatus = if (PermissionHelper.isAccessibilityServiceEnabled(this)) "已开启" else "未开启"
        overlayPermissionTextView.text = "悬浮窗权限：$overlayStatus"
        accessibilityPermissionTextView.text = "辅助功能权限：$accessibilityStatus"
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
