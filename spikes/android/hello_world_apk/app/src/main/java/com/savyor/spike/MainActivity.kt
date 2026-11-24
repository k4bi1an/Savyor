package com.savyor.spike

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
import android.widget.LinearLayout

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FAFAF8"))
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "SAVYOR ANDROID"
            textSize = 24f
            setTextColor(Color.parseColor("#1A1A1A"))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Feasibility & Toolchain Spike Verified"
            textSize = 14f
            setTextColor(Color.parseColor("#2F5D50"))
            setPadding(0, 16, 0, 32)
            gravity = Gravity.CENTER
        }

        val details = TextView(this).apply {
            text = "Android API 34\nADB, Fastboot & Emulation Ready\nStandalone Phone Architecture Verified"
            textSize = 12f
            setTextColor(Color.parseColor("#6B6B6B"))
            gravity = Gravity.CENTER
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(details)
        setContentView(layout)
    }
}
