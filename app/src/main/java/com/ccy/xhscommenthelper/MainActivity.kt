package com.ccy.xhscommenthelper

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
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
import com.ccy.xhscommenthelper.analytics.RoseChartView
import com.ccy.xhscommenthelper.analytics.SemiRingChartView
import com.ccy.xhscommenthelper.data.SettingsRepository
import com.ccy.xhscommenthelper.data.StatsRepository
import com.ccy.xhscommenthelper.domain.ArchiveLabelStatus
import com.ccy.xhscommenthelper.domain.ArchivedMessageRecord
import com.ccy.xhscommenthelper.overlay.FloatingOverlayService
import com.ccy.xhscommenthelper.util.PermissionHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private enum class MainPage {
        Settings,
        Stats,
        Analytics
    }

    private val reasonColors = listOf(
        Color.parseColor("#FFB64A4A"),
        Color.parseColor("#FF3F7E68"),
        Color.parseColor("#FF4F6F93"),
        Color.parseColor("#FFD49A3A"),
        Color.parseColor("#FF8B6FA8"),
        Color.parseColor("#FF7A6A55")
    )

    private var syncingBottomNavigation = false
    private var currentPage = MainPage.Settings
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
    private val defaultStatsGender = "女"
    private val defaultStatsIpLocation = "陕西"
    private val statsGenderOptions = listOf("男", "女")
    private val statsIpLocationOptions = ipLocationOptions
    private val labelStatusOptions = ArchiveLabelStatus.entries.map { status -> status.displayName }

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var statsRepository: StatsRepository

    private lateinit var settingsPage: View
    private lateinit var statsPage: View
    private lateinit var analyticsPage: View
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var accessibilityStatusCard: View
    private lateinit var accessibilityPermissionTextView: TextView
    private lateinit var fixedTextEditText: EditText
    private lateinit var profileRequirementEditText: EditText
    private lateinit var targetGenderRadioGroup: RadioGroup
    private lateinit var targetMaleRadioButton: RadioButton
    private lateinit var targetFemaleRadioButton: RadioButton
    private lateinit var targetIpLocationSpinner: Spinner
    private lateinit var statsListView: ListView
    private lateinit var xhsIdEditText: EditText
    private lateinit var nicknameEditText: EditText
    private lateinit var genderSpinner: Spinner
    private lateinit var ipLocationSpinner: Spinner
    private lateinit var commentEditText: EditText
    private lateinit var labelStatusSpinner: Spinner
    private lateinit var labelReasonEditText: EditText
    private lateinit var semiRingChartView: SemiRingChartView
    private lateinit var semiRingLegendTextView: TextView
    private lateinit var roseChartView: RoseChartView
    private lateinit var roseLegendTextView: TextView
    private lateinit var statsAdapter: ArrayAdapter<String>
    private var records: List<ArchivedMessageRecord> = emptyList()

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
        statsRepository = StatsRepository(applicationContext)
        bindViews()
        bindActions()
        observeState()
        showPage(MainPage.Settings)
        ensureOverlayPermissionAndService()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityPermissionStatus()
        if (currentPage == MainPage.Analytics) {
            loadAnalytics()
        }
        ensureOverlayPermissionAndService()
    }

    private fun bindViews() {
        settingsPage = findViewById(R.id.settingsPage)
        statsPage = findViewById(R.id.statsPage)
        analyticsPage = findViewById(R.id.analyticsPage)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        accessibilityStatusCard = findViewById(R.id.accessibilityStatusCard)
        accessibilityPermissionTextView = findViewById(R.id.accessibilityPermissionTextView)
        fixedTextEditText = findViewById(R.id.fixedTextEditText)
        profileRequirementEditText = findViewById(R.id.profileRequirementEditText)
        targetGenderRadioGroup = findViewById(R.id.targetGenderRadioGroup)
        targetMaleRadioButton = findViewById(R.id.targetMaleRadioButton)
        targetFemaleRadioButton = findViewById(R.id.targetFemaleRadioButton)
        targetIpLocationSpinner = findViewById(R.id.targetIpLocationSpinner)
        statsListView = findViewById(R.id.statsListView)
        xhsIdEditText = findViewById(R.id.xhsIdEditText)
        nicknameEditText = findViewById(R.id.nicknameEditText)
        genderSpinner = findViewById(R.id.genderSpinner)
        ipLocationSpinner = findViewById(R.id.ipLocationSpinner)
        commentEditText = findViewById(R.id.commentEditText)
        labelStatusSpinner = findViewById(R.id.labelStatusSpinner)
        labelReasonEditText = findViewById(R.id.labelReasonEditText)
        semiRingChartView = findViewById(R.id.semiRingChartView)
        semiRingLegendTextView = findViewById(R.id.semiRingLegendTextView)
        roseChartView = findViewById(R.id.roseChartView)
        roseLegendTextView = findViewById(R.id.roseLegendTextView)

        val ipLocationAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ipLocationOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        targetIpLocationSpinner.adapter = ipLocationAdapter

        val statsGenderAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            statsGenderOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        genderSpinner.adapter = statsGenderAdapter
        applyStatsGenderSelection("")

        val statsIpLocationAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            statsIpLocationOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        ipLocationSpinner.adapter = statsIpLocationAdapter
        applyStatsIpLocationSelection("")

        val labelStatusAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labelStatusOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        labelStatusSpinner.adapter = labelStatusAdapter
        applyLabelStatusSelection(ArchiveLabelStatus.Unlabeled.storageValue)

        statsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        statsListView.adapter = statsAdapter
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

        bottomNavigation.setOnItemSelectedListener { item ->
            if (syncingBottomNavigation) return@setOnItemSelectedListener true
            when (item.itemId) {
                R.id.navigationSettings -> {
                    showPage(MainPage.Settings)
                    true
                }
                R.id.navigationStats -> {
                    showPage(MainPage.Stats)
                    true
                }
                R.id.navigationAnalytics -> {
                    showPage(MainPage.Analytics)
                    true
                }
                else -> false
            }
        }

        statsListView.setOnItemClickListener { _, _, position, _ ->
            records.getOrNull(position)?.let { record -> applyRecord(record) }
        }

        findViewById<Button>(R.id.saveRecordButton).setOnClickListener {
            saveRecord()
        }

        findViewById<Button>(R.id.deleteRecordButton).setOnClickListener {
            deleteRecord()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            fixedTextEditText.setText(settings.fixedText)
            profileRequirementEditText.setText(settings.profileRequirement)
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
            ipLocation = selectedIpLocation(),
            profileRequirement = profileRequirementEditText.text.toString()
        )
    }

    private fun showPage(page: MainPage) {
        currentPage = page
        settingsPage.visibility = if (page == MainPage.Settings) View.VISIBLE else View.GONE
        statsPage.visibility = if (page == MainPage.Stats) View.VISIBLE else View.GONE
        analyticsPage.visibility = if (page == MainPage.Analytics) View.VISIBLE else View.GONE
        val targetItemId = when (page) {
            MainPage.Settings -> R.id.navigationSettings
            MainPage.Stats -> R.id.navigationStats
            MainPage.Analytics -> R.id.navigationAnalytics
        }
        if (bottomNavigation.selectedItemId != targetItemId) {
            syncingBottomNavigation = true
            try {
                bottomNavigation.selectedItemId = targetItemId
            } finally {
                syncingBottomNavigation = false
            }
        }
        if (page == MainPage.Stats) {
            loadRecords()
        }
        if (page == MainPage.Analytics) {
            loadAnalytics()
        }
    }

    private fun loadRecords() {
        lifecycleScope.launch {
            records = statsRepository.getAll()
                .sortedByDescending { record -> record.updatedAt }
            statsAdapter.clear()
            statsAdapter.addAll(records.map { record -> record.toListLabel() })
            statsAdapter.notifyDataSetChanged()
        }
    }

    private fun loadAnalytics() {
        lifecycleScope.launch {
            val records = statsRepository.getAll()
            val successCount = records.count { record ->
                ArchiveLabelStatus.fromStorageValue(record.labelStatus) == ArchiveLabelStatus.Success
            }
            val failureCount = records.count { record ->
                ArchiveLabelStatus.fromStorageValue(record.labelStatus) == ArchiveLabelStatus.Failure
            }
            val unlabeledCount = records.count { record ->
                ArchiveLabelStatus.fromStorageValue(record.labelStatus) == ArchiveLabelStatus.Unlabeled
            }

            val semiSegments = listOf(
                SemiRingChartView.Segment("成功", successCount, Color.parseColor("#FF3F7E68")),
                SemiRingChartView.Segment("失败", failureCount, Color.parseColor("#FFB64A4A")),
                SemiRingChartView.Segment("未标记", unlabeledCount, Color.parseColor("#FF8A929D"))
            )
            semiRingChartView.setSegments(semiSegments)
            semiRingLegendTextView.text = semiSegments.joinToString("    ") { segment ->
                "${segment.label}：${segment.count}"
            }

            val reasonCounts = records
                .filter { record ->
                    ArchiveLabelStatus.fromStorageValue(record.labelStatus) == ArchiveLabelStatus.Failure
                }
                .groupingBy { record ->
                    record.labelReason.trim().ifBlank { "未填写原因" }
                }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })

            val roseSlices = reasonCounts.mapIndexed { index, entry ->
                RoseChartView.Slice(
                    label = entry.key,
                    count = entry.value,
                    color = reasonColors[index % reasonColors.size]
                )
            }
            roseChartView.setSlices(roseSlices)
            roseLegendTextView.text = if (roseSlices.isEmpty()) {
                "暂无失败记录"
            } else {
                roseSlices.joinToString("\n") { slice -> "${slice.label}：${slice.count}" }
            }
        }
    }

    private fun saveRecord() {
        val xhsId = xhsIdEditText.text.toString().trim()
        if (xhsId.isBlank()) {
            Toast.makeText(this, "小红书号不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            statsRepository.save(
                ArchivedMessageRecord(
                    xhsId = xhsId,
                    nickname = nicknameEditText.text.toString().trim(),
                    gender = selectedStatsGender(),
                    ipLocation = selectedStatsIpLocation(),
                    comment = commentEditText.text.toString().trim(),
                    labelStatus = selectedLabelStatus().storageValue,
                    labelReason = labelReasonEditText.text.toString().trim()
                )
            )
            Toast.makeText(this@MainActivity, "记录已保存", Toast.LENGTH_SHORT).show()
            loadRecords()
        }
    }

    private fun deleteRecord() {
        val xhsId = xhsIdEditText.text.toString().trim()
        if (xhsId.isBlank()) {
            Toast.makeText(this, "请先选择或输入小红书号", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            statsRepository.delete(xhsId)
            Toast.makeText(this@MainActivity, "记录已删除", Toast.LENGTH_SHORT).show()
            clearForm()
            loadRecords()
        }
    }

    private fun applyRecord(record: ArchivedMessageRecord) {
        xhsIdEditText.setText(record.xhsId)
        nicknameEditText.setText(record.nickname)
        applyStatsGenderSelection(record.gender)
        applyStatsIpLocationSelection(record.ipLocation)
        commentEditText.setText(record.comment)
        applyLabelStatusSelection(record.labelStatus)
        labelReasonEditText.setText(record.labelReason)
    }

    private fun clearForm() {
        xhsIdEditText.setText("")
        nicknameEditText.setText("")
        applyStatsGenderSelection("")
        applyStatsIpLocationSelection("")
        commentEditText.setText("")
        applyLabelStatusSelection(ArchiveLabelStatus.Unlabeled.storageValue)
        labelReasonEditText.setText("")
        statsListView.clearChoices()
    }

    private fun selectedStatsGender(): String {
        return genderSpinner.selectedItem?.toString().orEmpty()
    }

    private fun selectedStatsIpLocation(): String {
        return ipLocationSpinner.selectedItem?.toString().orEmpty()
    }

    private fun applyStatsGenderSelection(gender: String) {
        val target = gender.ifBlank { defaultStatsGender }
        val index = statsGenderOptions.indexOf(target).takeIf { it >= 0 }
            ?: statsGenderOptions.indexOf(defaultStatsGender)
        genderSpinner.setSelection(index)
    }

    private fun applyStatsIpLocationSelection(ipLocation: String) {
        val target = ipLocation.ifBlank { defaultStatsIpLocation }
        val index = statsIpLocationOptions.indexOf(target).takeIf { it >= 0 }
            ?: statsIpLocationOptions.indexOf(defaultStatsIpLocation).takeIf { it >= 0 }
            ?: 0
        ipLocationSpinner.setSelection(index)
    }

    private fun selectedLabelStatus(): ArchiveLabelStatus {
        return ArchiveLabelStatus.fromDisplayName(labelStatusSpinner.selectedItem?.toString().orEmpty())
    }

    private fun applyLabelStatusSelection(labelStatus: String) {
        val status = ArchiveLabelStatus.fromStorageValue(labelStatus)
        val index = labelStatusOptions.indexOf(status.displayName).takeIf { it >= 0 } ?: 0
        labelStatusSpinner.setSelection(index)
    }

    private fun ArchivedMessageRecord.toListLabel(): String {
        val nicknameText = nickname.ifBlank { "未识别昵称" }
        val genderText = gender.ifBlank { "性别未识别" }
        val ipText = ipLocation.ifBlank { "IP未识别" }
        val labelText = ArchiveLabelStatus.fromStorageValue(labelStatus).displayName
        return "$xhsId / $nicknameText / $genderText / $ipText / $labelText"
    }

    private fun updateAccessibilityPermissionStatus() {
        if (PermissionHelper.isAccessibilityServiceEnabled(this)) {
            accessibilityStatusCard.setBackgroundResource(R.drawable.status_card_enabled_background)
            accessibilityPermissionTextView.text = "辅助功能已开启"
            accessibilityPermissionTextView.setTextColor(Color.parseColor("#FF2F6B45"))
            findViewById<Button>(R.id.openAccessibilitySettingsButton).text = "查看设置"
        } else {
            accessibilityStatusCard.setBackgroundResource(R.drawable.status_card_disabled_background)
            accessibilityPermissionTextView.text = "辅助功能未开启，开启后才能读取页面内容。"
            accessibilityPermissionTextView.setTextColor(Color.parseColor("#FF7A4B2A"))
            findViewById<Button>(R.id.openAccessibilitySettingsButton).text = "去开启"
        }
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
