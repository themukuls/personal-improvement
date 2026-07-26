package com.chiefofstaff.system

import android.content.Context

/**
 * The "about you" profile gathered during the conversational onboarding — enough to personalise the
 * greeting, bias what the app surfaces, and (above all) decide *how* the app talks to you. Stored in
 * plain prefs (not the Room life graph) so it needs no schema/migration and is readable at start.
 *
 * Situation:  [name], [pronouns], [focusAreas], [primaryGoal], [goodDay]
 * Personality / working style:  [chronotype], [nudgeStyle], [motivation], [overwhelmStyle],
 *                               [wellbeingCheckins], [briefHour]
 *
 * The style fields are the point — a chief of staff that nudges a "hold me to it" person the same as
 * a "stay out of the way" person is useless. Values are short keys (see the onboarding options).
 */
class UserProfile(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cos_profile", Context.MODE_PRIVATE)

    private fun str(key: String) = prefs.getString(key, "").orEmpty()
    private fun setStr(key: String, v: String) = prefs.edit().putString(key, v.trim()).apply()

    var name: String
        get() = str(KEY_NAME); set(v) = setStr(KEY_NAME, v)

    var pronouns: String
        get() = str(KEY_PRONOUNS); set(v) = setStr(KEY_PRONOUNS, v)

    var focusAreas: Set<String>
        get() = prefs.getStringSet(KEY_FOCUS, emptySet()).orEmpty()
        set(value) { prefs.edit().putStringSet(KEY_FOCUS, value).apply() }

    var primaryGoal: String
        get() = str(KEY_GOAL); set(v) = setStr(KEY_GOAL, v)

    /** What "sharpest" time of day: early | midday | evening | varies. */
    var chronotype: String
        get() = str(KEY_CHRONO); set(v) = setStr(KEY_CHRONO, v)

    /** How hard to nudge: gentle | firm | minimal. */
    var nudgeStyle: String
        get() = str(KEY_NUDGE); set(v) = setStr(KEY_NUDGE, v)

    /** Voice when off-track: encourage | challenge. */
    var motivation: String
        get() = str(KEY_MOTIVATION); set(v) = setStr(KEY_MOTIVATION, v)

    /** Heavy-day view: one (just the next thing) | all. */
    var overwhelmStyle: String
        get() = str(KEY_OVERWHELM); set(v) = setStr(KEY_OVERWHELM, v)

    /** Whether the app should check in on how you're *doing* (mood), not just what you're doing. */
    var wellbeingCheckins: Boolean
        get() = prefs.getBoolean(KEY_WELLBEING, true)
        set(v) { prefs.edit().putBoolean(KEY_WELLBEING, v).apply() }

    /** Morning-brief hour: "6" | "7" | "8" | "none". */
    var briefHour: String
        get() = str(KEY_BRIEF); set(v) = setStr(KEY_BRIEF, v)

    /** One line: what a good day looks like — rich, human signal. */
    var goodDay: String
        get() = str(KEY_GOODDAY); set(v) = setStr(KEY_GOODDAY, v)

    /** Free-text kept editable on the profile screen ("on your mind"). Seeded from [goodDay]. */
    var intention: String
        get() = str(KEY_INTENTION); set(v) = setStr(KEY_INTENTION, v)

    val isComplete: Boolean get() = name.isNotBlank()

    /** Persist the editable subset from the profile screen. */
    fun save(name: String, focusAreas: Set<String>, intention: String) {
        this.name = name
        this.focusAreas = focusAreas
        this.intention = intention
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_PRONOUNS = "pronouns"
        const val KEY_FOCUS = "focus_areas"
        const val KEY_GOAL = "primary_goal"
        const val KEY_CHRONO = "chronotype"
        const val KEY_NUDGE = "nudge_style"
        const val KEY_MOTIVATION = "motivation"
        const val KEY_OVERWHELM = "overwhelm_style"
        const val KEY_WELLBEING = "wellbeing_checkins"
        const val KEY_BRIEF = "brief_hour"
        const val KEY_GOODDAY = "good_day"
        const val KEY_INTENTION = "intention"
    }
}
