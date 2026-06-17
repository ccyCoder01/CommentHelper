package com.ccy.xhscommenthelper.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.ccy.xhscommenthelper.R
import com.ccy.xhscommenthelper.accessibility.AccessibilityBridge
import com.ccy.xhscommenthelper.accessibility.CommentReader
import com.ccy.xhscommenthelper.accessibility.MessageFiller
import com.ccy.xhscommenthelper.accessibility.NodeDebugDumper
import com.ccy.xhscommenthelper.accessibility.ProfileInfoReader
import com.ccy.xhscommenthelper.accessibility.XhsAccessibilityService
import com.ccy.xhscommenthelper.accessibility.XhsActionExecutor
import com.ccy.xhscommenthelper.data.RecentLeadStore
import com.ccy.xhscommenthelper.data.SettingsRepository
import com.ccy.xhscommenthelper.data.UserSettings
import com.ccy.xhscommenthelper.domain.CommentCandidate
import com.ccy.xhscommenthelper.domain.Lead
import com.ccy.xhscommenthelper.domain.LeadStatus
import com.ccy.xhscommenthelper.domain.ProfileInfo
import com.ccy.xhscommenthelper.util.ClipboardHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FloatingOverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var layoutInflater: LayoutInflater
    private lateinit var params: WindowManager.LayoutParams
    private var currentView: View? = null
    private var expanded = false

    private lateinit var clipboardHelper: ClipboardHelper
    private lateinit var commentReader: CommentReader
    private lateinit var actionExecutor: XhsActionExecutor
    private lateinit var messageFiller: MessageFiller
    private lateinit var profileInfoReader: ProfileInfoReader
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var recentLeadStore: RecentLeadStore

    private var currentLead = Lead()
    private var commentQueue = emptyList<CommentCandidate>()
    private var queueCursor = -1
    private var currentProfileInfo = ProfileInfo()
    private var autoLoopJob: Job? = null
    private var autoLoopRunning = false
    private var autoLoopStopReason: String? = null
    private var openClickSuccessCount = 0
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        layoutInflater = LayoutInflater.from(this)
        clipboardHelper = ClipboardHelper(this)
        commentReader = CommentReader(clipboardHelper)
        actionExecutor = XhsActionExecutor()
        messageFiller = MessageFiller(clipboardHelper)
        profileInfoReader = ProfileInfoReader()
        settingsRepository = SettingsRepository(applicationContext)
        recentLeadStore = RecentLeadStore(applicationContext)
        showCollapsed()
    }

    override fun onDestroy() {
        removeCurrentView()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createLayoutParams(width: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - width - dpToPx(16)
            y = dpToPx(220)
        }
    }

    private fun showCollapsed() {
        expanded = false
        removeCurrentView()
        val view = layoutInflater.inflate(R.layout.overlay_collapsed_button, null)
        params = createLayoutParams(dpToPx(56))
        bindDrag(view)
        view.setOnClickListener { showExpanded() }
        currentView = view
        windowManager.addView(view, params)
    }

    private fun showExpanded() {
        expanded = true
        removeCurrentView()
        val view = layoutInflater.inflate(R.layout.overlay_floating_panel, null)
        params = createLayoutParams(dpToPx(140))
        bindDrag(view)
        bindExpandedActions(view)
        currentView = view
        windowManager.addView(view, params)
        updateExpandedView(view)
    }

    private fun removeCurrentView() {
        currentView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        currentView = null
    }

    private fun bindDrag(view: View) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }

                else -> false
            }
        }
    }

    private fun bindExpandedActions(view: View) {
        view.findViewById<Button>(R.id.openProfileButton)
            .setOnClickListener { onNextClicked() }
        view.findViewById<Button>(R.id.collapseButton).setOnClickListener { showCollapsed() }
    }

    private suspend fun readCommentsOnCurrentScreen(service: XhsAccessibilityService): Boolean {
        val result = readFilteredCommentsOnCurrentScreen(service)
        if (result.filteredCandidates.isEmpty()) {
            commentQueue = emptyList()
            queueCursor = -1
            currentLead = currentLead.copy(
                status = LeadStatus.ERROR,
                updatedAt = System.currentTimeMillis()
            )
            currentProfileInfo = ProfileInfo()
            updateExpandedView()
            val message = if (result.rawCount > 0) {
                "已读取${result.rawCount}条，白名单保留0条"
            } else {
                "未读取到评论，可手动复制评论后再点击下一个。"
            }
            showToast(message)
            return false
        }

        applyCommentQueue(result.filteredCandidates)
        showToast("已读取${result.rawCount}条，白名单保留${result.filteredCandidates.size}条")
        return true
    }

    private suspend fun readFilteredCommentsOnCurrentScreen(
        service: XhsAccessibilityService
    ): CommentReadResult {
        val root = service.getRoot()
        NodeDebugDumper.dump(root, "read_comments")
        val candidates = commentReader.readVisibleComments(root)
        if (candidates.isNotEmpty()) {
            return CommentReadResult(
                rawCount = candidates.size,
                filteredCandidates = filterByCommentWhitelist(candidates)
            )
        }

        val comment = commentReader.readCurrentComment(root)
        if (comment.isNullOrBlank()) {
            return CommentReadResult(rawCount = 0, filteredCandidates = emptyList())
        }

        val fallbackCandidates = listOf(CommentCandidate(comment, 0))
        return CommentReadResult(
            rawCount = fallbackCandidates.size,
            filteredCandidates = filterByCommentWhitelist(fallbackCandidates)
        )
    }

    private suspend fun applyCommentQueue(candidates: List<CommentCandidate>) {
        commentQueue = candidates
        queueCursor = -1
        val firstCandidate = candidates.first()
        currentLead = Lead(
            nickname = firstCandidate.nickname,
            comment = firstCandidate.text,
            status = LeadStatus.COMMENT_READ
        )
        currentProfileInfo = ProfileInfo()
        recentLeadStore.saveComment(firstCandidate.text)
        updateExpandedView()
    }

    private suspend fun filterByCommentWhitelist(
        candidates: List<CommentCandidate>
    ): List<CommentCandidate> {
        val whitelist = settingsRepository.settingsFlow.first()
            .commentWhitelist
            .map { keyword -> keyword.trim() }
            .filter { keyword -> keyword.isNotBlank() }
        if (whitelist.isEmpty()) {
            return candidates.mapIndexed { index, candidate -> candidate.copy(index = index) }
        }
        return candidates
            .filter { candidate -> whitelist.any { keyword -> candidate.text.contains(keyword) } }
            .mapIndexed { index, candidate -> candidate.copy(index = index) }
    }

    private fun onNextClicked() {
        val service = AccessibilityBridge.service
        if (service == null) {
            showToast("请先开启辅助功能权限，否则无法读取当前评论。")
            return
        }

        if (autoLoopRunning) {
            stopAutoLoop("已停止")
            return
        }

        if (openClickSuccessCount >= MAX_OPEN_CLICK_SUCCESS_COUNT) {
            showToast("已发送${MAX_OPEN_CLICK_SUCCESS_COUNT}次，循环已停止")
            return
        }

        startAutoLoop(service)
    }

    private fun startAutoLoop(service: XhsAccessibilityService) {
        autoLoopRunning = true
        autoLoopStopReason = null
        updateExpandedView()
        showToast("已开始循环")
        autoLoopJob = serviceScope.launch {
            while (autoLoopRunning) {
                if (commentQueue.isEmpty() && !readCommentsOnCurrentScreen(service)) {
                    stopAutoLoop("未读取到可处理的评论，循环已停止")
                    return@launch
                }
                if (!openNextProfile(service)) {
                    stopAutoLoop("未能继续处理，循环已停止")
                    return@launch
                }
                autoLoopStopReason?.let { reason ->
                    stopAutoLoop(reason)
                    return@launch
                }
                delay(600)
            }
        }
    }

    private fun stopAutoLoop(message: String? = null) {
        autoLoopRunning = false
        autoLoopJob?.cancel()
        autoLoopJob = null
        commentQueue = emptyList()
        queueCursor = -1
        currentLead = Lead()
        currentProfileInfo = ProfileInfo()
        updateExpandedView()
        message?.let { showToast(it) }
    }

    private suspend fun openNextProfile(service: XhsAccessibilityService): Boolean {
        val comment = nextQueuedComment()
        if (comment == null) {
            showToast("未读取到可处理的评论")
            return false
        }

        val ok =
            actionExecutor.openProfileForComment(service.getRoot(), comment.text, comment.nickname)
        if (ok) {
            currentLead = currentLead.copy(
                nickname = comment.nickname,
                comment = comment.text,
                profileOpened = true,
                status = LeadStatus.PROFILE_OPENED,
                updatedAt = System.currentTimeMillis()
            )
            currentProfileInfo = ProfileInfo()
            recentLeadStore.saveComment(comment.text)
            updateExpandedView()
            showToast("已尝试打开主页")
            delay(1200)
            readProfileInfoOnCurrentScreen(service)
            return true
        } else {
            showToast("未能自动打开主页，请手动点击头像或昵称。")
            return false
        }
    }

    private fun nextQueuedComment(): CommentCandidate? {
        if (commentQueue.isEmpty()) return null
        queueCursor = (queueCursor + 1).coerceAtMost(commentQueue.lastIndex)
        return commentQueue.getOrNull(queueCursor)
    }

    private suspend fun readProfileInfoOnCurrentScreen(service: XhsAccessibilityService) {
        val root = service.getRoot()
        NodeDebugDumper.dump(root, "read_profile_info")
        currentProfileInfo = profileInfoReader.read(root)
        updateExpandedView()
        showToast("已读取当前主页公开信息")
        val settings = settingsRepository.settingsFlow.first()
        if (matchesProfileCriteria(currentProfileInfo, settings)) {
            openMessageEntryAndFill(service)
            performBackSteps(service, 2)
        } else {
            showToast("主页信息不符合画像，已跳过私信入口。")
            performBackSteps(service, 1)
        }
        if (autoLoopStopReason != null) return
        swipeToNextAreaIfQueueConsumed(service)
    }

    private suspend fun openMessageEntryAndFill(service: XhsAccessibilityService) {
        val ok = actionExecutor.openMessageEntry(service.getRoot())
        if (ok) {
            showToast("已尝试打开私信入口")
            delay(800)
            NodeDebugDumper.dump(service.getRoot(), "read_message_entry")
            fillMessageOnCurrentScreen(service)
        } else {
            showToast("未找到私信入口，请手动进入聊天框。")
        }
    }

    private suspend fun performBackSteps(service: XhsAccessibilityService, count: Int) {
        delay(1000)
        repeat(count) { index ->
            service.performBack()
            if (index < count - 1) {
                delay(350)
            }
        }
    }

    private suspend fun swipeToNextAreaIfQueueConsumed(service: XhsAccessibilityService) {
        if (commentQueue.isEmpty() || queueCursor < commentQueue.lastIndex) return
        autoLoopStopReason = null
        val previousSignature = commentQueueSignature(commentQueue)
        delay(1000)
        val ok = service.performSwipeToNextArea()
        if (ok) {
            delay(3000)
            val result = readFilteredCommentsOnCurrentScreen(service)
            if (result.filteredCandidates.isEmpty()) {
                commentQueue = emptyList()
                queueCursor = -1
                currentLead = Lead()
                currentProfileInfo = ProfileInfo()
                updateExpandedView()
                showToast("已滑动到下一区域，未读取到新队列")
                autoLoopStopReason = "未读取到新队列，循环已停止"
                return
            }

            applyCommentQueue(result.filteredCandidates)
            if (commentQueueSignature(result.filteredCandidates) == previousSignature) {
                showToast("滑动后内容未变化")
                autoLoopStopReason = "滑动后内容未变化，循环已停止"
            } else {
                showToast("已滑动并读取${result.filteredCandidates.size}条")
            }
        } else {
            showToast("队列已消费完，滑动失败")
            autoLoopStopReason = "滑动失败，循环已停止"
        }
    }

    private fun commentQueueSignature(candidates: List<CommentCandidate>): String {
        return candidates.joinToString(separator = "\n") { candidate ->
            "${candidate.nickname.orEmpty()}|${candidate.text}"
        }
    }

    private suspend fun fillMessageOnCurrentScreen(service: XhsAccessibilityService) {
        val fixedText = settingsRepository.settingsFlow.first().fixedText.trim()
        if (fixedText.isBlank()) {
            showToast("请先在主界面配置固定话术。")
            return
        }

        NodeDebugDumper.dump(service.getRoot(), "before_fill_message")
        val ok = messageFiller.fillMessage(service.getRoot(), fixedText)
        delay(300)
        NodeDebugDumper.dump(service.getRoot(), "after_fill_message")
        if (ok) {
            currentLead = currentLead.copy(
                status = LeadStatus.MESSAGE_FILLED,
                updatedAt = System.currentTimeMillis()
            )
            showToast("已填入固定话术。")
            if (actionExecutor.openClick(service.getRoot())) {
                openClickSuccessCount += 1
                showToast("已发送：$openClickSuccessCount/$MAX_OPEN_CLICK_SUCCESS_COUNT")
                if (openClickSuccessCount >= MAX_OPEN_CLICK_SUCCESS_COUNT) {
                    autoLoopStopReason = "已发送${MAX_OPEN_CLICK_SUCCESS_COUNT}次，循环已停止"
                }
            } else {
                showToast("发送失败。")
            }
        } else {
            clipboardHelper.copyText(fixedText)
            showToast("自动填入失败，已复制固定话术，请手动粘贴。")
        }
    }

    private fun matchesProfileCriteria(profileInfo: ProfileInfo, settings: UserSettings): Boolean {
        val targetGender = settings.targetGender.trim()
        val targetIpLocation = settings.targetIpLocation.trim()
        return targetGender.isNotBlank() &&
                targetIpLocation.isNotBlank() &&
                profileInfo.visibleGender == targetGender &&
                profileInfo.ipLocation == targetIpLocation
    }

    private fun updateExpandedView(view: View? = currentView) {
        if (!expanded || view == null) return
        view.findViewById<Button>(R.id.openProfileButton).text =
            if (autoLoopRunning) "停止" else "下一个"
        view.findViewById<TextView>(R.id.queueTextView).text =
            "评论队列：${if (queueCursor >= 0) queueCursor + 1 else 0}/${commentQueue.size}"
        view.findViewById<TextView>(R.id.queuePreviewTextView).text = queuePreviewText()
    }

    private fun queuePreviewText(): CharSequence {
        if (commentQueue.isEmpty()) return "暂无队列"
        val visibleQueue = visibleQueuePreviewItems()
        val lines = visibleQueue.map { candidate ->
            val position = candidate.index + 1
            val nickname = candidate.nickname?.takeIf { it.isNotBlank() } ?: "未识别"
            "$position. $nickname"
        }
        val text = lines.joinToString(separator = "\n")
        val currentLineIndex = visibleQueue.indexOfFirst { candidate -> candidate.index == queueCursor }
        if (currentLineIndex < 0) return text

        val start = lines.take(currentLineIndex).sumOf { line -> line.length + 1 }
        val end = start + lines[currentLineIndex].length
        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(Color.rgb(244, 67, 54)),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun visibleQueuePreviewItems(): List<CommentCandidate> {
        if (commentQueue.size <= MAX_QUEUE_PREVIEW_COUNT) return commentQueue
        val currentIndex = commentQueue.indexOfFirst { candidate -> candidate.index == queueCursor }
            .takeIf { it >= 0 }
            ?: 0
        val start = currentIndex
            .coerceAtMost((commentQueue.size - MAX_QUEUE_PREVIEW_COUNT).coerceAtLeast(0))
        return commentQueue.drop(start).take(MAX_QUEUE_PREVIEW_COUNT)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private data class CommentReadResult(
        val rawCount: Int,
        val filteredCandidates: List<CommentCandidate>
    )

    private companion object {
        const val MAX_QUEUE_PREVIEW_COUNT = 6
        const val MAX_OPEN_CLICK_SUCCESS_COUNT = 6
    }
}
