package com.frostwire.android.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.preference.PreferenceManager
import com.andrew.apollo.MusicPlaybackService
import com.frostwire.util.Hex
import com.frostwire.util.JsonUtils
import com.frostwire.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ConfigurationRepository {

    private val LOG = Logger.getLogger(ConfigurationRepository::class.java)

    private lateinit var dataStore: DataStore<Preferences>

    private val cache = ConcurrentHashMap<String, Any?>()

    private val listeners = CopyOnWriteArrayList<OnPreferenceChangeListener>()

    @Volatile
    private var initialized = false

    private val initLatch = CountDownLatch(1)

    private val defaults: Map<String, Any> by lazy { buildDefaults() }

    private val volatileKeys: Set<String> by lazy { buildVolatileKeys() }

    interface OnPreferenceChangeListener {
        fun onPreferenceChanged(key: String)
    }

    @JvmStatic
    fun initialize(context: Context) {
        if (initialized) return
        FrostwirePreferences.initialize(context)
        dataStore = FrostwirePreferences.dataStore
        migrateFromSharedPreferences(context)
        loadFromDataStore()
        seedDefaults()
        resetVolatileKeys()
        initialized = true
        initLatch.countDown()
    }

    @JvmStatic
    fun awaitInitialization(timeoutSeconds: Long = 4) {
        if (initialized) return
        try {
            initLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            LOG.warn("ConfigurationRepository initialization interrupted", e)
        }
    }

    @JvmStatic
    fun getString(key: String, defValue: String? = null): String? {
        val cached = cache[key]
        return when {
            cached != null -> cached as String
            defValue != null -> defValue
            else -> defaults[key] as? String
        }
    }

    @JvmStatic
    fun getString(key: String): String? = getString(key, null)

    @JvmStatic
    fun setString(key: String, value: String) {
        cache[key] = value
        persistAsync(key, value)
        notifyListeners(key)
    }

    @JvmStatic
    fun getInt(key: String, defValue: Int = 0): Int {
        val cached = cache[key]
        return when {
            cached != null -> {
                when (cached) {
                    is Int -> cached
                    is Long -> cached.toInt()
                    is String -> cached.toIntOrNull() ?: defValue
                    else -> defValue
                }
            }
            key in defaults -> (defaults[key] as? Number)?.toInt() ?: defValue
            else -> defValue
        }
    }

    @JvmStatic
    fun getInt(key: String): Int = getInt(key, 0)

    @JvmStatic
    fun setInt(key: String, value: Int) {
        cache[key] = value
        persistAsync(key, value)
        notifyListeners(key)
    }

    @JvmStatic
    fun getLong(key: String, defValue: Long = 0L): Long {
        val cached = cache[key]
        return when {
            cached != null -> {
                when (cached) {
                    is Long -> cached
                    is Int -> cached.toLong()
                    is String -> cached.toLongOrNull() ?: defValue
                    else -> defValue
                }
            }
            key in defaults -> (defaults[key] as? Number)?.toLong() ?: defValue
            else -> defValue
        }
    }

    @JvmStatic
    fun getLong(key: String): Long = getLong(key, 0L)

    @JvmStatic
    fun setLong(key: String, value: Long) {
        cache[key] = value
        persistAsync(key, value)
        notifyListeners(key)
    }

    @JvmStatic
    fun getBoolean(key: String, defValue: Boolean = false): Boolean {
        val cached = cache[key]
        return when {
            cached != null -> cached as Boolean
            key in defaults -> defaults[key] as? Boolean ?: defValue
            else -> defValue
        }
    }

    @JvmStatic
    fun getBoolean(key: String): Boolean = getBoolean(key, false)

    @JvmStatic
    fun setBoolean(key: String, value: Boolean) {
        cache[key] = value
        persistAsync(key, value)
        notifyListeners(key)
    }

    @JvmStatic
    fun getStringArray(key: String): Array<String>? {
        val s = getString(key) ?: return null
        return try {
            JsonUtils.toObject(s, Array<String>::class.java)
        } catch (e: Throwable) {
            LOG.warn("getStringArray(key=$key) failed", e)
            null
        }
    }

    @JvmStatic
    fun setStringArray(key: String, values: Array<String>) {
        setString(key, JsonUtils.toJson(values))
    }

    @JvmStatic
    fun containsKey(key: String): Boolean = cache.containsKey(key) || defaults.containsKey(key)

    @JvmStatic
    fun addListener(listener: OnPreferenceChangeListener) {
        listeners.add(listener)
    }

    @JvmStatic
    fun removeListener(listener: OnPreferenceChangeListener) {
        listeners.remove(listener)
    }

    @JvmStatic
    fun resetToDefaults() {
        for ((key, value) in defaults) {
            setDefault(key, value)
        }
    }

    @JvmStatic
    fun getDefaultValues(): Map<String, Any> = defaults

    private fun notifyListeners(key: String) {
        for (listener in listeners) {
            try {
                listener.onPreferenceChanged(key)
            } catch (e: Throwable) {
                LOG.warn("Listener error for key=$key", e)
            }
        }
    }

    private fun persistAsync(key: String, value: Any?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dataStore.edit { prefs ->
                    when (value) {
                        is String -> prefs[stringPreferencesKey(key)] = value
                        is Int -> prefs[intPreferencesKey(key)] = value
                        is Long -> prefs[longPreferencesKey(key)] = value
                        is Boolean -> prefs[booleanPreferencesKey(key)] = value
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            prefs[stringSetPreferencesKey(key)] = value as Set<String>
                        }
                        null -> prefs.remove(stringPreferencesKey(key))
                    }
                }
            } catch (e: Throwable) {
                LOG.warn("persistAsync(key=$key) failed", e)
            }
        }
    }

    private fun loadFromDataStore() {
        try {
            val prefs = runBlocking { dataStore.data.first() }
            for ((key, value) in prefs.asMap()) {
                cache[key.name] = value
            }
        } catch (e: Throwable) {
            LOG.warn("loadFromDataStore failed, starting with empty cache", e)
        }
    }

    private fun seedDefaults() {
        for ((key, value) in defaults) {
            if (!cache.containsKey(key)) {
                setDefault(key, value)
            }
        }
    }

    private fun resetVolatileKeys() {
        for (key in volatileKeys) {
            val value = defaults[key] ?: continue
            setDefault(key, value)
        }
    }

    private fun setDefault(key: String, value: Any) {
        cache[key] = value
        persistAsync(key, value)
    }

    @Suppress("DEPRECATION")
    private fun migrateFromSharedPreferences(context: Context) {
        val oldPrefs = try {
            PreferenceManager.getDefaultSharedPreferences(context)
        } catch (e: Throwable) {
            LOG.warn("Could not access SharedPreferences for migration", e)
            return
        }

        if (oldPrefs.contains("_migrated_to_datastore")) return

        val editor = oldPrefs.edit()
        for ((key, defaultValue) in defaults) {
            if (!oldPrefs.contains(key)) continue
            try {
                val value: Any = when (defaultValue) {
                    is String -> oldPrefs.getString(key, null) ?: continue
                    is Int -> {
                        try {
                            oldPrefs.getInt(key, 0)
                        } catch (e: ClassCastException) {
                            LOG.warn("migrateFromSharedPreferences: key=$key stored as non-Int, attempting String read")
                            val s = oldPrefs.getString(key, null) ?: continue
                            s.toIntOrNull() ?: continue
                        }
                    }
                    is Long -> {
                        try {
                            oldPrefs.getLong(key, 0L)
                        } catch (e: ClassCastException) {
                            LOG.warn("migrateFromSharedPreferences: key=$key stored as non-Long, attempting String read")
                            val s = oldPrefs.getString(key, null) ?: continue
                            s.toLongOrNull() ?: continue
                        }
                    }
                    is Boolean -> {
                        try {
                            oldPrefs.getBoolean(key, false)
                        } catch (e: ClassCastException) {
                            LOG.warn("migrateFromSharedPreferences: key=$key stored as non-Boolean, attempting String read")
                            val s = oldPrefs.getString(key, null) ?: continue
                            s.toBooleanStrictOrNull() ?: continue
                        }
                    }
                    else -> continue
                }
                cache[key] = value
                editor.remove(key)
            } catch (e: Throwable) {
                LOG.warn("migrateFromSharedPreferences: key=$key failed", e)
            }
        }

        for ((key, value) in cache) {
            persistAsync(key, value)
        }

        editor.putBoolean("_migrated_to_datastore", true)
        editor.apply()
        LOG.info("migrateFromSharedPreferences: migration complete")
    }

    private fun buildDefaults(): Map<String, Any> {
        val m = HashMap<String, Any>()
        m[Constants.PREF_KEY_CORE_UUID] = uuidToString(UUID.randomUUID())
        m[Constants.PREF_KEY_CORE_LAST_SEEN_VERSION_BUILD] = ""
        m[Constants.PREF_KEY_MAIN_APPLICATION_ON_CREATE_TIMESTAMP] = System.currentTimeMillis()

        m[Constants.PREF_KEY_GUI_VIBRATE_ON_FINISHED_DOWNLOAD] = true
        m[Constants.PREF_KEY_GUI_LAST_MEDIA_TYPE_FILTER] = Constants.FILE_TYPE_TORRENTS.toInt()
        m[Constants.PREF_KEY_GUI_TOS_ACCEPTED] = false
        m[Constants.PREF_KEY_GUI_FINISHED_DOWNLOADS_BETWEEN_RATINGS_REMINDER] = 10
        m[Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE] = false
        m[Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION] = true
        m[Constants.PREF_KEY_GUI_SEARCH_KEYWORDFILTERDRAWER_TIP_TOUCHTAGS_DISMISSED] = false
        m[Constants.PREF_KEY_GUI_SEARCH_FILTER_DRAWER_BUTTON_CLICKED] = false
        m[Constants.PREF_KEY_GUI_SHOW_TRANSFERS_ON_DOWNLOAD_START] = true
        m[Constants.PREF_KEY_GUI_SHOW_NEW_TRANSFER_DIALOG] = true
        m[Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD] = 50
        m[Constants.PREF_KEY_GUI_INSTALLATION_TIMESTAMP] = -1L
        m[Constants.PREF_KEY_GUI_DISTRACTION_FREE_SEARCH] = true
        m[Constants.PREF_KEY_GUI_PLAYER_REPEAT_MODE] = MusicPlaybackService.REPEAT_ALL
        m[Constants.PREF_KEY_GUI_PLAYER_SHUFFLE_ENABLED] = false
        m[Constants.PREF_KEY_GUI_THEME_MODE] = "system"

        m[Constants.PREF_KEY_SEARCH_COUNT_DOWNLOAD_FOR_TORRENT_DEEP_SCAN] = 20
        m[Constants.PREF_KEY_SEARCH_COUNT_ROUNDS_FOR_TORRENT_DEEP_SCAN] = 10
        m[Constants.PREF_KEY_SEARCH_INTERVAL_MS_FOR_TORRENT_DEEP_SCAN] = 2000
        m[Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_DEEP_SCAN] = 20
        m[Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_RESULT] = 20
        m[Constants.PREF_KEY_SEARCH_MAX_TORRENT_FILES_TO_INDEX] = 100
        m[Constants.PREF_KEY_SEARCH_FULLTEXT_SEARCH_RESULTS_LIMIT] = 256

        m[Constants.PREF_KEY_SEARCH_USE_ZOOQLE] = true
        m[Constants.PREF_KEY_SEARCH_USE_SOUNDCLOUD] = true
        m[Constants.PREF_KEY_SEARCH_USE_ARCHIVEORG] = true
        m[Constants.PREF_KEY_SEARCH_USE_FROSTCLICK] = true
        m[Constants.PREF_KEY_SEARCH_USE_NYAA] = true
        m[Constants.PREF_KEY_SEARCH_USE_TPB] = true
        m[Constants.PREF_KEY_SEARCH_USE_TORRENTZ2] = true
        m[Constants.PREF_KEY_SEARCH_USE_MAGNETDL] = true
        m[Constants.PREF_KEY_SEARCH_USE_ONE337X] = true
        m[Constants.PREF_KEY_SEARCH_USE_IDOPE] = true
        m[Constants.PREF_KEY_SEARCH_USE_GLOTORRENTS] = true
        m[Constants.PREF_KEY_SEARCH_USE_YT] = (Constants.IS_BASIC_AND_DEBUG || !Constants.IS_GOOGLE_PLAY_DISTRIBUTION)
        m[Constants.PREF_KEY_SEARCH_USE_KNABEN] = true
        m[Constants.PREF_KEY_SEARCH_USE_TORRENTSCSV] = true

        m[Constants.PREF_KEY_NETWORK_ENABLE_DHT] = true
        m[Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY] = false
        m[Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY] = false

        m[Constants.PREF_KEY_NETWORK_I2P_ENABLED] = false
        m[Constants.PREF_KEY_NETWORK_I2P_HOSTNAME] = "127.0.0.1"
        m[Constants.PREF_KEY_NETWORK_I2P_PORT] = "7656"
        m[Constants.PREF_KEY_NETWORK_I2P_ALLOW_MIXED] = false
        m[Constants.PREF_KEY_NETWORK_I2P_INBOUND_QUANTITY] = "3"
        m[Constants.PREF_KEY_NETWORK_I2P_OUTBOUND_QUANTITY] = "3"
        m[Constants.PREF_KEY_NETWORK_I2P_INBOUND_LENGTH] = "3"
        m[Constants.PREF_KEY_NETWORK_I2P_OUTBOUND_LENGTH] = "3"

        m[Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS] = false
        m[Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY] = true
        m[Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED] = 0L
        m[Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED] = 0L
        m[Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS] = 4L
        m[Constants.PREF_KEY_TORRENT_MAX_UPLOADS] = 4L
        m[Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS] = 200L
        m[Constants.PREF_KEY_TORRENT_MAX_PEERS] = 200L
        m[Constants.PREF_KEY_TORRENT_DELETE_STARTED_TORRENT_FILES] = false
        m[Constants.PREF_KEY_TORRENT_TRANSFER_DETAIL_LAST_SELECTED_TAB_INDEX] = 1
        m[Constants.PREF_KEY_TORRENT_SEQUENTIAL_TRANSFERS_ENABLED] = false
        m[Constants.PREF_KEY_TORRENT_INCOMING_PORT_START] = 1024
        m[Constants.PREF_KEY_TORRENT_INCOMING_PORT_END] = 57000

        m[Constants.PREF_KEY_STORAGE_PATH] = Environment.getExternalStorageDirectory().absolutePath

        return m
    }

    private fun buildVolatileKeys(): Set<String> = setOf(
        Constants.PREF_KEY_SEARCH_COUNT_DOWNLOAD_FOR_TORRENT_DEEP_SCAN,
        Constants.PREF_KEY_SEARCH_COUNT_ROUNDS_FOR_TORRENT_DEEP_SCAN,
        Constants.PREF_KEY_SEARCH_INTERVAL_MS_FOR_TORRENT_DEEP_SCAN,
        Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_DEEP_SCAN,
        Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_RESULT,
        Constants.PREF_KEY_SEARCH_MAX_TORRENT_FILES_TO_INDEX,
        Constants.PREF_KEY_SEARCH_FULLTEXT_SEARCH_RESULTS_LIMIT,
        Constants.PREF_KEY_MAIN_APPLICATION_ON_CREATE_TIMESTAMP,
    )

    private fun uuidToString(uuid: UUID): String {
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        val buffer = ByteArray(16)
        for (i in 0..7) buffer[i] = (msb ushr 8 * (7 - i)).toByte()
        for (i in 8..15) buffer[i] = (lsb ushr 8 * (7 - i)).toByte()
        return Hex.encode(buffer)
    }
}
