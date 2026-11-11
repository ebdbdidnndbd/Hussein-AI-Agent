package com.hussein.aiagent

import android.content.Context
import android.content.SharedPreferences

/**
 * ذاكرة صگر الدائمة — تخزين محلي بالكامل على الجهاز (بدون أي اتصال إنترنت).
 * تحفظ رمز القفل، الإحصائيات، ووضع التركيز الصارم حتى لو انسكر التطبيق أو انعاد تشغيل الجهاز.
 */
object AgentPrefs {
    private const val PREFS_NAME = "sager_space_prefs"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    var pinCode: String
        get() = prefs?.getString("pin_code", "0000") ?: "0000"
        set(value) { prefs?.edit()?.putString("pin_code", value)?.apply() }

    var strictMode: Boolean
        get() = prefs?.getBoolean("strict_mode", false) ?: false
        set(value) { prefs?.edit()?.putBoolean("strict_mode", value)?.apply() }

    var blockedAttempts: Int
        get() = prefs?.getInt("blocked_attempts", 0) ?: 0
        set(value) { prefs?.edit()?.putInt("blocked_attempts", value)?.apply() }

    var totalFocusMinutes: Int
        get() = prefs?.getInt("total_focus_minutes", 0) ?: 0
        set(value) { prefs?.edit()?.putInt("total_focus_minutes", value)?.apply() }

    var sessionsCompleted: Int
        get() = prefs?.getInt("sessions_completed", 0) ?: 0
        set(value) { prefs?.edit()?.putInt("sessions_completed", value)?.apply() }

    // عدد الأشجار اللي نمت كاملة (جلسة خلصت بنجاح بدون ما تنفتح تطبيقات مشتتة)
    var treesGrown: Int
        get() = prefs?.getInt("trees_grown", 0) ?: 0
        set(value) { prefs?.edit()?.putInt("trees_grown", value)?.apply() }

    // عدد الأشجار اللي ماتت (فتح تطبيق مشتت وقت التركيز)
    var treesDead: Int
        get() = prefs?.getInt("trees_dead", 0) ?: 0
        set(value) { prefs?.edit()?.putInt("trees_dead", value)?.apply() }

    // ملاحظات/خطة دراسية دائمة (مثل Notion مصغرة)
    var studyNotes: String
        get() = prefs?.getString("study_notes", "") ?: ""
        set(value) { prefs?.edit()?.putString("study_notes", value)?.apply() }

    fun incrementBlockedAttempts() {
        blockedAttempts += 1
    }

    fun addFocusMinutes(minutes: Int) {
        if (minutes > 0) totalFocusMinutes += minutes
    }

    fun statsReport(): String {
        return "لحد الحين سويت $sessionsCompleted جلسة تركيز، وجمعت $totalFocusMinutes دقيقة قراءة، وزرعت $treesGrown شجرة ناجحة، وماتت عندك $treesDead شجرة، وصديت $blockedAttempts محاولة تشتت. أنت صاروخ يا حسين!"
    }
}
