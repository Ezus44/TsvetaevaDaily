package ru.tsvetaeva.daily.core

/**
 * Разбор викитекста страниц ru.wikisource.org.
 *
 * Страница может быть:
 *  - отдельным стихотворением (один блок `<poem>`),
 *  - циклом/сборником, где стихи лежат прямо на странице (несколько блоков `<poem>`),
 *  - оглавлением, где стихи — ссылки на другие страницы (блоков нет, есть ссылки),
 *  - прозой с цитатами (такие страницы пропускаем, чтобы не брать чужие эпиграфы).
 */
object WikiParser {

    data class ParsedPage(
        val poems: List<Poem>,
        /** Ссылки на другие страницы основного пространства (уже с раскрытыми «/подстраницами»). */
        val links: List<String>,
        /** Даты, указанные рядом со ссылками в оглавлениях: «[[…]] (6 октября 1915)». */
        val dateHints: Map<String, DateInfo>,
        val isProse: Boolean,
    )

    private val commentRe = Regex("""<!--[\s\S]*?-->""")
    private val poemRe = Regex("""<poem[^>]*>([\s\S]*?)</poem\s*>""", RegexOption.IGNORE_CASE)
    private val linkRe = Regex("""\[\[([^\[\]|]*)(?:\|([^\[\]]*))?\]\]""")
    private val headingRe = Regex("""(?m)^(=+)\s*(.+?)\s*\1\s*$""")
    private val poemOnRe = Regex("""\{\{\s*[Pp]oem-on\s*\|([^{}]*)\}\}""")
    private val poemOffRe = Regex("""\{\{\s*[Pp]oem-off\s*\|([^{}]*)\}\}""")
    private val boldLineRe = Regex("""(?m)^\s*'''(.+?)'''\s*$""")
    private val centerRe = Regex("""<center>([\s\S]*?)</center>""", RegexOption.IGNORE_CASE)
    private val createdRe = Regex("""ДАТАСОЗДАНИЯ\s*=\s*([^|\n}]*)""")
    private val authorParenRe = Regex("""\s*\([^()]*Цветаев[^()]*\)""")

    private val skipNamespaces = setOf(
        "категория", "category", "файл", "file", "изображение", "image", "автор", "author",
        "викитека", "wikisource", "шаблон", "template", "индекс", "index", "страница", "page",
        "портал", "portal", "участник", "user", "обсуждение", "talk", "служебная", "special",
        "медиа", "media", "справка", "help", "модуль", "module", "викиданные", "wikidata",
        "commons", "wikipedia", "википедия", "викитека",
    )

    fun isRelevantTitle(title: String): Boolean =
        title.contains("(Цветаева") || title.contains("Цветаева)")

    /** [externalHint] — дата, найденная для этой страницы в оглавлении (если есть). */
    fun parse(pageTitle: String, wikitext: String, externalHint: DateInfo? = null): ParsedPage {
        val raw = commentRe.replace(wikitext, "")
        val links = extractLinks(pageTitle, raw)
        val hints = extractDateHints(pageTitle, raw)
        val pageDate = createdRe.find(raw)?.groupValues?.get(1)?.let { Dates.find(it, allowRoman = false) }

        val blocks = poemRe.findAll(raw).toList()
        if (blocks.isEmpty()) return ParsedPage(emptyList(), links, hints, isProse = false)

        // Проза с цитатами в стихах: текста вне блоков намного больше, чем в блоках.
        val poemLen = blocks.sumOf { clean(it.groupValues[1]).length }
        val outsideLen = clean(poemRe.replace(raw, "")).replace(Regex("\\s+"), " ").length
        if (outsideLen > 3000 && outsideLen > poemLen * 3) {
            return ParsedPage(emptyList(), links, hints, isProse = true)
        }

        val pageName = displayTitle(pageTitle)
        val poems = ArrayList<Poem>()
        var prevEnd = 0
        blocks.forEachIndexed { i, m ->
            val before = raw.substring(prevEnd, m.range.first)
            val afterEnd = if (i + 1 < blocks.size) blocks[i + 1].range.first else raw.length
            val after = raw.substring(m.range.last + 1, afterEnd).take(600)
            prevEnd = m.range.last + 1

            val lines = clean(m.groupValues[1]).lines().toMutableList()
            trimBlank(lines)

            // Подпись с датой в конце блока.
            val meta = ArrayList<String>()
            while (lines.isNotEmpty() && meta.size < 3 && Dates.isDateLine(lines.last())) {
                meta.add(0, lines.removeAt(lines.lastIndex).trim())
                trimBlank(lines)
            }
            // …или сразу после блока: {{poem-off|дата}} либо отдельная строка.
            if (meta.isEmpty()) {
                val off = poemOffRe.find(after)?.groupValues?.get(1)?.let { clean(lastArg(it)).trim() }
                if (!off.isNullOrEmpty() && !Dates.find(off).isEmpty) {
                    meta.add(off)
                } else {
                    clean(after).lines().map { it.trim() }.filter { it.isNotEmpty() }.take(2)
                        .takeWhile { Dates.isDateLine(it) }.forEach { meta.add(it) }
                }
            }

            val text = lines.joinToString("\n").trimEnd()
            val nonEmpty = lines.count { it.isNotBlank() }
            if (nonEmpty < 2 || text.length < 30) return@forEachIndexed

            val heading = findHeading(before)
            val title = when {
                blocks.size == 1 -> if (isUntitled(pageName)) firstLineTitle(lines) else pageName
                heading == null || isUntitled(heading) -> firstLineTitle(lines)
                isNumber(heading) -> "$pageName — $heading"
                else -> heading
            }

            val metaText = meta.joinToString(", ").ifBlank { null }
            var date = metaText?.let { Dates.find(it) } ?: DateInfo(null, null, null)
            date = date.orElse(externalHint)
            if (blocks.size == 1 || date.year == null) date = date.orElse(pageDate)

            poems += Poem(
                id = if (blocks.size == 1) pageTitle else "$pageTitle#${i + 1}",
                pageTitle = pageTitle,
                title = title,
                text = text,
                dateText = metaText,
                day = date.day, month = date.month, year = date.year,
            )
        }
        return ParsedPage(poems, links, hints, isProse = false)
    }

