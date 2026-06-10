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
import com.ccy.xhscommenthelper.accessibility.XhsActionExecutor
import com.ccy.xhscommenthelper.data.RecentLeadStore
import com.ccy.xhscommenthelper.data.SettingsRepository
import com.ccy.xhscommenthelper.domain.Lead
import com.ccy.xhscommenthelper.domain.LeadStatus
import com.ccy.xhscommenthelper.domain.MessageBuilder
import com.ccy.xhscommenthelper.util.ClipboardHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var recentLeadStore: RecentLeadStore

    private var currentLead = Lead()
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
        params = createLayoutParams(dpToPx(260))
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
        view.findViewById<Button>(R.id.suitableButton).setOnClickListener { onSuitableClicked() }
        view.findViewById<Button>(R.id.unsuitableButton).setOnClickListener { onUnsuitableClicked() }
        view.findViewById<Button>(R.id.generateMessageButton).setOnClickListener { onGenerateMessageClicked() }
        view.findViewById<Button>(R.id.copyMessageButton).setOnClickListener { onCopyMessageClicked() }
        view.findViewById<Button>(R.id.fillMessageButton).setOnClickListener { onFillMessageClicked() }
        view.findViewById<Button>(R.id.collapseButton).setOnClickListener { showCollapsed() }
    }

    private fun onReadCommentClicked() {
        val service = AccessibilityBridge.service
        if (service == null) {
            showToast("请先开启辅助功能权限，否则无法读取当前评论。")
            return
        }

        val comment = commentReader.readCurrentComment(service.getRoot())
        if (comment.isNullOrBlank()) {
            currentLead = currentLead.copy(status = LeadStatus.ERROR, updatedAt = System.currentTimeMillis())
            showToast("未读取到评论，可手动复制评论后再点击读取。")
            return
        }

        currentLead = Lead(comment = comment, status = LeadStatus.COMMENT_READ)
        serviceScope.launch { recentLeadStore.saveComment(comment) }
        updateExpandedView()
    }

    private fun onOpenProfileClicked() {
        val service = AccessibilityBridge.service
        if (service == null) {
            showToast("请先开启辅助功能权限，否则无法读取当前评论。")
            return
        }

        val ok = actionExecutor.openProfile(service.getRoot())
        if (ok) {
            currentLead = currentLead.copy(
                profileOpened = true,
                status = LeadStatus.PROFILE_OPENED,
                updatedAt = System.currentTimeMillis()
            )
            showToast("已尝试打开主页")
        } else {
            showToast("未能自动打开主页，请手动点击头像或昵称。")
        }
    }

    private fun onSuitableClicked() {
        currentLead = currentLead.copy(
            suitable = true,
            status = LeadStatus.MARKED_SUITABLE,
            updatedAt = System.currentTimeMillis()
        )
        showToast("已标记合适")
    }

    private fun onUnsuitableClicked() {
        currentLead = currentLead.copy(
            suitable = false,
            status = LeadStatus.MARKED_UNSUITABLE,
            updatedAt = System.currentTimeMillis()
        )
        showToast("已标记不合适")
    }

    private fun onGenerateMessageClicked() {
        serviceScope.launch {
            val comment = currentLead.comment
            if (comment.isNullOrBlank()) {
                showToast("请先读取评论")
                return@launch
            }
            if (currentLead.suitable != true) {
                showToast("请先标记为合适。")
                return@launch
            }

            val settings = settingsRepository.settingsFlow.first()
            val message = MessageBuilder.build(comment, settings.fixedText)
            currentLead = currentLead.copy(
                message = message,
                status = LeadStatus.MESSAGE_GENERATED,
                updatedAt = System.currentTimeMillis()
            )
            recentLeadStore.saveMessage(message)
            updateExpandedView()
        }
    }

    private fun onCopyMessageClicked() {
        val message = currentLead.message
        if (message.isNullOrBlank()) {
            showToast("请先生成私信")
            return
        }

        clipboardHelper.copyText(message)
        currentLead = currentLead.copy(
            status = LeadStatus.MESSAGE_COPIED,
            updatedAt = System.currentTimeMillis()
        )
        showToast("已复制私信")
    }

    private fun onFillMessageClicked() {
        val message = currentLead.message
        if (message.isNullOrBlank()) {
            showToast("请先生成私信")
            return
        }

        val service = AccessibilityBridge.service
        if (service == null) {
            showToast("请先开启辅助功能权限，否则无法读取当前评论。")
            return
        }

        val ok = messageFiller.fillMessage(service.getRoot(), message)
        if (ok) {
            currentLead = currentLead.copy(
                status = LeadStatus.MESSAGE_FILLED,
                updatedAt = System.currentTimeMillis()
            )
            showToast("已填入私信框，请人工确认发送。")
        } else {
            clipboardHelper.copyText(message)
            showToast("自动填入失败，已复制私信，请手动粘贴。")
        }
    }

    private fun updateExpandedView(view: View? = currentView) {
        if (!expanded || view == null) return
        view.findViewById<TextView>(R.id.commentTextView).text =
            "当前评论：${currentLead.comment ?: "暂无"}"
        view.findViewById<TextView>(R.id.messageTextView).text =
            "私信：${currentLead.message ?: "暂无"}"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
