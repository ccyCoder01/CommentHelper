package com.ccy.xhscommenthelper

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.ccy.xhscommenthelper.analytics.FailureReasonBarChartView
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
    private companion object {
        const val ARCHIVE_PAGE_SIZE = 5
    }

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
    private val labelStatusOptions = ArchiveLabelStatus.entries.map { status -> status.displayName }
    private val labelReasonOptions = listOf("不理人", "不想找了", "卡年龄", "我不会说话", "卡颜", "卡 IP")

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
    private lateinit var llmApiKeyEditText: EditText
    private lateinit var targetGenderRadioGroup: RadioGroup
    private lateinit var targetMaleRadioButton: RadioButton
    private lateinit var targetFemaleRadioButton: RadioButton
    private lateinit var targetIpLocationSpinner: Spinner
    private lateinit var statsListView: ListView
    private lateinit var archivePrevPageButton: Button
    private lateinit var archiveNextPageButton: Button
    private lateinit var archivePageTextView: TextView
    private lateinit var xhsIdEditText: EditText
    private lateinit var labelStatusSpinner: Spinner
    private lateinit var labelReasonSpinner: Spinner
    private lateinit var semiRingChartView: SemiRingChartView
    private lateinit var semiRingLegendTextView: TextView
    private lateinit var failureReasonBarChartView: FailureReasonBarChartView
    private lateinit var statsAdapter: ArchivedRecordAdapter
    private var records: List<ArchivedMessageRecord> = emptyList()
    private var pagedRecords: List<ArchivedMessageRecord> = emptyList()
    private var archiveCurrentPage = 1

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
        llmApiKeyEditText = findViewById(R.id.llmApiKeyEditText)
        targetGenderRadioGroup = findViewById(R.id.targetGenderRadioGroup)
        targetMaleRadioButton = findViewById(R.id.targetMaleRadioButton)
        targetFemaleRadioButton = findViewById(R.id.targetFemaleRadioButton)
        targetIpLocationSpinner = findViewById(R.id.targetIpLocationSpinner)
        statsListView = findViewById(R.id.statsListView)
        archivePrevPageButton = findViewById(R.id.archivePrevPageButton)
        archiveNextPageButton = findViewById(R.id.archiveNextPageButton)
        archivePageTextView = findViewById(R.id.archivePageTextView)
        xhsIdEditText = findViewById(R.id.xhsIdEditText)
        labelStatusSpinner = findViewById(R.id.labelStatusSpinner)
        labelReasonSpinner = findViewById(R.id.labelReasonSpinner)
        semiRingChartView = findViewById(R.id.semiRingChartView)
        semiRingLegendTextView = findViewById(R.id.semiRingLegendTextView)
        failureReasonBarChartView = findViewById(R.id.failureReasonBarChartView)

        val ipLocationAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ipLocationOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        targetIpLocationSpinner.adapter = ipLocationAdapter

        val labelStatusAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labelStatusOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        labelStatusSpinner.adapter = labelStatusAdapter
        applyLabelStatusSelection(ArchiveLabelStatus.Unlabeled.storageValue)

        val labelReasonAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labelReasonOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        labelReasonSpinner.adapter = labelReasonAdapter
        applyLabelReasonSelection("")

        statsAdapter = ArchivedRecordAdapter(this)
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
            pagedRecords.getOrNull(position)?.let { record -> applyRecord(record) }
        }

        archivePrevPageButton.setOnClickListener {
            if (archiveCurrentPage > 1) {
                archiveCurrentPage -= 1
                renderArchivePage()
            }
        }

        archiveNextPageButton.setOnClickListener {
            val totalPages = archiveTotalPages()
            if (archiveCurrentPage < totalPages) {
                archiveCurrentPage += 1
                renderArchivePage()
            }
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
            llmApiKeyEditText.setText(settings.llmApiKey)
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
            profileRequirement = profileRequirementEditText.text.toString(),
            llmApiKey = llmApiKeyEditText.text.toString()
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
            clampArchiveCurrentPage()
            renderArchivePage()
        }
    }

    private fun loadRecordsFromFirstPage() {
        archiveCurrentPage = 1
        loadRecords()
    }

    private fun renderArchivePage() {
        clampArchiveCurrentPage()
        val totalPages = archiveTotalPages()
        val startIndex = if (totalPages == 0) 0 else (archiveCurrentPage - 1) * ARCHIVE_PAGE_SIZE
        pagedRecords = if (totalPages == 0) {
            emptyList()
        } else {
            records.drop(startIndex).take(ARCHIVE_PAGE_SIZE)
        }

        statsAdapter.clear()
        statsAdapter.addAll(pagedRecords)
        statsAdapter.notifyDataSetChanged()
        statsListView.clearChoices()

        archivePageTextView.text = if (totalPages == 0) {
            "第 0/0 页"
        } else {
            "第 $archiveCurrentPage/$totalPages 页"
        }
        archivePrevPageButton.isEnabled = totalPages > 0 && archiveCurrentPage > 1
        archiveNextPageButton.isEnabled = totalPages > 0 && archiveCurrentPage < totalPages
    }

    private fun archiveTotalPages(): Int {
        if (records.isEmpty()) return 0
        return (records.size + ARCHIVE_PAGE_SIZE - 1) / ARCHIVE_PAGE_SIZE
    }

    private fun clampArchiveCurrentPage() {
        val totalPages = archiveTotalPages()
        archiveCurrentPage = when {
            totalPages == 0 -> 1
            archiveCurrentPage < 1 -> 1
            archiveCurrentPage > totalPages -> totalPages
            else -> archiveCurrentPage
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

            val reasonBars = reasonCounts.mapIndexed { index, entry ->
                FailureReasonBarChartView.Bar(
                    label = entry.key,
                    count = entry.value,
                    color = reasonColors[index % reasonColors.size]
                )
            }
            failureReasonBarChartView.setBars(reasonBars)
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
                    labelStatus = selectedLabelStatus().storageValue,
                    labelReason = selectedLabelReason()
                )
            )
            Toast.makeText(this@MainActivity, "记录已保存", Toast.LENGTH_SHORT).show()
            loadRecordsFromFirstPage()
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
        applyLabelStatusSelection(record.labelStatus)
        applyLabelReasonSelection(record.labelReason)
    }

    private fun clearForm() {
        xhsIdEditText.setText("")
        applyLabelStatusSelection(ArchiveLabelStatus.Unlabeled.storageValue)
        applyLabelReasonSelection("")
        statsListView.clearChoices()
    }

    private fun selectedLabelStatus(): ArchiveLabelStatus {
        return ArchiveLabelStatus.fromDisplayName(labelStatusSpinner.selectedItem?.toString().orEmpty())
    }

    private fun applyLabelStatusSelection(labelStatus: String) {
        val status = ArchiveLabelStatus.fromStorageValue(labelStatus)
        val index = labelStatusOptions.indexOf(status.displayName).takeIf { it >= 0 } ?: 0
        labelStatusSpinner.setSelection(index)
    }

    private fun selectedLabelReason(): String {
        return labelReasonSpinner.selectedItem?.toString().orEmpty()
    }

    private fun applyLabelReasonSelection(reason: String) {
        val normalizedReason = reason.trim()
        val index = labelReasonOptions.indexOf(normalizedReason).takeIf { it >= 0 } ?: 0
        labelReasonSpinner.setSelection(index)
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

    private class ArchivedRecordAdapter(
        context: MainActivity
    ) : ArrayAdapter<ArchivedMessageRecord>(context, R.layout.item_archived_record, mutableListOf()) {
        private val inflater = LayoutInflater.from(context)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(R.layout.item_archived_record, parent, false)
            val record = getItem(position)
            val labelStatus = ArchiveLabelStatus.fromStorageValue(record?.labelStatus.orEmpty())
            val reason = record?.labelReason?.trim().orEmpty().ifBlank { "未填写原因" }

            view.findViewById<TextView>(R.id.recordXhsIdTextView).text = record?.xhsId.orEmpty()
            view.findViewById<TextView>(R.id.recordLabelStatusTextView).text = labelStatus.displayName
            view.findViewById<TextView>(R.id.recordLabelReasonTextView).text = reason
            return view
        }
    }
}
