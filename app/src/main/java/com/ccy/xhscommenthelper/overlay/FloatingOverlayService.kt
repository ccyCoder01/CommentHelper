package com.ccy.xhscommenthelper.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
import com.ccy.xhscommenthelper.accessibility.XhsActionExecutor
import com.ccy.xhscommenthelper.data.RecentLeadStore
import com.ccy.xhscommenthelper.data.SettingsRepository
import com.ccy.xhscommenthelper.domain.Lead
import com.ccy.xhscommenthelper.domain.LeadStatus
import com.ccy.xhscommenthelper.domain.CommentCandidate
import com.ccy.xhscommenthelper.domain.ProfileInfo
import com.ccy.xhscommenthelper.util.ClipboardHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
            x = resources.displayMetrics.widthPixels - width
            y = resources.displayMetrics.heightPixels / 3
        }
    }

    private fun showCollapsed() {
        expanded = false
        removeCurrentView()
        val view = layoutInflater.inflate(R.layout.overlay_collapsed_button, null)
        params = createLayoutParams(dpToPx(48))
        bindDrag(view)
        view.setOnClickListener { showExpanded() }
        currentView = view
        windowManager.addView(view, params)
    }

    private fun showExpanded() {
        expanded = true
        removeCurrentView()
        val view = layoutInflater.inflate(R.layout.overlay_floating_panel, null)
        params = createLayoutParams(dpToPx(300))
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
        view.findViewById<Button>(R.id.readCommentButton).setOnClickListener { onReadCommentClicked() }
        view.findViewById<Button>(R.id.openProfileButton).setOnClickListener { onOpenProfileClicked() }
        view.findViewById<Button>(R.id.readProfileInfoButton).setOnClickListener { onReadProfileInfoClicked() }
        view.findViewById<Button>(R.id.openMessageEntryButton).setOnClickListener { onOpenMessageEntryClicked() }
        view.findViewById<Button>(R.id.collapseButton).setOnClickListener { showCollapsed() }
    }

    private fun onReadCommentClicked() {
        val service = AccessibilityBridge.service
        if (service == null) {
            showToast("请先开启辅助功能权限，否则无法读取当前评论。")
            return
        }

        val root = service.getRoot()
        NodeDebugDumper.dump(root, "read_comments")
        val candidates = commentReader.readVisibleComments(root)
        if (candidates.isNotEmpty()) {
            commentQueue = candidates
            queueCursor = -1
            val firstCandidate = candidates.first()
            val comment = firstCandidate.text
            currentLead = Lead(
                nickname = firstCandidate.nickname,
                comment = comment,
                status = LeadStatus.COMMENT_READ
            )
            currentProfileInfo = ProfileInfo()
            serviceScope.launch { recentLeadStore.saveComment(comment) }
            updateExpandedView()
            showToast("已读取${candidates.size}条当前已加载主页候选")
            return
        }

        val comment = commentReader.readCurrentComment(root)
        if (comment.isNullOrBlank()) {
            currentLead = currentLead.copy(status = LeadStatus.ERROR, updatedAt = System.currentTimeMillis())
            showToast("未读取到评论，可手动复制评论后再点击读取。")
            return
        }

        currentLead = Lead(comment = comment, status = LeadStatus.COMMENT_READ)
        commentQueue = listOf(CommentCandidate(comment, 0))
        queueCursor = -1
        currentProfileInfo = ProfileInfo()
        serviceScope.launch { recentLeadStore.saveComment(comment) }
        updateExpandedView()
    }

    private fun onOpenProfileClicked() {
        val service = AccessibilityBridge.service
        if (service == null) {
            showToast("请先开启辅助功能权限，否则无法读取当前评论。")
            return
        }

        val comment = nextQueuedComment()
        if (comment == null) {
            showToast("请先读取评论/回复列表")
            return
        }

        val ok = actionExecutor.openProfileForComment(service.getRoot(), comment.text, comment.nickname)
        if (ok) {
            currentLead = currentLead.copy(
                nickname = comment.nickname,
                comment = comment.text,
                profileOpened = true,
                status = LeadStatus.PROFILE_OPENED,
                updatedAt = System.currentTimeMillis()
            )
            currentProfileInfo = ProfileInfo()
            serviceScope.launch { recentLeadStore.saveComment(comment.text) }
            updateExpandedView()
            showToast("已尝试打开主页")
        } else {
            showToast("未能自动打开主页，请手动点击头像或昵称。")
        }
    }

    private fun nextQueuedComment(): CommentCandidate? {
        if (commentQueue.isEmpty()) return null
        queueCursor = (queueCursor + 1).coerceAtMost(commentQueue.lastIndex)
        return commentQueue.getOrNull(queueCursor)
    }

    private fun onReadProfileInfoClicked() {
        val service = AccessibilityBridge.service
        if (service == null) {
            showToast("请先开启辅助功能权限，否则无法读取主页信息。")
            return
        }

        val root = service.getRoot()
        NodeDebugDumper.dump(root, "read_profile_info")
        currentProfileInfo = profileInfoReader.read(root)
        updateExpandedView()
        showToast("已读取当前主页公开信息")
    }

    private fun onOpenMessageEntryClicked() {
        val service = AccessibilityBridge.service
        if (service == null) {
            showToast("请先开启辅助功能权限，否则无法打开私信入口。")
            return
        }

        val ok = actionExecutor.openMessageEntry(service.getRoot())
        if (ok) {
            showToast("已尝试打开私信入口")
            serviceScope.launch {
                delay(800)
                NodeDebugDumper.dump(service.getRoot(), "read_message_entry")
                fillMessageOnCurrentScreen(service)
            }
        } else {
            showToast("未找到私信入口，请手动进入聊天框。")
        }
    }

    private suspend fun fillMessageOnCurrentScreen(service: com.ccy.xhscommenthelper.accessibility.XhsAccessibilityService) {
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
            showToast("已填入固定话术，请人工确认发送。")
        } else {
            clipboardHelper.copyText(fixedText)
            showToast("自动填入失败，已复制固定话术，请手动粘贴。")
        }
    }

    private fun updateExpandedView(view: View? = currentView) {
        if (!expanded || view == null) return
        view.findViewById<TextView>(R.id.commentTextView).text =
            "当前主页：${currentLead.nickname ?: "未识别"}\n当前评论：${currentLead.comment ?: "暂无"}"
        view.findViewById<TextView>(R.id.queueTextView).text =
            "评论队列：${if (queueCursor >= 0) queueCursor + 1 else 0}/${commentQueue.size}"
        view.findViewById<TextView>(R.id.profileInfoTextView).text =
            "主页信息：${currentProfileInfo.summary.ifBlank { "暂无" }}"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
