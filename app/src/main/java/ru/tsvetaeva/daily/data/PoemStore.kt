package ru.tsvetaeva.daily.data

import android.content.Context
import ru.tsvetaeva.daily.core.Author
import ru.tsvetaeva.daily.core.Poem
import java.io.File

/** Скачанные с Викитеки стихи: по JSON-файлу на автора во внутреннем хранилище приложения. */
class PoemStore(context: Context, author: Author) {
    // У Цветаевой — прежнее имя файла, чтобы не потерять уже собранную базу.
    private val name = if (author == Author.TSVETAEVA) "poems.json" else "poems_${author.key}.json"
    private val file = File(context.filesDir, name)

    fun exists(): Boolean = file.exists()

    fun delete() {
        file.delete()
    }

    fun load(): List<Poem> {
        if (!file.exists()) return emptyList()
        return try {
            PoemJson.parse(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(poems: List<Poem>) {
        val tmp = File(file.parentFile, "$name.tmp")
        tmp.writeText(PoemJson.write(poems), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
