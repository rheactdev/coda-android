package com.codabookmarker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AppStore(context: Context) {
    private val preferences = context.getSharedPreferences("coda_bookmarker", Context.MODE_PRIVATE)

    fun loadSettings() = Settings(
        backendBaseUrl = preferences.getString("backendBaseUrl", "") ?: "",
        bookmarkApiKey = preferences.getString("bookmarkApiKey", "") ?: "",
        codaToken = preferences.getString("codaToken", "") ?: "",
    )

    fun saveSettings(settings: Settings) {
        preferences.edit()
            .putString("backendBaseUrl", settings.backendBaseUrl)
            .putString("bookmarkApiKey", settings.bookmarkApiKey)
            .putString("codaToken", settings.codaToken)
            .apply()
    }

    fun loadForms(): List<SavedForm> {
        val raw = preferences.getString("savedForms", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { SavedForm.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun saveForms(forms: List<SavedForm>) {
        val array = JSONArray().apply { forms.forEach { put(it.toJson()) } }
        preferences.edit().putString("savedForms", array.toString()).apply()
    }

    fun loadValues(formId: String): Map<String, Any> {
        val raw = preferences.getString("formValues", "{}") ?: "{}"
        val formValues = runCatching { JSONObject(raw).optJSONObject(formId) }.getOrNull()
            ?: return emptyMap()
        return formValues.keys().asSequence().associateWith { key ->
            when (val value = formValues.opt(key)) {
                is JSONArray -> value.toStringList()
                else -> value?.toString().orEmpty()
            }
        }
    }

    fun saveValues(formId: String, values: Map<String, Any>) {
        val root = runCatching {
            JSONObject(preferences.getString("formValues", "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        root.put(formId, JSONObject().apply {
            values.forEach { (key, value) ->
                put(key, if (value is List<*>) JSONArray(value) else value)
            }
        })
        preferences.edit().putString("formValues", root.toString()).apply()
    }

    fun selectedFormId(): String = preferences.getString("selectedFormId", "") ?: ""

    fun selectForm(id: String) {
        preferences.edit().putString("selectedFormId", id).apply()
    }
}
