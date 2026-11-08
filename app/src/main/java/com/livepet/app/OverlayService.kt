package com.livepet.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import java.util.SortedMap

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var petView: View? = null
    private var bubbleView: TextView? = null
    private var bubbleContainer: FrameLayout? = null
    private lateinit var webView: WebView

    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundPkg: String? = null

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, 1500)
        }
    }

    companion object {
        const val CHANNEL_ID = "livepet_fgs"
        const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        setupOverlay()
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun setupOverlay() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.BOTTOM or Gravity.END
        layoutParams.x = 0
        layoutParams.y = 0

        // 气泡台词
        bubbleContainer = FrameLayout(this).apply {
            bubbleView = TextView(this@OverlayService).apply {
                text = "哼，又来看我了？"
                textSize = 14f
                setTextColor(Color.parseColor("#5B5B5B"))
                setBackgroundResource(R.drawable.bubble_bg)
                setPadding(28, 18, 28, 18)
                visibility = View.GONE
                alpha = 0f
            }
            addView(bubbleView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ))
        }

        // 透明 WebView 加载角色（触摸穿透，交给外层 root 处理拖拽/点击）
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
            webViewClient = WebViewClient()
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isClickable = false
            isFocusable = false
            setOnTouchListener { _, _ -> false }
            loadUrl("file:///android_asset/pet.html")
        }

        val root = FrameLayout(this).apply {
            addView(bubbleContainer, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ))
            addView(webView, FrameLayout.LayoutParams(
                dp(120),
                dp(220),
                Gravity.BOTTOM or Gravity.END
            ))
        }

        // 拖拽
        root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var isDragging = false
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        isDragging = false
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchX
                        val dy = event.rawY - touchY
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            isDragging = true
                            moved = true
                        }
                        layoutParams.x = initialX - dx.toInt()
                        layoutParams.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(root, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            showBubble()
                        }
                        return true
                    }
                }
                return false
            }
        })

        petView = root
        windowManager.addView(root, layoutParams)
        handler.post(checkRunnable)
    }

    private fun checkForegroundApp() {
        val pkg = getForegroundPackage()
        if (pkg != lastForegroundPkg) {
            lastForegroundPkg = pkg
            pkg?.let { showMessageFor(it) }
        }
    }

    private fun getForegroundPackage(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats: List<android.app.usage.UsageStats> =
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
                    ?: return null
            if (stats.isEmpty()) return null
            val sorted: SortedMap<Long, android.app.usage.UsageStats> =
                sortedMapOf()
            for (s in stats) {
                if (s.lastTimeUsed > 0) sorted[s.lastTimeUsed] = s
            }
            if (sorted.isEmpty()) return null
            sorted[sorted.lastKey()]?.packageName
        } catch (e: Exception) {
            null
        }
    }

    private fun showMessageFor(pkg: String) {
        val msg = when (pkg) {
            "com.ss.android.ugc.aweme" -> "又在刷抖音？我站你身边你没看见吗？"
            "com.tencent.mm", "com.tencent.mobileqq" -> "和谁聊得这么起劲呢？嗯？"
            "com.bilibili.app.in" -> "看视频都不带我，哼。"
            "com.taobao.taobao", "com.jingdong.app.mall" -> "又买东西？也不问问我喜不喜欢。"
            else -> null
        }
        msg?.let { showBubble(it) }
    }

    private fun showBubble(text: String = "怎么，现在才想起来看我？") {
        bubbleView?.let {
            it.text = text
            it.visibility = View.VISIBLE
            it.alpha = 0f
            it.animate().alpha(1f).setDuration(200).start()
            handler.postDelayed({
                it.animate().alpha(0f).setDuration(400).withEndAction {
                    it.visibility = View.GONE
                }.start()
            }, 3500)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "桌面宠物", NotificationManager.IMPORTANCE_MIN
            )
            nm.createNotificationChannel(channel)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("桌宠运行中")
                .setContentText("裴羡辞在看着你哦")
                .setSmallIcon(R.drawable.pet)
                .setContentIntent(pi)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("桌宠运行中")
                .setContentText("裴羡辞在看着你哦")
                .setSmallIcon(R.drawable.pet)
                .setContentIntent(pi)
                .build()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        petView?.let { windowManager.removeView(it) }
        petView = null
        super.onDestroy()
    }
}