package com.obdreader.app.car

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/** Entry point Android Auto binds to. */
class ObdCarAppService : CarAppService() {

    // The Car App Library's published list of Android Auto hosts, as its documentation recommends.
    @SuppressLint("PrivateResource")
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            // Android Auto and Android Automotive OS hosts, as published by the Car App Library.
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen {
            // Plugging the phone into the car usually means the ignition is on: connect straight away.
            com.obdreader.app.session.DriveScorer.start()
            com.obdreader.app.session.ObdSession.autoConnect(carContext)
            return DashboardScreen(carContext)
        }
    }
}
