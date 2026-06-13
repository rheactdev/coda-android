package com.codabookmarker

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class ApiException(message: String) : Exception(message)

object CodaApi {
    private const val API_BASE = "https://coda.io/apis/v1"

    fun listDocs(token: String): List<CodaDoc> =
        fetchAll("$API_BASE/docs", token).map {
            CodaDoc(it.optString("id"), it.optString("name", it.optString("id")))
        }

    fun listTables(token: String, docId: String): List<CodaTable> =
        fetchAll("$API_BASE/docs/${encode(docId)}/tables", token).map {
            CodaTable(it.optString("id"), it.optString("name", it.optString("id")))
        }

    fun listColumns(token: String, docId: String, tableId: String): List<CodaColumn> {
        return fetchAll("$API_BASE/docs/${encode(docId)}/tables/${encode(tableId)}/columns", token)
            .map { column ->
                val format = column.optJSONObject("format") ?: JSONObject()
                val type = format.optString(
                    "type",
                    column.optString("displayType", column.optString("type")),
                ).lowercase()
                val multiple = format.optBoolean("isArray") ||
                    listOf("select", "lookup", "person", "relation").any(type::contains)
                CodaColumn(
                    id = column.optString("id"),
                    name = column.optString("name", column.optString("id")),
                    type = type,
                    multiple = multiple,
                    options = format.optJSONArray("options").optionNames(),
                )
            }
    }

    fun saveBookmark(
        settings: Settings,
        sharedUrl: String,
        form: SavedForm,
        properties: Map<String, Any>,
    ): SaveResult {
        val endpoint = "${settings.backendBaseUrl.trimEnd('/')}/api/save-bookmark"
        val body = JSONObject()
            .put("url", sharedUrl)
            .put("docId", form.docId)
            .put("tableId", form.tableId)
            .put("properties", JSONObject().apply {
                properties.forEach { (key, value) ->
                    put(key, if (value is List<*>) JSONArray(value) else value)
                }
            })
        val response = request(
            url = endpoint,
            method = "POST",
            token = settings.codaToken,
            headers = mapOf("x-api-key" to settings.bookmarkApiKey),
            body = body.toString(),
        )
        if (!response.optBoolean("ok", true)) {
            throw ApiException(response.optString("error", "The backend rejected the bookmark."))
        }
        return SaveResult(response.optString("workflowRunId").takeIf(String::isNotBlank))
    }

    private fun fetchAll(initialUrl: String, token: String): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        var nextUrl: String? = initialUrl
        var pageCount = 0
        while (!nextUrl.isNullOrBlank()) {
            if (++pageCount > 100) throw ApiException("Coda returned too many result pages.")
            val response = request(nextUrl, "GET", token)
            val pageItems = response.optJSONArray("items") ?: JSONArray()
            repeat(pageItems.length()) { items += pageItems.getJSONObject(it) }
            nextUrl = response.optString("nextPageLink").takeIf(String::isNotBlank)?.let {
                URI(API_BASE + "/").resolve(it).toString()
            }
        }
        return items
    }

    private fun request(
        url: String,
        method: String,
        token: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            headers.forEach(::setRequestProperty)
            if (body != null) {
                doOutput = true
                outputStream.bufferedWriter().use { it.write(body) }
            }
        }
        return try {
            val status = connection.responseCode
            val text = readText(if (status in 200..299) connection.inputStream else connection.errorStream)
            val json = runCatching { JSONObject(text) }.getOrElse {
                JSONObject().put("error", text.ifBlank { "HTTP $status" })
            }
            if (status !in 200..299) {
                throw ApiException(
                    json.optString(
                        "message",
                        json.optString("error", "Request failed with HTTP $status."),
                    ),
                )
            }
            json
        } finally {
            connection.disconnect()
        }
    }

    private fun readText(stream: InputStream?): String =
        stream?.bufferedReader()?.use { it.readText() }.orEmpty()

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun JSONArray?.optionNames(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { index ->
            when (val item = opt(index)) {
                is JSONObject -> item.optString("name", item.optString("display", item.optString("value")))
                else -> item?.toString().orEmpty()
            }
        }.filter(String::isNotBlank)
    }
}
