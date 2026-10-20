package net.koalastuff.koalashelf.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core. edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import net.koalastuff.koalashelf.core.data.util.clampRate
import net.koalastuff.koalashelf.core.network.AbsJson

private val Context.koalaDataStore: DataStore<Preferences> by preferencesDataStore(name = "koalashelf_prefs")

@Serializable
data class SavedLibraryView(
    val id: String,
    val name: String,
    val query: String,
)

/**
 * Full listener preferences matching `apps/web/src/lib/prefs.svelte.ts`.
 */
@Serializable
data class KoalaPrefs(
    // Appearance
    val themeMode: String = "system", // "system" | "light" | "dark"
    val locale: String = "system", // "system" | "de" | "en"
    val accent: String = "vinyl", // "vinyl" | "ocean" | "teal" | "forest" | "indigo" | "plum" | "custom"
    val customAccent: String = "#7B4BD6",
    val textSize: String = "md", // "sm" | "md" | "lg" | "xl"
    val font: String = "standard", // "standard" | "literary" | "vintage" | "readable" | "dyslexic" | "mono" | "system"
    val motion: String = "system", // "system" | "reduce" | "full"
    val spin: Boolean = true,
    val coverSize: String = "md", // "sm" | "md" | "lg"
    // Player
    val skipBack: Int = 30,
    val skipForward: Int = 30,
    val smartRewind: Boolean = true,
    val remainingTime: Boolean = false,
    val progressScope: String = "book", // "book" | "chapter"
    val speedAdjusted: Boolean = true,
    val defaultRate: Float = 1.0f,
    val useLastRate: Boolean = true,
    val rememberRate: Boolean = true,
    val mediaKeys: String = "chapter", // "chapter" | "skip"
    // Sound & Audio effects
    val leveling: Boolean = false,
    val volume: Float = 1.0f,
    val boost: Float = 1.0f, // 1.0f .. 2.0f
    val voiceBoost: Boolean = false,
    val soundProfile: String = "off", // "off" | "voice" | "night" | "commute"
    val mono: Boolean = false,
    val skipSilence: Boolean = false,
    val needleDrop: Boolean = false,
    val vinylCrackle: Boolean = false,
    val vinylCrackleVolume: Float = 0.04f,
    val autoQueue: Boolean = true,
    val seriesOffer: Boolean = true,
    // Sleep timer
    val sleepFade: Boolean = true,
    val sleepFadeDuration: Int = 30, // 10 | 30 | 60
    val sleepRewind: Int = 15, // 0 | 15 | 30
    val sleepMin: Int = 20,
    val sleepMax: Int = 40,
    val autoSleep: Boolean = false,
    val autoSleepFrom: Int = 22 * 60,
    val autoSleepTo: Int = 6 * 60,
    val autoSleepKind: String = "smart", // "minutes" | "chapter" | "smart"
    val autoSleepMinutes: Int = 30,
    // Library & Home
    val startPage: String = "home", // "home" | "library"
    val hiddenShelves: List<String> = emptyList(),
    val shelfOrder: List<String> = emptyList(),
    val navHidden: List<String> = emptyList(),
    val savedViews: List<SavedLibraryView> = emptyList(),
    val dailyGoal: Int = 30, // minutes per day (0 = off)
    // Downloads
    val removeFinished: Boolean = false,
)

@Serializable
data class KoalaSettingsBackupEnvelope(
    val app: String = "KoalaShelf",
    val kind: String = "settings",
    val version: Int = 1,
    val exportedAt: String = "",
    val data: KoalaPrefs = KoalaPrefs(),
)

