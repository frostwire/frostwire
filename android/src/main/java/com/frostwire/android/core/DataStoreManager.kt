package com.frostwire.android.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.util.concurrent.ConcurrentHashMap

object DataStoreManager {

    private val dataStores = ConcurrentHashMap<String, DataStore<Preferences>>()

    @Volatile
    private var initialized = false

    @JvmStatic
    fun initialize(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        dataStores["settings_prefs"] = createDataStore(appContext, "settings_prefs")
        dataStores["service_prefs"] = createDataStore(appContext, "service_prefs")
        dataStores["notified_prefs"] = createDataStore(appContext, "notified_prefs")
        initialized = true
    }

    @get:JvmStatic
    val settingsDataStore: DataStore<Preferences>?
        get() = dataStores["settings_prefs"]

    @get:JvmStatic
    val serviceDataStore: DataStore<Preferences>?
        get() = dataStores["service_prefs"]

    @get:JvmStatic
    val notifiedDataStore: DataStore<Preferences>?
        get() = dataStores["notified_prefs"]

    @JvmStatic
    fun dataStore(context: Context, name: String): DataStore<Preferences> {
        return dataStores.getOrPut(name) { createDataStore(context.applicationContext, name) }
    }

    private fun createDataStore(context: Context, name: String): DataStore<Preferences> {
        return PreferenceDataStoreFactory.createWithPath(
            produceFile = { context.preferencesDataStoreFile(name).absolutePath.toPath() }
        )
    }

    @JvmStatic
    fun getString(dataStore: DataStore<Preferences>, key: String, defaultValue: String?): String? {
        return runBlocking { dataStore.data.first()[stringPreferencesKey(key)] ?: defaultValue }
    }

    @JvmStatic
    fun getInt(dataStore: DataStore<Preferences>, key: String, defaultValue: Int): Int {
        return runBlocking { dataStore.data.first()[intPreferencesKey(key)] ?: defaultValue }
    }

    @JvmStatic
    fun getLong(dataStore: DataStore<Preferences>, key: String, defaultValue: Long): Long {
        return runBlocking { dataStore.data.first()[longPreferencesKey(key)] ?: defaultValue }
    }

    @JvmStatic
    fun getBoolean(dataStore: DataStore<Preferences>, key: String, defaultValue: Boolean): Boolean {
        return runBlocking { dataStore.data.first()[booleanPreferencesKey(key)] ?: defaultValue }
    }

    @JvmStatic
    fun putString(dataStore: DataStore<Preferences>, key: String, value: String) {
        runBlocking { dataStore.edit { it[stringPreferencesKey(key)] = value } }
    }

    @JvmStatic
    fun putInt(dataStore: DataStore<Preferences>, key: String, value: Int) {
        runBlocking { dataStore.edit { it[intPreferencesKey(key)] = value } }
    }

    @JvmStatic
    fun putLong(dataStore: DataStore<Preferences>, key: String, value: Long) {
        runBlocking { dataStore.edit { it[longPreferencesKey(key)] = value } }
    }

    @JvmStatic
    fun putBoolean(dataStore: DataStore<Preferences>, key: String, value: Boolean) {
        runBlocking { dataStore.edit { it[booleanPreferencesKey(key)] = value } }
    }

    @JvmStatic
    fun containsKey(dataStore: DataStore<Preferences>, key: String): Boolean {
        return runBlocking {
            dataStore.data.first().asMap().keys.any { it.name == key }
        }
    }
}