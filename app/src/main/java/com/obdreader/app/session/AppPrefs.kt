package com.obdreader.app.session

import android.content.Context
import android.content.SharedPreferences
import com.obdreader.app.obd.VehicleCatalog

/** Settings shared by the phone screens, the car screen and the background connection. */
object AppPrefs {
    const val FILE = "obd_reader"

    const val THEME = "theme"
    const val PROTOCOL = "protocol"
    const val LAST_PROTOCOL = "last_protocol"
    const val CAR_MAKE = "car_make"
    const val CAR_YEAR = "car_year"
    const val CAR_MODEL = "car_model"
    const val CAR_ENGINE = "car_engine"
    const val LAST_TYPE = "last_type"
    const val LAST_ADDRESS = "last_address"
    const val LAST_NAME = "last_name"
    const val HOST = "wifi_host"
    const val PORT = "wifi_port"
    const val AUTO_RECONNECT = "auto_reconnect"
    const val ALERTS = "alerts"
    const val UNITS_MPH = "units_mph"
    const val UNITS_FAHRENHEIT = "units_fahrenheit"
    const val USER_VIN = "user_vin"
    /** Battery checks at engine start (time, resting and cranking voltage): no location. */
    const val BATTERY_CHECKS = "battery_checks"
    const val USER_VIN_CAR = "user_vin_car"

    const val TYPE_CLASSIC = "classic"
    const val TYPE_BLE = "ble"
    const val TYPE_WIFI = "wifi"
    const val DEFAULT_HOST = "192.168.0.10"
    const val DEFAULT_PORT = 35000

    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** "Reconnect automatically": on unless the user turns it off. */
    fun autoReconnect(prefs: SharedPreferences): Boolean = prefs.getBoolean(AUTO_RECONNECT, true)

    fun alerts(prefs: SharedPreferences): Boolean = prefs.getBoolean(ALERTS, true)

    /** Loads the chosen units (km/h or mph, °C or °F) for display. */
    fun applyUnits(prefs: SharedPreferences) {
        com.obdreader.app.obd.Units.mph = prefs.getBoolean(UNITS_MPH, false)
        com.obdreader.app.obd.Units.fahrenheit = prefs.getBoolean(UNITS_FAHRENHEIT, false)
    }

    fun savedCar(prefs: SharedPreferences): VehicleCatalog.Car? {
        val make = prefs.getString(CAR_MAKE, null) ?: return null
        val year = prefs.getInt(CAR_YEAR, 0).takeIf { it > 0 } ?: return null
        return VehicleCatalog.Car(make, year, prefs.getString(CAR_MODEL, null), prefs.getString(CAR_ENGINE, null))
    }

    /**
     * The VIN the user typed in from their VIN plate, if any. It belongs to the car that was chosen
     * when it was entered, so choosing a different car stops using it.
     */
    fun userVin(prefs: SharedPreferences): String? {
        val vin = prefs.getString(USER_VIN, null) ?: return null
        val car = savedCar(prefs)?.label
        return if (car == prefs.getString(USER_VIN_CAR, null)) vin else null
    }

    fun setUserVin(prefs: SharedPreferences, vin: String?) {
        if (vin == null) prefs.edit().remove(USER_VIN).remove(USER_VIN_CAR).apply()
        else prefs.edit().putString(USER_VIN, vin).putString(USER_VIN_CAR, savedCar(prefs)?.label).apply()
    }
}
