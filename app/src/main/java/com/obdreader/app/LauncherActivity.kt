package com.obdreader.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * What the home-screen icon opens: it hands straight over to the app's screen ([HomeActivity])
 * and closes, without showing anything. The icon is one of several launcher entries (one per icon
 * colour, see ui/AppIcons.kt), and Android closes any screen opened from an entry that's switched
 * off. Opening the screen from here, under its own name, means changing the icon's colour leaves
 * the app open.
 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, HomeActivity::class.java).apply {
            action = intent?.action
            intent?.categories?.forEach { addCategory(it) }
            intent?.extras?.let { putExtras(it) }
        })
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
