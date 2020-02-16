package com.example.autowallpaper.helper

import android.content.Context
import android.content.SharedPreferences

class PrefHelper(private val prefName: String = defaultPrefName) {

    private var pref: SharedPreferences? = null

    fun put(key: String, value: String): PrefHelper {
        getPref()!!.edit().putString(key, value).apply()
        return this
    }

    fun put(key: String, value: Int): PrefHelper {
        getPref()!!.edit().putInt(key, value).apply()
        return this
    }

    fun put(key: String, value: Boolean): PrefHelper {
        getPref()!!.edit().putBoolean(key, value).apply()
        return this
    }

    fun put(key: String, value: Float): PrefHelper {
        getPref()!!.edit().putFloat(key, value).apply()
        return this
    }

    fun put(key: String, value: Long): PrefHelper {
        getPref()!!.edit().putLong(key, value).apply()
        return this
    }

    fun getInt(key: String, defValue: Int = 0): Int {
        return getPref()!!.getInt(key, defValue)
    }

    fun getLong(key: String, defValue: Long = 0L): Long {
        return getPref()!!.getLong(key, defValue)
    }

    fun getString(key: String, defValue: String = ""): String {
        return getPref()!!.getString(key, defValue) ?: defValue
    }

    fun getBoolean(key: String, defValue: Boolean = false): Boolean {
        return getPref()!!.getBoolean(key, defValue)
    }

    fun remove(key: String) {
        getPref()!!.edit().remove(key).apply()
    }

    fun clear() {
        getPref()!!.edit().clear().apply()
    }

    private fun getPref(): SharedPreferences? {
        if (pref == null) {
            pref = GlobalApplication.instance.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        }
        return pref
    }

    companion object {

        private const val defaultPrefName = "config.pref"

        @JvmOverloads
        operator fun get(prefName: String = defaultPrefName): PrefHelper {
            return PrefHelper(prefName)
        }
    }
}
