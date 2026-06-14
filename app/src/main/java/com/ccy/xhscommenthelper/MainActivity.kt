package com.ccy.xhscommenthelper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
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
import com.ccy.xhscommenthelper.util.ClipboardHelper
import com.ccy.xhscommenthelper.util.PermissionHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

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
    private lateinit var clipboardHelper: ClipboardHelper

    private lateinit var accessibilityPermissionTextView: TextView
    private lateinit var fixedTextEditText: EditText
    private lateinit var targetGenderRadioGroup: RadioGroup
    private lateinit var targetMaleRadioButton: RadioButton
    private lateinit var targetFemaleRadioButton: RadioButton
    private lateinit var targetIpLocationSpinner: Spinner
    private lateinit var commentWhitelistStatusTextView: TextView
    private lateinit var commentWhitelistSpinner: Spinner
    private lateinit var commentWhitelistEditText: EditText
    private lateinit var commentWhitelistAdapter: ArrayAdapter<String>
    private val commentWhitelist = mutableListOf<String>()

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
        clipboardHelper = ClipboardHelper(applicationContext)
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
        commentWhitelistStatusTextView = findViewById(R.id.commentWhitelistStatusTextView)
        commentWhitelistSpinner = findViewById(R.id.commentWhitelistSpinner)
        commentWhitelistEditText = findViewById(R.id.commentWhitelistEditText)
        val ipLocationAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ipLocationOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        targetIpLocationSpinner.adapter = ipLocationAdapter
        commentWhitelistAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            commentWhitelist
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        commentWhitelistSpinner.adapter = commentWhitelistAdapter
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

        findViewById<Button>(R.id.exportProfileCriteriaButton).setOnClickListener {
            exportProfileCriteriaToClipboard()
        }

        findViewById<Button>(R.id.importProfileCriteriaButton).setOnClickListener {
            importProfileCriteriaFromClipboard()
        }

        commentWhitelistSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                commentWhitelistEditText.setText(commentWhitelist.getOrNull(position).orEmpty())
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        findViewById<Button>(R.id.addCommentWhitelistButton).setOnClickListener {
            val keyword = commentWhitelistEditText.text.toString().trim()
            if (keyword.isBlank()) {
                Toast.makeText(this, "请输入白名单关键词", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (commentWhitelist.contains(keyword)) {
                Toast.makeText(this, "关键词已存在", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            commentWhitelist.add(keyword)
            persistCommentWhitelist("白名单已新增")
        }

        findViewById<Button>(R.id.updateCommentWhitelistButton).setOnClickListener {
            val position = commentWhitelistSpinner.selectedItemPosition
            val keyword = commentWhitelistEditText.text.toString().trim()
            if (position !in commentWhitelist.indices) {
                Toast.makeText(this, "请选择要修改的关键词", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (keyword.isBlank()) {
                Toast.makeText(this, "请输入白名单关键词", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val duplicateIndex = commentWhitelist.indexOf(keyword)
            if (duplicateIndex >= 0 && duplicateIndex != position) {
                Toast.makeText(this, "关键词已存在", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            commentWhitelist[position] = keyword
            persistCommentWhitelist("白名单已修改")
        }

        findViewById<Button>(R.id.deleteCommentWhitelistButton).setOnClickListener {
            val position = commentWhitelistSpinner.selectedItemPosition
            if (position !in commentWhitelist.indices) {
                Toast.makeText(this, "请选择要删除的关键词", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            commentWhitelist.removeAt(position)
            persistCommentWhitelist("白名单已删除")
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
            applyCommentWhitelist(settings.commentWhitelist)
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

    private fun applyCommentWhitelist(keywords: List<String>) {
        commentWhitelist.clear()
        commentWhitelist.addAll(keywords)
        refreshCommentWhitelistView()
    }

    private fun persistCommentWhitelist(toastText: String) {
        lifecycleScope.launch {
            settingsRepository.saveCommentWhitelist(commentWhitelist)
            refreshCommentWhitelistView()
            Toast.makeText(this@MainActivity, toastText, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshCommentWhitelistView() {
        commentWhitelistAdapter.notifyDataSetChanged()
        commentWhitelistStatusTextView.text = "白名单：${commentWhitelist.size} 条"
        if (commentWhitelist.isEmpty()) {
            commentWhitelistEditText.setText("")
        } else {
            val position = commentWhitelistSpinner.selectedItemPosition
                .takeIf { it in commentWhitelist.indices }
                ?: 0
            commentWhitelistSpinner.setSelection(position)
            commentWhitelistEditText.setText(commentWhitelist[position])
        }
    }

    private suspend fun saveProfileCriteria() {
        settingsRepository.saveProfileCriteria(
            gender = selectedGender(),
            ipLocation = selectedIpLocation()
        )
        settingsRepository.saveCommentWhitelist(commentWhitelist)
    }

    private fun exportProfileCriteriaToClipboard() {
        val json = JSONObject()
            .put("version", 1)
            .put("gender", selectedGender())
            .put("ipLocation", selectedIpLocation())
            .put("whitelist", JSONArray(commentWhitelist))
            .toString(2)
        clipboardHelper.copyText(json)
        Toast.makeText(this, "画像 JSON 已复制", Toast.LENGTH_SHORT).show()
    }

    private fun importProfileCriteriaFromClipboard() {
        val text = clipboardHelper.getText()?.trim()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "剪切板为空", Toast.LENGTH_SHORT).show()
            return
        }

        val profile = runCatching { parseProfileCriteriaJson(text) }
            .getOrElse { error ->
                val message = if (error is JSONException) "剪切板 JSON 格式不正确" else "画像内容不合法"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                return
            }

        applyGenderSelection(profile.gender)
        applyIpLocationSelection(profile.ipLocation)
        applyCommentWhitelist(profile.whitelist)
        lifecycleScope.launch {
            saveProfileCriteria()
            Toast.makeText(this@MainActivity, "画像已导入", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseProfileCriteriaJson(text: String): ProfileCriteriaImport {
        val json = JSONObject(text)
        val gender = json.optString("gender").trim()
        val ipLocation = json.optString("ipLocation").trim()
        if (gender !in listOf("男", "女")) {
            throw IllegalArgumentException("Invalid gender")
        }
        if (ipLocation !in ipLocationOptions) {
            throw IllegalArgumentException("Invalid IP location")
        }

        val whitelistJson = json.optJSONArray("whitelist") ?: JSONArray()
        val whitelist = buildList {
            for (index in 0 until whitelistJson.length()) {
                val keyword = whitelistJson.optString(index).trim()
                if (keyword.isNotBlank() && keyword !in this) {
                    add(keyword)
                }
            }
        }
        return ProfileCriteriaImport(
            gender = gender,
            ipLocation = ipLocation,
            whitelist = whitelist
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

    private data class ProfileCriteriaImport(
        val gender: String,
        val ipLocation: String,
        val whitelist: List<String>
    )
}
