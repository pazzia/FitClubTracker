package com.fitclub.tracker.data.repo

import android.content.Context
import com.fitclub.tracker.data.model.Sex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileSettings(
    val name: String = "Мой профиль",
    val sex: Sex = Sex.MALE,
    val level: Int = 2,
    val unitsKg: Boolean = true,
    val reminders: Boolean = true,
    /** Short beep when the rest between sets runs out. */
    val restSound: Boolean = true,
    val theme: String = "Темная",
    val accent: Long = 0xFFC6FF3D
)

class ProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<ProfileSettings> = _settings.asStateFlow()

    private fun load() = ProfileSettings(
        name = prefs.getString("name", "Мой профиль") ?: "Мой профиль",
        sex = runCatching { Sex.valueOf(prefs.getString("sex", Sex.MALE.name)!!) }.getOrDefault(Sex.MALE),
        level = prefs.getInt("level", 2), unitsKg = prefs.getBoolean("kg", true),
        reminders = prefs.getBoolean("reminders", true),
        restSound = prefs.getBoolean("rest_sound", true),
        theme = normalizedTheme(prefs.getString("theme", null)),
        accent = themeAccent(normalizedTheme(prefs.getString("theme", null)))
    )

    fun update(requested: ProfileSettings) {
        val theme = normalizedTheme(requested.theme)
        val value = requested.copy(theme = theme, accent = themeAccent(theme))
        _settings.value = value
        prefs.edit().putString("name", value.name).putString("sex", value.sex.name)
            .putInt("level", value.level).putBoolean("kg", value.unitsKg)
            .putBoolean("reminders", value.reminders).putBoolean("rest_sound", value.restSound).putString("theme", value.theme)
            .putLong("accent", value.accent).apply()
    }
}


internal fun normalizedTheme(name: String?): String = if (name == "White Day" || name == "Светлая") "Светлая" else "Темная"
/** One brand accent; the light palette pairs it with ink instead of using it as text. */
internal fun themeAccent(@Suppress("UNUSED_PARAMETER") name: String): Long = 0xFFC6FF3D