    // ---------- ссылки и подсказки с датами ----------

    fun resolveLink(pageTitle: String, target0: String): String? {
        var t = target0.substringBefore('#').replace('_', ' ').trim()
        if (t.isEmpty() || t.startsWith("../")) return null
        if (t.startsWith(":")) t = t.substring(1)
        if (t.startsWith("/")) t = pageTitle + t.trimEnd('/')
        val colon = t.indexOf(':')
        if (colon > 0) {
            val prefix = t.substring(0, colon).trim().lowercase()
            if (prefix in skipNamespaces || prefix.length <= 3) return null
        }
        return t.replaceFirstChar { it.uppercaseChar() }.replace(Regex("\\s+"), " ")
    }

    private fun extractLinks(pageTitle: String, raw: String): List<String> =
        linkRe.findAll(raw).mapNotNull { resolveLink(pageTitle, it.groupValues[1]) }
            .filter { it != pageTitle }.distinct().toList()

    private fun extractDateHints(pageTitle: String, raw: String): Map<String, DateInfo> {
        val hints = HashMap<String, DateInfo>()
        var sectionYear: Int? = null
        for (line in raw.lines()) {
            headingRe.matchEntire(line.trim())?.let { h ->
                sectionYear = Dates.find(h.groupValues[2], allowRoman = false).year ?: sectionYear
                return@let
            }
            val first = linkRe.find(line) ?: continue
            val target = resolveLink(pageTitle, first.groupValues[1]) ?: continue
            if (!isRelevantTitle(target)) continue
            val rest = clean(linkRe.replace(line, " "))
            val d = Dates.find(rest, allowRoman = false)
            val withYear = if (d.year == null && sectionYear != null) DateInfo(d.day, d.month, sectionYear) else d
            if (!withYear.isEmpty) hints[target] = withYear
        }
        return hints
    }

    // ---------- заголовки ----------

    private fun findHeading(before: String): String? {
        data class C(val pos: Int, val text: String)
        val cands = ArrayList<C>()
        poemOnRe.findAll(before).lastOrNull()?.let { cands += C(it.range.first, lastArg(it.groupValues[1])) }
        headingRe.findAll(before).lastOrNull()?.let { cands += C(it.range.first, it.groupValues[2]) }
        boldLineRe.findAll(before).lastOrNull()?.let { cands += C(it.range.first, it.groupValues[1]) }
        centerRe.findAll(before).lastOrNull()?.let { cands += C(it.range.first, it.groupValues[1]) }
        val best = cands.maxByOrNull { it.pos } ?: return null
        val t = clean(best.text).lines().joinToString(" ").replace(Regex("\\s+"), " ").trim()
        return t.ifEmpty { null }
    }

    private fun isUntitled(t: String): Boolean = t.replace(Regex("[\\s*⁂✱∗.·]"), "").isEmpty()

    private fun isNumber(t: String): Boolean = t.trim().trimEnd('.').matches(Regex("\\d{1,3}|[IVXLC]{1,7}"))

    fun firstLineTitle(lines: List<String>): String {
        val first = lines.firstOrNull { it.isNotBlank() }?.trim() ?: return "* * *"
        val cut = first.trimEnd(',', ';', ':', '—', '-', '–', ' ', '.', '!', '?', '…')
        val end = if (cut.length < first.length && first.trimEnd().last() in "!?") first.trimEnd().last().toString() else ""
        return "«$cut$end…»"
    }

