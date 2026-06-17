package com.ccy.xhscommenthelper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.ccy.xhscommenthelper.data.SettingsRepository
import com.ccy.xhscommenthelper.overlay.FloatingOverlayService
import com.ccy.xhscommenthelper.util.PermissionHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var overlayPermissionDialogShown = false
    private var overlayServiceStarted = false

    private val ipLocationOptions = listOf(
        "北京",
        "天津",
        "河北",
        "山西",
        "内蒙古",
        "辽宁",
        "吉林",
        "黑龙江",
        "上海",
        "江苏",
        "浙江",
        "安徽",
        "福建",
        "江西",
        "山东",
        "河南",
        "湖北",
        "湖南",
        "广东",
        "广西",
        "海南",
        "重庆",
        "四川",
        "贵州",
        "云南",
        "西藏",
        "陕西",
        "甘肃",
        "青海",
        "宁夏",
        "新疆",
        "香港",
        "澳门",
        "台湾"
    )

    private lateinit var settingsRepository: SettingsRepository

    private lateinit var accessibilityPermissionTextView: TextView
    private lateinit var fixedTextEditText: EditText
    private lateinit var targetGenderRadioGroup: RadioGroup
    private lateinit var targetMaleRadioButton: RadioButton
    private lateinit var targetFemaleRadioButton: RadioButton
    private lateinit var targetIpLocationSpinner: Spinner

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
        bindViews()
        bindActions()
        observeState()
        ensureOverlayPermissionAndService()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityPermissionStatus()
        ensureOverlayPermissionAndService()
    }

    private fun bindViews() {
        accessibilityPermissionTextView = findViewById(R.id.accessibilityPermissionTextView)
        fixedTextEditText = findViewById(R.id.fixedTextEditText)
        targetGenderRadioGroup = findViewById(R.id.targetGenderRadioGroup)
        targetMaleRadioButton = findViewById(R.id.targetMaleRadioButton)
        targetFemaleRadioButton = findViewById(R.id.targetFemaleRadioButton)
        targetIpLocationSpinner = findViewById(R.id.targetIpLocationSpinner)
        val ipLocationAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ipLocationOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        targetIpLocationSpinner.adapter = ipLocationAdapter
    }

    private fun bindActions() {
        findViewById<Button>(R.id.saveFixedTextButton).setOnClickListener {
            lifecycleScope.launch {
                settingsRepository.saveFixedText(fixedTextEditText.text.toString())
                Toast.makeText(this@MainActivity, "话术已保存", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.saveProfileCriteriaButton).setOnClickListener {
            lifecycleScope.launch {
                saveProfileCriteria()
                Toast.makeText(this@MainActivity, "画像已保存", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.openAccessibilitySettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            fixedTextEditText.setText(settings.fixedText)
            applyGenderSelection(settings.targetGender)
            applyIpLocationSelection(settings.targetIpLocation)
        }
    }

    private fun selectedGender(): String {
        return when (targetGenderRadioGroup.checkedRadioButtonId) {
            R.id.targetMaleRadioButton -> "男"
            R.id.targetFemaleRadioButton -> "女"
            else -> ""
        }
    }

    private fun selectedIpLocation(): String {
        return targetIpLocationSpinner.selectedItem?.toString().orEmpty()
    }

    private fun applyGenderSelection(gender: String) {
        when (gender) {
            "男" -> targetMaleRadioButton.isChecked = true
            "女" -> targetFemaleRadioButton.isChecked = true
            else -> targetGenderRadioGroup.clearCheck()
        }
    }

    private fun applyIpLocationSelection(ipLocation: String) {
        val index = ipLocationOptions.indexOf(ipLocation).takeIf { it >= 0 } ?: 0
        targetIpLocationSpinner.setSelection(index)
    }

    private suspend fun saveProfileCriteria() {
        settingsRepository.saveProfileCriteria(
            gender = selectedGender(),
            ipLocation = selectedIpLocation()
        )
    }

    private fun updateAccessibilityPermissionStatus() {
        val accessibilityStatus = if (PermissionHelper.isAccessibilityServiceEnabled(this)) "已开启" else "未开启"
        accessibilityPermissionTextView.text = "辅助功能权限：$accessibilityStatus"
    }

    private fun ensureOverlayPermissionAndService() {
        if (PermissionHelper.canDrawOverlays(this)) {
            if (!overlayServiceStarted) {
                startService(Intent(this, FloatingOverlayService::class.java))
                overlayServiceStarted = true
            }
            return
        }

        if (overlayPermissionDialogShown) return
        overlayPermissionDialogShown = true
        AlertDialog.Builder(this)
            .setTitle("开启悬浮窗权限")
            .setMessage("匹配助手需要悬浮窗权限，授权后会自动显示悬浮球。")
            .setPositiveButton("去开启") { _, _ -> openOverlaySettings() }
            .setNegativeButton("稍后再说", null)
            .show()
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
