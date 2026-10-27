package com.obdreader.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * What the home-screen icon opens: it hands straight over to the app's screen ([HomeActivity])
 * and closes, without showing anything. The icon is one of several launcher entries (one per icon
 * colour, see ui/AppIcons.kt), and Android closes any screen opened from an entry that's switched
 * off, along with the task it opened. So the screen opens here in a task of its own (its own task
 * affinity), under its own name: changing the icon's colour then leaves the app open. Opening it
 * again from the icon brings that task back, as usual.
 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, HomeActivity::class.java).apply {
            action = intent?.action
            intent?.categories?.forEach { addCategory(it) }
            intent?.extras?.let { putExtras(it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
