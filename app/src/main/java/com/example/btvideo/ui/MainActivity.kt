package com.example.btvideo.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.btvideo.R
import com.example.btvideo.util.PermissionHelper
import com.example.btvideo.util.ThemePrefs

class MainActivity : Activity() {
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var colorThemeButton: Button
    private lateinit var modeThemeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PermissionHelper.request(this)

        titleText = findViewById(R.id.titleText)
        subtitleText = findViewById(R.id.subtitleText)
        colorThemeButton = findViewById(R.id.colorThemeButton)
        modeThemeButton = findViewById(R.id.modeThemeButton)

        findViewById<Button>(R.id.serverButton).setOnClickListener {
            startActivity(Intent(this, ServerActivity::class.java))
        }
        findViewById<Button>(R.id.clientButton).setOnClickListener {
            startActivity(Intent(this, ClientActivity::class.java))
        }
        colorThemeButton.setOnClickListener {
            val next = ThemePrefs.toggleColor(this)
            Toast.makeText(this, "Tema: ${if (next == ThemePrefs.AZUL) "Azul ESCOM" else "Guinda IPN"}", Toast.LENGTH_SHORT).show()
            recreate()
        }
        modeThemeButton.setOnClickListener {
            ThemePrefs.toggleMode(this)
            Toast.makeText(this, "Modo: ${ThemePrefs.modeLabel(this)}", Toast.LENGTH_SHORT).show()
            recreate()
        }

        updateLabels()
    }

    private fun updateLabels() {
        titleText.text = "BT Video Player"
        subtitleText.text = "Tema actual: ${ThemePrefs.colorLabel(this)} · Modo: ${ThemePrefs.modeLabel(this)}"
        colorThemeButton.text = "Cambiar color: ${ThemePrefs.colorLabel(this)}"
        modeThemeButton.text = "Cambiar claro/oscuro: ${ThemePrefs.modeLabel(this)}"
    }
}
