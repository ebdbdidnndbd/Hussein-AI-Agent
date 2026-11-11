package com.hussein.aiagent

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SagerTask(
    val id: String,
    var title: String,
    var deadline: String,
    var completed: Boolean
)

/**
 * قائمة مهام محلية بالكامل (بدون إنترنت) — شبيهة بـ Todoist / Microsoft To Do.
 * كل المهام تنخزن بملف SharedPreferences كـ JSON، وتظل محفوظة حتى لو انسكر التطبيق.
 */
object TaskManager {
    private const val PREFS_NAME = "sager_tasks_prefs"
    private const val KEY_TASKS = "tasks_json"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getTasks(): MutableList<SagerTask> {
        val json = prefs?.getString(KEY_TASKS, "[]") ?: "[]"
        val list = mutableListOf<SagerTask>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SagerTask(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        deadline = obj.optString("deadline", ""),
                        completed = obj.optBoolean("completed", false)
                    )
                )
            }
        } catch (_: Exception) {
            // لو تلف ملف الحفظ بالغلط، نرجع قائمة فاضية بدل ما نكرش
        }
        return list
    }

    private fun saveTasks(tasks: List<SagerTask>) {
        val arr = JSONArray()
        for (t in tasks) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("title", t.title)
            obj.put("deadline", t.deadline)
            obj.put("completed", t.completed)
            arr.put(obj)
        }
        prefs?.edit()?.putString(KEY_TASKS, arr.toString())?.apply()
    }

    fun addTask(title: String, deadline: String = ""): SagerTask {
        val tasks = getTasks()
        val task = SagerTask(
            id = System.currentTimeMillis().toString(),
            title = title.trim(),
            deadline = deadline.trim(),
            completed = false
        )
        tasks.add(task)
        saveTasks(tasks)
        return task
    }

    fun toggleComplete(id: String) {
        val tasks = getTasks()
        val task = tasks.find { it.id == id } ?: return
        task.completed = !task.completed
        saveTasks(tasks)
    }

    fun deleteTask(id: String) {
        val tasks = getTasks()
        tasks.removeAll { it.id == id }
        saveTasks(tasks)
    }

    fun pendingCount(): Int = getTasks().count { !it.completed }
}