    /** «Стихи к Блоку (Цветаева)/3» → «Стихи к Блоку — 3»; «Сборник (Цветаева)/Встреча» → «Встреча». */
    fun displayTitle(pageTitle: String): String {
        val parts = pageTitle.split('/').map { authorParenRe.replace(it, "").trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return pageTitle
        val last = parts.last()
        return if (parts.size > 1 && isNumber(last)) "${parts[parts.size - 2]} — $last" else last
    }

    // ---------- очистка викитекста ----------

    private val keepTemplates = setOf(
        "razr", "razr2", "разр", "разрядка", "b", "i", "u", "sc", "smaller", "larger", "big", "small",
        "nobr", "nowrap", "выступ", "перевод", "transl", "abbr", "nowrap", "ul", "uline", "esc", "s",
        "right", "center", "block center", "center block", "якорь", "anchor", "!", "lang",
    )
    private val templateRe = Regex("""\{\{([^{}]*)\}\}""")
    private val tagLinkRe = Regex("""\[https?://[^\s\]]+\s*([^\]]*)\]""")

    fun clean(src: String): String {
        var s = commentRe.replace(src, "")
        s = s.replace(Regex("""<ref[^>]*/>""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""<ref[^>]*>[\s\S]*?</ref>""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""<sup[^>]*>[\s\S]*?</sup>""", RegexOption.IGNORE_CASE), "")
        // Ссылки до шаблонов: внутри [[a|b]] тоже есть «|».
        s = linkRe.replace(s) { m ->
            val target = m.groupValues[1]
            val prefix = target.substringBefore(':', "").trim().lowercase()
            if (prefix in skipNamespaces) "" else (m.groups[2]?.value ?: target.substringBefore('#').trimStart('/', ':'))
        }
        s = tagLinkRe.replace(s) { it.groupValues[1] }
        for (pass in 0 until 12) {
            val next = templateRe.replace(s) { m -> renderTemplate(m.groupValues[1]) }
            if (next == s) break
            s = next
        }
        s = s.replace(Regex("""\{\{|\}\}"""), "")
        s = s.replace("'''", "").replace("''", "")
        s = s.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("""<[^>]+>"""), "")
        s = s.replace(Regex("""__[A-ZА-ЯЁ_]+__"""), "")
        s = decodeEntities(s)
        val lines = s.lines().map { line ->
            var l = line.trimEnd()
            var indent = 0
            while (l.startsWith(":")) { indent++; l = l.substring(1) }
            if (indent > 0) " ".repeat(indent) + l.trimStart() else l
        }
        // Не больше одной пустой строки подряд.
        val out = ArrayList<String>()
        for (l in lines) {
            if (l.isBlank() && (out.isEmpty() || out.last().isBlank())) continue
            out += if (l.isBlank()) "" else l
        }
        return out.joinToString("\n").trim('\n')
    }

    private fun renderTemplate(body: String): String {
        val parts = body.split('|')
        val name = parts[0].trim().lowercase().replace('_', ' ')
        val positional = parts.drop(1).filter { !it.contains('=') }
        return when {
            name == "---" || name == "--" || name == "mdash" || name == "—" -> "—"
            name == "gap" || name == "отступ" -> "  "
            name in keepTemplates || name.startsWith("lang") -> positional.lastOrNull()?.trim() ?: ""
            else -> ""
        }
    }

    private fun lastArg(args: String): String =
        args.split('|').filter { !it.contains('=') }.lastOrNull()?.trim() ?: ""

    private fun decodeEntities(s: String): String {
        var r = s
            .replace("&nbsp;", " ").replace("&#160;", " ")
            .replace("&mdash;", "—").replace("&ndash;", "–").replace("&hellip;", "…")
            .replace("&laquo;", "«").replace("&raquo;", "»").replace("&bdquo;", "„").replace("&ldquo;", "“")
            .replace("&rdquo;", "”").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#039;", "'").replace("&apos;", "'").replace("&shy;", "")
        r = Regex("""&#(\d+);""").replace(r) { it.groupValues[1].toIntOrNull()?.let { c -> String(Character.toChars(c)) } ?: "" }
        r = Regex("""&#x([0-9a-fA-F]+);""").replace(r) { it.groupValues[1].toIntOrNull(16)?.let { c -> String(Character.toChars(c)) } ?: "" }
        return r.replace("&amp;", "&")
    }

    private fun trimBlank(lines: MutableList<String>) {
        while (lines.isNotEmpty() && lines.first().isBlank()) lines.removeAt(0)
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.lastIndex)
    }
}
