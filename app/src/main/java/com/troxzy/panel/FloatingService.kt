package com.troxzy.panel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private var launcher: TextView? = null
    private var dashboard: LinearLayout? = null
    private var logView: TextView? = null
    private var runtimeView: TextView? = null
    private var expanded = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startPanelForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (launcher == null) addLauncher()
        log("Floating controls ready · app-local profile")
        return START_STICKY
    }

    private fun startPanelForeground() {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("TR∅XZY controls active")
            .setContentText("Tap the floating badge to open your app profile.")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun addLauncher() {
        val badge = TextView(this).apply {
            text = "T∅"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(9, 13, 20))
            background = rounded(Color.rgb(114, 224, 193), 28, Color.WHITE)
            elevation = dp(10).toFloat()
            contentDescription = "Open TR∅XZY floating controls"
        }
        val params = WindowManager.LayoutParams(
            dp(58), dp(58), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(150)
        }
        attachDraggableLauncher(badge, params)
        windowManager.addView(badge, params)
        launcher = badge
    }

    private fun attachDraggableLauncher(view: TextView, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > dp(5) || abs(dy) > dp(5)) dragged = true
                    if (dragged) {
                        params.x = startX + dx
                        params.y = startY + dy
                        try { windowManager.updateViewLayout(view, params) }
                        catch (_: IllegalArgumentException) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) toggleDashboard()
                    true
                }
                else -> true
            }
        }
    }

    private fun toggleDashboard() {
        if (expanded) removeDashboard() else showDashboard()
    }

    private fun showDashboard() {
        val existing = dashboard
        if (existing != null) {
            expanded = true
            existing.visibility = View.VISIBLE
            return
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.argb(246, 14, 21, 31), 22, Color.rgb(62, 83, 101))
            elevation = dp(18).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text("TR∅XZY", 19f, Color.rgb(242, 246, 250), true))
        heading.addView(text("FLOATING APP PROFILE", 9f, Color.rgb(114, 224, 193), true))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(smallButton("—") { removeDashboard() }, LinearLayout.LayoutParams(dp(42), dp(38)))
        header.addView(smallButton("×") { stopSelf() }, LinearLayout.LayoutParams(dp(42), dp(38)).apply {
            leftMargin = dp(6)
        })
        panel.addView(header)
        panel.addView(View(this).apply { setBackgroundColor(Color.rgb(48, 63, 79)) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(10) })

        val scroll = ScrollView(this).apply { isFillViewport = false; overScrollMode = View.OVER_SCROLL_NEVER }
        val sections = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(sections)

        val performance = section("01", "PERFORMANCE TWEAKS", true)
        addSwitch(performance, "Frame pacing", "App-local rendering profile", "frame_pacing", false)
        addSwitch(performance, "Adaptive cache", "Manage this app's cache policy", "adaptive_cache", false)
        addSlider(performance, "Profile intensity", 50)
        sections.addView(performance, sectionParams())

        val memory = section("02", "MEMORY MANAGER", false)
        bodyOf(memory).addView(text(
            "Profile controls are scoped to TR∅XZY. No other process memory is read or changed.",
            12f, Color.rgb(160, 173, 186), false
        ).apply { setPadding(dp(2), dp(10), dp(2), dp(2)) })
        sections.addView(memory, sectionParams())

        val monitor = section("03", "STATUS MONITOR", false)
        addSwitch(monitor, "Runtime telemetry", "Show local diagnostic status", "telemetry", true)
        runtimeView = text(NativeBridge.runtimeInfo(), 11f, Color.rgb(160, 173, 186), false).apply {
            setPadding(dp(2), dp(8), dp(2), dp(4))
        }
        bodyOf(monitor).addView(runtimeView)
        bodyOf(monitor).addView(smallButton("Refresh runtime status") { updateRuntimeInfo() },
            LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(8) })
        sections.addView(monitor, sectionParams())

        val scrollHeight = (resources.displayMetrics.heightPixels * 0.48f)
            .toInt().coerceAtMost(dp(430)).coerceAtLeast(dp(260))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, scrollHeight))
        panel.addView(text("LIVE LOG", 9f, Color.rgb(114, 224, 193), true).apply {
            setPadding(dp(2), dp(10), 0, dp(3))
        })
        logView = text("Waiting for interaction…", 10f, Color.rgb(160, 173, 186), false).apply {
            maxLines = 3
            setPadding(dp(2), 0, dp(2), dp(2))
        }
        panel.addView(logView)

        val displayWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val panelWidth = dp(minOf(350, displayWidthDp - 28).coerceAtLeast(280))
        val params = WindowManager.LayoutParams(
            panelWidth, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(14)
            y = dp(92)
        }
        windowManager.addView(panel, params)
        dashboard = panel
        expanded = true
        panel.alpha = 0f
        panel.translationY = -dp(12).toFloat()
        panel.animate().alpha(1f).translationY(0f).setDuration(180).start()
        log("Dashboard expanded")
    }

    private fun section(number: String, title: String, initiallyExpanded: Boolean): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.argb(150, 25, 36, 51), 16, Color.rgb(48, 63, 79))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (initiallyExpanded) View.VISIBLE else View.GONE
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
        }
        heading.addView(text("$number  /  $title", 11f, Color.rgb(226, 234, 241), true),
            LinearLayout.LayoutParams(0, dp(28), 1f))
        val indicator = text(if (initiallyExpanded) "−" else "+", 18f, Color.rgb(114, 224, 193), true).apply {
            gravity = Gravity.CENTER
        }
        heading.addView(indicator, LinearLayout.LayoutParams(dp(28), dp(28)))
        heading.setOnClickListener {
            val show = body.visibility != View.VISIBLE
            body.visibility = if (show) View.VISIBLE else View.GONE
            indicator.text = if (show) "−" else "+"
        }
        container.addView(heading)
        container.addView(body)
        container.tag = body
        return container
    }

    private fun bodyOf(section: LinearLayout): LinearLayout = section.tag as LinearLayout

    private fun addSwitch(section: LinearLayout, title: String, subtitle: String, key: String, checked: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(2))
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(title, 13f, Color.rgb(242, 246, 250), true))
        labels.addView(text(subtitle, 10f, Color.rgb(144, 158, 173), false).apply {
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(SwitchMaterial(this).apply {
            isChecked = checked
            contentDescription = title
            setOnCheckedChangeListener { _, enabled ->
                val result = NativeBridge.setFeature(key, enabled)
                log("$title ${if (enabled) "on" else "off"} · $result")
                updateRuntimeInfo()
            }
        })
        bodyOf(section).addView(row)
    }

    private fun addSlider(section: LinearLayout, title: String, start: Int) {
        val label = text("$title  ·  $start%", 12f, Color.rgb(226, 234, 241), true)
        bodyOf(section).addView(label, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val seek = SeekBar(this).apply {
            max = 100
            progress = start
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(114, 224, 193))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.rgb(114, 224, 193))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    label.text = "$title  ·  $progress%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val value = seekBar?.progress ?: start
                    log(NativeBridge.setProfileLevel(value))
                    updateRuntimeInfo()
                }
            })
        }
        bodyOf(section).addView(seek, LinearLayout.LayoutParams(-1, dp(38)))
    }

    private fun updateRuntimeInfo() {
        runtimeView?.text = NativeBridge.runtimeInfo()
    }

    private fun log(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val previous = logView?.text?.toString().orEmpty().lines().filter { it.isNotBlank() }.takeLast(2)
        logView?.text = (previous + "$stamp  $message").joinToString("\n")
    }

    private fun removeDashboard() {
        val panel = dashboard ?: return
        expanded = false
        panel.animate().alpha(0f).translationY(-dp(8).toFloat()).setDuration(120).withEndAction {
            try { windowManager.removeView(panel) } catch (_: IllegalArgumentException) { }
            if (dashboard === panel) {
                dashboard = null
                logView = null
                runtimeView = null
            }
        }.start()
    }

    private fun smallButton(title: String, action: () -> Unit): Button = Button(this).apply {
        text = title
        textSize = 11f
        isAllCaps = false
        setTextColor(Color.rgb(218, 229, 238))
        background = rounded(Color.rgb(34, 47, 63), 12, Color.rgb(55, 73, 91))
        setPadding(dp(6), 0, dp(6), 0)
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        includeFontPadding = true
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun sectionParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(8)
    }

    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_description)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        dashboard?.let { panel ->
            try { windowManager.removeView(panel) } catch (_: IllegalArgumentException) { }
        }
        launcher?.let { badge ->
            try { windowManager.removeView(badge) } catch (_: IllegalArgumentException) { }
        }
        dashboard = null
        launcher = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "troxzy_floating_controls"
        private const val NOTIFICATION_ID = 701
    }
}
