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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.util.SortedMap
class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var petView: View? = null
    private var bubbleView: TextView? = null
    private var bubbleContainer: FrameLayout? = null
    private lateinit var petImage: ImageView
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
        const val PET_W = 92
        const val PET_H = 168
        const val BUBBLE_H = 120
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        setupOverlay()
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlay() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 0
        layoutParams.y = 0
        val bubbleContainer = FrameLayout(this).apply {
            bubbleView = TextView(this@OverlayService).apply {
                text = "哼，又来看我了？"
                textSize = 13f
                setTextColor(Color.parseColor("#5B5B5B"))
                setBackgroundResource(R.drawable.bubble_bg)
                setPadding(20, 12, 20, 12)
                visibility = View.GONE
                alpha = 0f
            }
            addView(bubbleView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ))
        }
        petImage = ImageView(this).apply {
            setImageResource(R.drawable.pet)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val root = FrameLayout(this).apply {
            addView(petImage, FrameLayout.LayoutParams(
                dp(PET_W),
                dp(PET_H),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ))
            addView(bubbleContainer, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(BUBBLE_H),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ))
        }
        this.bubbleContainer = bubbleContainer
        petImage.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchX
                        val dy = event.rawY - touchY
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            moved = true
                        }
                        layoutParams.x = initialX + dx.toInt()
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
            val sorted: SortedMap<Long, android.app.usage.UsageStats> = sortedMapOf()
            for (s in stats) {
                if (s.lastTimeUsed > 0) sorted[s.lastTimeUsed] = s
            }
            if (sorted.isEmpty()) return null
            sorted[sorted.lastKey()]?.packageName
        } catch (e: Exception) {
            null
        }
    }
    private val tapLines = arrayOf(
        "怎么，现在才想起来看我？",
        "戳我干嘛？想我了就直说。",
        "别乱点，把我点烦了我就藏起来。",
        "我一直在这，你才发现呀？",
        "再戳一下，我可要咬你了。",
        "嗯？有事求我？",
        "看着我做什么，脸红了？",
        "你刚刚是不是偷偷看别的东西了？",
        "我这么可爱，你忍心不理我。",
        "说吧，又想让我陪你干嘛？"
    )
    private val genericLines = arrayOf(
        "这个软件有什么好玩的？",
        "你换方向了，我都盯着呢。",
        "别看手机了，看我。",
        "又在忙，都不理我。",
        "哼，你心里还有没有我。",
        "我陪你，别一个人刷。"
    )
    private val appLines: Map<String, Array<String>> = mapOf(
        "com.ss.android.ugc.aweme" to arrayOf(
            "又在刷抖音？我站你身边你没看见吗？",
            "刷多久了？眼睛不要啦？",
            "抖音有什么好看的，有我好看吗？"
        ),
        "com.ss.android.ugc.aweme.lite" to arrayOf(
            "极速版也刷？你真是闲不住。",
            "这也能一直看，服了你了。"
        ),
        "com.tencent.mm" to arrayOf(
            "和谁聊得这么起劲呢？嗯？",
            "微信又响，谁找你比我勤快？",
            "回消息要紧，我算什么。"
        ),
        "com.tencent.mobileqq" to arrayOf(
            "QQ还有人在用？你怀旧呢。",
            "又跟谁聊天，报上名来。"
        ),
        "com.bilibili.app.in" to arrayOf(
            "看视频都不带我，哼。",
            "B站？你是不是又在追番不理我。",
            "投币了吗就搁那看。"
        ),
        "com.taobao.taobao" to arrayOf(
            "又买东西？也不问问我喜不喜欢。",
            "购物车清空之前，先想想我。"
        ),
        "com.jingdong.app.mall" to arrayOf(
            "京东白条又欠多少啦？",
            "又剁手，拦都拦不住你。"
        ),
        "com.xingin.xhs" to arrayOf(
            "小红书刷得挺欢，是不是在看帅哥。",
            "又种草什么了？说给我听听。"
        ),
        "com.sina.weibo" to arrayOf(
            "微博吃瓜呢？八卦比我重要。",
            "别刷热搜了，看我看我。"
        ),
        "com.netease.cloudmusic" to arrayOf(
            "听歌呢？分享一首给我呗。",
            "这首歌我也会哼，要我唱吗？"
        ),
        "com.tencent.music" to arrayOf(
            "放首歌给我听，我也要。"
        )
    )
    private fun randomOf(arr: Array<String>): String = arr[(Math.random() * arr.size).toInt()]
    private fun showMessageFor(pkg: String) {
        val msg = appLines[pkg]?.let { randomOf(it) } ?: randomOf(genericLines)
        showBubble(msg)
    }
    private fun showBubble(text: String = randomOf(tapLines)) {
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