class PrefsRepository(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _prefs = MutableStateFlow(KoalaPrefs())
    val prefs: StateFlow<KoalaPrefs> = _prefs.asStateFlow()

    private val _bookRates = MutableStateFlow<Map<String, Float>>(emptyMap())
    val bookRates: StateFlow<Map<String, Float>> = _bookRates.asStateFlow()

    private val _silenceSavedTotalSec = MutableStateFlow(0.0)
    val silenceSavedTotalSec: StateFlow<Double> = _silenceSavedTotalSec.asStateFlow()

    init {
        scope.launch {
            val data = context.koalaDataStore.data.first()
            data[KEY_PREFS_JSON]?.let { raw ->
                runCatching { AbsJson.decodeFromString<KoalaPrefs>(raw) }.getOrNull()?.let {
                    _prefs.value = sanitizePrefs(it)
                }
            }
            data[KEY_BOOK_RATES_JSON]?.let { raw ->
                runCatching { AbsJson.decodeFromString<Map<String, Float>>(raw) }.getOrNull()?.let {
                    _bookRates.value = it
                }
            }
            data[KEY_SILENCE_SAVED]?.toDoubleOrNull()?.let {
                _silenceSavedTotalSec.value = it
            }
        }
    }

    fun update(transform: (KoalaPrefs) -> KoalaPrefs) {
        val next = sanitizePrefs(transform(_prefs.value))
        _prefs.value = next
        scope.launch {
            context.koalaDataStore.edit { store ->
                store[KEY_PREFS_JSON] = AbsJson.encodeToString(next)
            }
        }
    }

    fun resetToDefaults() {
        update { KoalaPrefs() }
    }

    fun reset() = resetToDefaults()

    fun getBookRate(itemId: String): Float? {
        return _bookRates.value[itemId]?.let { clampRate(it) }
    }

    fun setBookRate(itemId: String, rate: Float) {
        val clamped = clampRate(rate)
        val updated = _bookRates.value + (itemId to clamped)
        _bookRates.value = updated
        if (_prefs.value.useLastRate) {
            update { it.copy(defaultRate = clamped) }
        }
        scope.launch {
            context.koalaDataStore.edit { store ->
                store[KEY_BOOK_RATES_JSON] = AbsJson.encodeToString(updated)
            }
        }
    }

    fun addSilenceSaved(seconds: Double) {
        if (seconds <= 0.0 || seconds > 600.0) return
        val next = _silenceSavedTotalSec.value + seconds
        _silenceSavedTotalSec.value = next
        scope.launch {
            context.koalaDataStore.edit { store ->
                store[KEY_SILENCE_SAVED] = next.toString()
            }
        }
    }

    fun exportBackupJson(): String {
        val envelope = KoalaSettingsBackupEnvelope(
            app = "KoalaShelf",
            kind = "settings",
            version = 1,
            exportedAt = java.time.Instant.now().toString(),
            data = _prefs.value,
        )
        return AbsJson.encodeToString(envelope)
    }

    fun importBackupJson(jsonString: String): Boolean {
        return try {
            val trimmed = jsonString.trim()
            val prefsToApply = if (trimmed.contains("\"kind\"") && trimmed.contains("\"data\"")) {
                val env = AbsJson.decodeFromString<KoalaSettingsBackupEnvelope>(trimmed)
                if (env.app != "KoalaShelf" || env.kind != "settings") return false
                env.data
            } else {
                AbsJson.decodeFromString<KoalaPrefs>(trimmed)
            }
            update { sanitizePrefs(prefsToApply) }
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private val KEY_PREFS_JSON = stringPreferencesKey("ks_prefs_json")
        private val KEY_BOOK_RATES_JSON = stringPreferencesKey("ks_book_rates_json")
        private val KEY_SILENCE_SAVED = stringPreferencesKey("ks_silence_saved")

        val SKIP_STEPS = listOf(10, 15, 30, 45, 60)

        fun sanitizePrefs(p: KoalaPrefs): KoalaPrefs {
            return p.copy(
                skipBack = if (p.skipBack in SKIP_STEPS) p.skipBack else 30,
                skipForward = if (p.skipForward in SKIP_STEPS) p.skipForward else 30,
                defaultRate = clampRate(p.defaultRate),
                volume = p.volume.coerceIn(0f, 1f),
                boost = p.boost.coerceIn(1f, 2f),
                vinylCrackleVolume = p.vinylCrackleVolume.coerceIn(0.01f, 0.15f),
                sleepFadeDuration = if (p.sleepFadeDuration in listOf(10, 30, 60)) p.sleepFadeDuration else 30,
                sleepRewind = if (p.sleepRewind in listOf(0, 15, 30)) p.sleepRewind else 15,
                dailyGoal = p.dailyGoal.coerceIn(0, 720),
            )
        }
    }
}
