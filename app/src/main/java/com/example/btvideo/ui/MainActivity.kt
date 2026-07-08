package com.example.btvideo.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.app.Activity
import com.example.btvideo.R
import com.example.btvideo.util.PermissionHelper
import com.example.btvideo.util.ThemePrefs

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PermissionHelper.request(this)

        findViewById<Button>(R.id.serverButton).setOnClickListener {
            startActivity(Intent(this, ServerActivity::class.java))
        }
        findViewById<Button>(R.id.clientButton).setOnClickListener {
            startActivity(Intent(this, ClientActivity::class.java))
        }
        findViewById<Button>(R.id.themeButton).setOnClickListener {
            val next = ThemePrefs.toggle(this)
            Toast.makeText(this, "Tema: $next", Toast.LENGTH_SHORT).show()
            recreate()
        }
    }
}
