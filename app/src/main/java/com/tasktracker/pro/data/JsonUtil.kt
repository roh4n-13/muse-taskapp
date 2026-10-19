package com.tasktracker.pro.data

/**
 * Minimal JSON encoder/decoder for flat string maps. Pure Kotlin (no org.json),
 * so it works in JVM unit tests as well as on Android.
 * Supports: objects with string/null values, arrays of objects, strings, numbers,
 * booleans, null. Not a general-purpose parser — just enough for backups.
 */
object JsonUtil {

    fun escape(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    fun mapToJson(map: Map<String, String?>): String = buildString {
        append('{')
        map.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) append(',')
            append(escape(k))
            append(':')
            if (v == null) append("null") else append(escape(v))
        }
        append('}')
    }

    fun backupToJson(tasks: List<Map<String, String?>>, exportedAt: String): String = buildString {
        append("{\"app\":")
        append(escape("TaskTrackerPro"))
        append(",\"version\":1")
        append(",\"exportedAt\":")
        append(escape(exportedAt))
        append(",\"tasks\":[")
        tasks.forEachIndexed { i, m ->
            if (i > 0) append(',')
            append(mapToJson(m))
        }
        append("]}")
    }

    /** Parses a backup file. Returns null if the top-level JSON is invalid. */
    fun parseBackup(json: String): BackupData? {
        val parser = Parser(json)
        val root = try {
            parser.parseValue()
        } catch (e: Exception) {
            return null
        } as? Map<*, *> ?: return null
        @Suppress("UNCHECKED_CAST")
        val rootMap = root as Map<String, Any?>
        val tasksRaw = rootMap["tasks"] ?: return null
        if (tasksRaw !is List<*>) return null
        val tasks = mutableListOf<Map<String, String?>>()
        val malformed = mutableListOf<Int>()
        for ((index, item) in tasksRaw.withIndex()) {
            if (item !is Map<*, *>) {
                malformed.add(index)
                continue // skip malformed row, keep the rest
            }
            @Suppress("UNCHECKED_CAST")
            val m = item as Map<String, Any?>
            val strMap = HashMap<String, String?>()
            var badKey = false
            for ((k, v) in m) {
                if (k !is String) { badKey = true; break }
                strMap[k] = when (v) {
                    null -> null
                    is String -> v
                    else -> v.toString()
                }
            }
            if (badKey) {
                malformed.add(index)
                continue
            }
            tasks.add(strMap)
        }
        return BackupData(tasks, malformed)
    }

    data class BackupData(val tasks: List<Map<String, String?>>, val malformedIndices: List<Int> = emptyList())

    private class Parser(private val s: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWs()
            if (pos >= s.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (s[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> expect("true", true)
                'f' -> expect("false", false)
                'n' -> expect("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWs()
            if (pos < s.length && s[pos] == '}') { pos++; return map }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                if (pos >= s.length || s[pos] != ':') throw IllegalArgumentException("Expected ':' in object")
                pos++
                val value = parseValue()
                map[key] = value
                skipWs()
                if (pos >= s.length) throw IllegalArgumentException("Unterminated object")
                when (s[pos]) {
                    ',' -> { pos++; continue }
                    '}' -> { pos++; return map }
                    else -> throw IllegalArgumentException("Expected ',' or '}' in object")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            val list = mutableListOf<Any?>()
            pos++ // [
            skipWs()
            if (pos < s.length && s[pos] == ']') { pos++; return list }
            while (true) {
                list.add(parseValue())
                skipWs()
                if (pos >= s.length) throw IllegalArgumentException("Unterminated array")
                when (s[pos]) {
                    ',' -> { pos++; continue }
                    ']' -> { pos++; return list }
                    else -> throw IllegalArgumentException("Expected ',' or ']' in array")
                }
            }
        }

        private fun parseString(): String {
            if (s[pos] != '"') throw IllegalArgumentException("Expected string")
            pos++
            val sb = StringBuilder()
            while (pos < s.length) {
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) throw IllegalArgumentException("Bad escape")
                        when (val e = s[pos++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 4 > s.length) throw IllegalArgumentException("Bad unicode escape")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape: $e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseNumber(): Any {
            val start = pos
            while (pos < s.length && s[pos] in "-+0123456789.eE") pos++
            val num = s.substring(start, pos)
            return num.toLongOrNull() ?: num.toDoubleOrNull()
                ?: throw IllegalArgumentException("Bad number: $num")
        }

        private fun expect(literal: String, value: Any?): Any? {
            if (!s.startsWith(literal, pos)) throw IllegalArgumentException("Expected $literal")
            pos += literal.length
            return value
        }

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }
    }
}
