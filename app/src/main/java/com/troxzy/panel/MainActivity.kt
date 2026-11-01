package com.troxzy.panel

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {
    private lateinit var rootStatus: TextView
    private lateinit var overlayButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 13, 20)
        window.navigationBarColor = Color.rgb(9, 13, 20)
        buildLandingPage()
    }

    override fun onResume() {
        super.onResume()
        if (::overlayButton.isInitialized) refreshPermissionState()
        refreshRootStatus()
    }

    private fun buildLandingPage() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(9, 13, 20))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(content)

        content.addView(label("TR∅XZY", 32f, Color.rgb(242, 246, 250), true))
        content.addView(label("SYSTEM CONTROL  /  APP PROFILE", 11f, Color.rgb(114, 224, 193), true),
            LinearLayout.LayoutParams(-1, dp(30)))
        content.addView(label("A precise, floating workspace for monitoring this app's runtime profile.", 16f,
            Color.rgb(160, 173, 186), false).apply { setPadding(0, dp(4), 0, dp(24)) })

        val deviceCard = card()
        deviceCard.addView(label("DEVICE PROFILE", 11f, Color.rgb(114, 224, 193), true))
        deviceCard.addView(label("${Build.MANUFACTURER} ${Build.MODEL}", 19f, Color.rgb(242, 246, 250), true).apply {
            setPadding(0, dp(10), 0, dp(4))
        })
        deviceCard.addView(label("ABI  ·  ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}\nAndroid ${Build.VERSION.RELEASE}  ·  API ${Build.VERSION.SDK_INT}",
            13f, Color.rgb(160, 173, 186), false))
        content.addView(deviceCard, marginParams(bottom = 14))

        val privilegeCard = card()
        privilegeCard.addView(label("PRIVILEGE CHECK", 11f, Color.rgb(114, 224, 193), true))
        rootStatus = label("Checking native diagnostics…", 14f, Color.rgb(160, 173, 186), false).apply {
            setPadding(0, dp(12), 0, 0)
        }
        privilegeCard.addView(rootStatus)
        content.addView(privilegeCard, marginParams(bottom = 14))

        val infoCard = card()
        infoCard.addView(label("SAFE BY DESIGN", 11f, Color.rgb(145, 168, 255), true))
        infoCard.addView(label(
            "Controls apply only to TR∅XZY's own app-local profile. Root detection is informational; this app does not execute superuser commands or modify other processes.",
            13f, Color.rgb(160, 173, 186), false
        ).apply { setPadding(0, dp(10), 0, 0) })
        content.addView(infoCard, marginParams(bottom = 20))

        overlayButton = Button(this).apply {
            text = "Grant overlay access"
            isAllCaps = false
            setTextColor(Color.rgb(9, 13, 20))
            background = rounded(Color.rgb(114, 224, 193), 16)
            setOnClickListener { onOverlayButtonClicked() }
        }
        content.addView(overlayButton, LinearLayout.LayoutParams(-1, dp(54)))
        content.addView(label("The floating panel starts only after you tap the button.", 12f,
            Color.rgb(126, 140, 155), false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
        page.addView(scroll, ViewGroup.LayoutParams(-1, -1))
        setContentView(page)
    }

    private fun refreshPermissionState() {
        val granted = Settings.canDrawOverlays(this)
        overlayButton.text = if (granted) "Launch overlay menu" else "Grant overlay access"
        overlayButton.isEnabled = true
    }

    private fun onOverlayButtonClicked() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        val serviceIntent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
        overlayButton.text = "Overlay menu is running"
    }

    private fun refreshRootStatus() {
        if (!::rootStatus.isInitialized) return
        val rootFound = NativeBridge.rootAvailable()
        val native = if (NativeBridge.isLoaded) "Native diagnostics online" else "Native library unavailable"
        rootStatus.text = "$native\nSuperuser executable: ${if (rootFound) "detected (not invoked)" else "not detected"}"
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(17))
        background = rounded(Color.rgb(17, 25, 37), 20, Color.rgb(44, 58, 74))
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        includeFontPadding = true
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    private fun marginParams(bottom: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 42
    }
}
