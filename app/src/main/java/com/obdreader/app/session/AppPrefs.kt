package com.obdreader.app.session

import android.content.Context
import android.content.SharedPreferences
import com.obdreader.app.obd.VehicleCatalog

/** Settings shared by the phone screens, the car screen and the background connection. */
object AppPrefs {
    const val FILE = "obd_reader"

    const val THEME = "theme"
    const val APP_ICON = "app_icon"
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
    const val UNITS_PRESSURE = "units_pressure"
    /** The four Android Auto readings next to speed and engine speed (see CarReadings). */
    const val CAR_READINGS = "car_readings"
    /** Where the car was sold new (a Market name); absent means worked out from the phone's country. */
    const val MARKET = "car_market"
    const val USER_VIN = "user_vin"
    /** Battery checks at engine start (time, resting and cranking voltage): no location. */
    const val BATTERY_CHECKS = "battery_checks"
    /** "Watch the battery while the car is off": off unless the user turns it on. */
    const val BATTERY_WATCH = "battery_watch"
    /** The last night's battery readings (time and voltage only) and when the car was switched off. */
    const val BATTERY_NIGHT = "battery_night"
    const val BATTERY_NIGHT_OFF = "battery_night_off"
    const val USER_VIN_CAR = "user_vin_car"
    /** Every trouble code seen, per car ([com.obdreader.app.obd.CodeHistory]). */
    const val CODE_HISTORY = "code_history"
    /** How much of the VIN to show and share ([com.obdreader.app.obd.VinPrivacy]). */
    const val VIN_PRIVACY = "vin_privacy"

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

    fun batteryWatch(prefs: SharedPreferences): Boolean = prefs.getBoolean(BATTERY_WATCH, false)

    /** Loads the chosen units (km/h or mph, °C or °F) for display. */
    fun applyUnits(prefs: SharedPreferences) {
        com.obdreader.app.obd.Units.mph = mph(prefs)
        com.obdreader.app.obd.Units.fahrenheit = fahrenheit(prefs)
        com.obdreader.app.obd.Units.pressure = pressure(prefs)
        com.obdreader.app.obd.Market.current = market(prefs)
    }

    /** The chosen units, or until one is chosen, what the phone's country uses. */
    fun mph(prefs: SharedPreferences): Boolean = prefs.getBoolean(UNITS_MPH, com.obdreader.app.obd.Units.defaultMph(phoneCountry()))
    fun fahrenheit(prefs: SharedPreferences): Boolean = prefs.getBoolean(UNITS_FAHRENHEIT, com.obdreader.app.obd.Units.defaultFahrenheit(phoneCountry()))
    fun pressure(prefs: SharedPreferences): com.obdreader.app.obd.Units.Pressure =
        prefs.getString(UNITS_PRESSURE, null)?.let { com.obdreader.app.obd.Units.Pressure.from(it) } ?: com.obdreader.app.obd.Units.defaultPressure(phoneCountry())

    /** The phone's country, from its region setting. */
    fun phoneCountry(): String? = java.util.Locale.getDefault().country.takeIf { it.isNotEmpty() }

    /** The saved market, or the one for the phone's country. */
    fun market(prefs: SharedPreferences): com.obdreader.app.obd.Market =
        com.obdreader.app.obd.Market.byName(prefs.getString(MARKET, null)) ?: com.obdreader.app.obd.Market.fromCountry(phoneCountry())

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

    fun vinPrivacy(prefs: SharedPreferences): com.obdreader.app.obd.VinPrivacy.Mode =
        com.obdreader.app.obd.VinPrivacy.of(prefs.getString(VIN_PRIVACY, null))

    fun setUserVin(prefs: SharedPreferences, vin: String?) {
        if (vin == null) prefs.edit().remove(USER_VIN).remove(USER_VIN_CAR).apply()
        else prefs.edit().putString(USER_VIN, vin).putString(USER_VIN_CAR, savedCar(prefs)?.label).apply()
    }
}
