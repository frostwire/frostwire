package com.frostwire.android.core

import androidx.preference.PreferenceDataStore

class FrostwirePreferenceDataStore : PreferenceDataStore() {

    override fun getString(key: String, defValue: String?): String? {
        return ConfigurationRepository.getString(key, defValue)
    }

    override fun putString(key: String, value: String?) {
        if (value != null) {
            ConfigurationRepository.setString(key, value)
        }
    }

    override fun getInt(key: String, defValue: Int): Int {
        return ConfigurationRepository.getInt(key, defValue)
    }

    override fun putInt(key: String, value: Int) {
        ConfigurationRepository.setInt(key, value)
    }

    override fun getLong(key: String, defValue: Long): Long {
        return ConfigurationRepository.getLong(key, defValue)
    }

    override fun putLong(key: String, value: Long) {
        ConfigurationRepository.setLong(key, value)
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return ConfigurationRepository.getBoolean(key, defValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        ConfigurationRepository.setBoolean(key, value)
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        val arr = ConfigurationRepository.getStringArray(key) ?: return defValues
        return arr.toMutableSet()
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        if (values != null) {
            ConfigurationRepository.setStringArray(key, values.toTypedArray())
        }
    }
}
