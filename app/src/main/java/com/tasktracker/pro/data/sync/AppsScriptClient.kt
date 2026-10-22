package com.tasktracker.pro.data.sync

import com.tasktracker.pro.data.Task
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Result of one backend call. Never throws for transport problems — they become errors. */
data class ApiResult<T>(val ok: Boolean, val data: T?, val error: String) {
    companion object {
        fun <T> success(data: T): ApiResult<T> = ApiResult(true, data, "")
        fun <T> failure(error: String): ApiResult<T> = ApiResult(false, null, error)
    }
}

data class PingInfo(val version: Int, val sheetTitle: String, val taskCount: Int)

/** Result of a batch upsert: how many wrote, and which IDs the server skipped. */
data class BatchUpsertResult(val upserted: Int, val skippedIds: List<String>)

/** Abstraction over the Apps Script backend so the sync engine is unit-testable. */
interface SheetsApi {
    fun isValidWebAppUrl(url: String): Boolean
    fun ping(url: String, token: String): ApiResult<PingInfo>
    fun snapshot(url: String, token: String): ApiResult<List<Task>>
    fun upsert(url: String, token: String, task: Task): ApiResult<String>
    fun delete(url: String, token: String, id: String): ApiResult<Boolean>
    fun batchUpsert(url: String, token: String, tasks: List<Task>): ApiResult<BatchUpsertResult>
}

/**
 * Thin HTTP client for the Google Apps Script Web App backend.
 * All methods are blocking — call from Dispatchers.IO.
 */
class AppsScriptClient : SheetsApi {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Accepts the standard Apps Script Web App URL shapes ending in /exec. */
    override fun isValidWebAppUrl(url: String): Boolean {
        val t = url.trim()
        if (!t.startsWith("https://")) return false
        if (!t.contains("script.google")) return false
        val path = t.substringBefore("?").trimEnd('/')
        return path.endsWith("/exec")
    }

    /** Raw action call. Returns the parsed response object, or null on transport failure. */
    private fun call(url: String, token: String, action: String, payload: JSONObject?): JSONObject? {
        val body = JSONObject()
            .put("token", token)
            .put("action", action)
        if (payload != null) body.put("payload", payload)
        val request = Request.Builder()
            .url(url.trim())
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string()?.trim().orEmpty()
            if (text.isEmpty()) return null
            return try {
                JSONObject(text)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun <T> wrap(
        url: String,
        token: String,
        action: String,
        payload: JSONObject?,
        parse: (JSONObject) -> T
    ): ApiResult<T> {
        val json = try {
            call(url, token, action, payload)
        } catch (e: Exception) {
            return ApiResult.failure("Network error: ${e.message ?: "connection failed"}")
        } ?: return ApiResult.failure(
            "No valid response from the server. Check the URL and your internet connection."
        )
        if (!json.optBoolean("ok", false)) {
            return ApiResult.failure(json.optString("error", "Server reported an error.").ifBlank { "Server reported an error." })
        }
        return try {
            ApiResult.success(parse(json))
        } catch (e: Exception) {
            ApiResult.failure("The server response was malformed: ${e.message}")
        }
    }

    /** Verifies URL + token with a read-only call. Changes nothing on the sheet. */
    override fun ping(url: String, token: String): ApiResult<PingInfo> =
        wrap(url, token, "ping", null) { json ->
            val d = json.getJSONObject("data")
            PingInfo(
                version = d.optInt("version", 0),
                sheetTitle = d.optString("sheetTitle", ""),
                taskCount = d.optInt("taskCount", 0)
            )
        }

    /** Full validated snapshot of remote tasks. */
    override fun snapshot(url: String, token: String): ApiResult<List<Task>> =
        wrap(url, token, "snapshot", null) { json ->
            val arr = json.getJSONObject("data").getJSONArray("tasks")
            val out = ArrayList<Task>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val map = HashMap<String, String?>(obj.length())
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = if (obj.isNull(k)) null else obj.optString(k, "")
                }
                SheetsSchema.mapToTask(map)?.let { out.add(it) }
            }
            out
        }

    override fun upsert(url: String, token: String, task: Task): ApiResult<String> {
        val payload = JSONObject().put("task", JSONObject(SheetsSchema.taskToMap(task)))
        return wrap(url, token, "upsert", payload) { json ->
            json.getJSONObject("data").getString("id")
        }
    }

    override fun delete(url: String, token: String, id: String): ApiResult<Boolean> {
        val payload = JSONObject().put("id", id)
        return wrap(url, token, "delete", payload) { json ->
            json.getJSONObject("data").optBoolean("deleted", false)
        }
    }

    override fun batchUpsert(url: String, token: String, tasks: List<Task>): ApiResult<BatchUpsertResult> {
        val arr = JSONArray()
        tasks.forEach { arr.put(JSONObject(SheetsSchema.taskToMap(it))) }
        val payload = JSONObject().put("tasks", arr)
        return wrap(url, token, "batchUpsert", payload) { json ->
            val d = json.getJSONObject("data")
            val skipped = mutableListOf<String>()
            val skippedArr = d.optJSONArray("skippedIds")
            if (skippedArr != null) {
                for (i in 0 until skippedArr.length()) {
                    skipped.add(skippedArr.optString(i, ""))
                }
            }
            BatchUpsertResult(
                upserted = d.optInt("upserted", 0),
                skippedIds = skipped.filter { it.isNotEmpty() }
            )
        }
    }
}
