package com.livepet.app

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val btnOverlay = findViewById<Button>(R.id.btnOverlay)
        val btnUsage = findViewById<Button>(R.id.btnUsage)

        // 悬浮窗权限
        if (Settings.canDrawOverlays(this)) {
            btnOverlay.text = "悬浮窗权限：已开启 ✔"
        } else {
            btnOverlay.text = "开启悬浮窗权限"
        }
        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        // 使用情况访问权限
        updateUsageStatus(statusText)
        btnUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        // 启动悬浮窗服务
        val btnStart = findViewById<Button>(R.id.btnStart)
        btnStart.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, OverlayService::class.java))
                btnStart.text = "桌宠已启动，可返回桌面查看"
            } else {
                btnStart.text = "请先开启悬浮窗权限"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val statusText = findViewById<TextView>(R.id.statusText)
        updateUsageStatus(statusText)
    }

    private fun updateUsageStatus(tv: TextView) {
        val has = hasUsageAccess(this)
        tv.text = if (has) {
            "使用情况访问：已授权 ✔（能检测你刷的 app）"
        } else {
            "使用情况访问：未授权（点击下方按钮开启）"
        }
    }

    private fun hasUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }
}
