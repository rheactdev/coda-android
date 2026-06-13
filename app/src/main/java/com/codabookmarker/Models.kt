package com.codabookmarker

import org.json.JSONArray
import org.json.JSONObject

data class Settings(
    val backendBaseUrl: String = "",
    val bookmarkApiKey: String = "",
    val codaToken: String = "",
)

data class CodaDoc(val id: String, val name: String)

data class CodaTable(val id: String, val name: String)

data class CodaColumn(
    val id: String,
    val name: String,
    val type: String,
    val multiple: Boolean,
    val options: List<String> = emptyList(),
) {
    fun toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("type", type)
        .put("multiple", multiple)
        .put("options", JSONArray(options))

    companion object {
        fun fromJson(json: JSONObject) = CodaColumn(
            id = json.optString("id"),
            name = json.optString("name"),
            type = json.optString("type"),
            multiple = json.optBoolean("multiple"),
            options = json.optJSONArray("options").toStringList(),
        )
    }
}

data class SavedForm(
    val id: String,
    val name: String,
    val docId: String,
    val docName: String,
    val tableId: String,
    val tableName: String,
    val tokenFingerprint: String,
    val columns: List<CodaColumn>,
) {
    fun toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("docId", docId)
        .put("docName", docName)
        .put("tableId", tableId)
        .put("tableName", tableName)
        .put("tokenFingerprint", tokenFingerprint)
        .put("columns", JSONArray().apply { columns.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(json: JSONObject): SavedForm {
            val columnsJson = json.optJSONArray("columns") ?: JSONArray()
            return SavedForm(
                id = json.optString("id"),
                name = json.optString("name"),
                docId = json.optString("docId"),
                docName = json.optString("docName"),
                tableId = json.optString("tableId"),
                tableName = json.optString("tableName"),
                tokenFingerprint = json.optString("tokenFingerprint"),
                columns = List(columnsJson.length()) { CodaColumn.fromJson(columnsJson.getJSONObject(it)) },
            )
        }
    }
}

data class SaveResult(val workflowRunId: String?)

internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return List(length()) { optString(it) }.filter { it.isNotBlank() }
}

internal fun tokenFingerprint(token: String): String {
    var hash = 0
    token.forEach { character -> hash = (hash * 31) + character.code }
    return "${token.length}:$hash"
}
