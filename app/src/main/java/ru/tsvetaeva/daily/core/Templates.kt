package ru.tsvetaeva.daily.core

/** Разбор вызовов шаблонов викитекста с учётом вложенных `{{…}}` и `[[…|…]]`. */
object Templates {

    data class Call(
        /** Название шаблона в нижнем регистре. */
        val name: String,
        /** Позиционные параметры (без «имя=значение»), как есть. */
        val args: List<String>,
        val named: Map<String, String>,
        val range: IntRange,
    )

    /** Все вызовы шаблонов с именами из [names] (регистр не важен). */
    fun find(src: String, names: Set<String>): List<Call> = find(src) { it in names }

    /** Все вызовы шаблонов, чьё имя (в нижнем регистре) подходит под [accept]; вложенные в них не ищутся. */
    fun find(src: String, accept: (String) -> Boolean): List<Call> {
        val out = ArrayList<Call>()
        var i = 0
        while (true) {
            val start = src.indexOf("{{", i)
            if (start < 0) break
            val end = matchingEnd(src, start)
            if (end < 0) break
            val body = src.substring(start + 2, end - 1)
            val parts = splitTop(body)
            val name = parts[0].trim().lowercase().replace('_', ' ')
            if (accept(name)) {
                val args = ArrayList<String>()
                val named = HashMap<String, String>()
                for (p in parts.drop(1)) {
                    val eq = topLevelEquals(p)
                    if (eq > 0) named[p.substring(0, eq).trim().lowercase()] = p.substring(eq + 1) else args += p
                }
                out += Call(name, args, named, start..end)
                i = end + 1
            } else {
                i = start + 2
            }
        }
        return out
    }

    /** Индекс последней `}` вызова, начинающегося в [start] (`{{`), или -1. */
    private fun matchingEnd(s: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < s.length - 1) {
            when {
                s.startsWith("{{", i) -> { depth++; i += 2 }
                s.startsWith("}}", i) -> {
                    depth--
                    i += 2
                    if (depth == 0) return i - 1
                }
                else -> i++
            }
        }
        return -1
    }

    /** Делит тело шаблона по `|` верхнего уровня. */
    private fun splitTop(body: String): List<String> {
        val parts = ArrayList<String>()
        var curly = 0
        var square = 0
        var last = 0
        var i = 0
        while (i < body.length) {
            when {
                body.startsWith("{{", i) -> { curly++; i += 2; continue }
                body.startsWith("}}", i) -> { curly--; i += 2; continue }
                body.startsWith("[[", i) -> { square++; i += 2; continue }
                body.startsWith("]]", i) -> { square--; i += 2; continue }
                body[i] == '|' && curly == 0 && square == 0 -> { parts += body.substring(last, i); last = i + 1 }
            }
            i++
        }
        parts += body.substring(last)
        return parts
    }

    /** «имя=значение»: знак `=` до первого переноса строки и вне вложенных конструкций. */
    private fun topLevelEquals(p: String): Int {
        val eq = p.indexOf('=')
        if (eq <= 0) return -1
        val key = p.substring(0, eq)
        if ('\n' in key.trim() || "{{" in key || "[[" in key || "<" in key) return -1
        return if (key.trim().matches(Regex("""[\p{L}\d _-]{1,30}"""))) eq else -1
    }
}
