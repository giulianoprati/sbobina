package it.intesys.audiototext.history

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.Normalizer

data class HistoryEntry(
    val id: String,
    val fileName: String,
    val audioDurationMs: Long?,
    val sharedAtMs: Long,
    val transcribedAtMs: Long,
    val text: String,
    val stats: String,
)

interface HistoryStore {
    /** Newest transcription first. */
    val entries: StateFlow<List<HistoryEntry>>

    /** Throws [IOException] if it cannot be persisted; the in-memory list is then left unchanged. */
    fun add(entry: HistoryEntry)
    fun delete(id: String)
    fun clear()
}

/** One JSON file in the app's private storage, rewritten atomically (temp file + move). */
class JsonHistoryStore(private val file: File) : HistoryStore {
    private val _entries = MutableStateFlow(load())
    override val entries: StateFlow<List<HistoryEntry>> = _entries

    @Synchronized
    override fun add(entry: HistoryEntry) =
        update((_entries.value + entry).sortedByDescending { it.transcribedAtMs })

    @Synchronized
    override fun delete(id: String) = update(_entries.value.filterNot { it.id == id })

    @Synchronized
    override fun clear() = update(emptyList())

    private fun update(list: List<HistoryEntry>) {
        persist(list)
        _entries.value = list
    }

    private fun persist(list: List<HistoryEntry>) {
        val array = JSONArray()
        for (e in list) {
            array.put(JSONObject().apply {
                put("id", e.id)
                put("name", e.fileName)
                if (e.audioDurationMs != null) put("duration", e.audioDurationMs)
                put("shared", e.sharedAtMs)
                put("transcribed", e.transcribedAtMs)
                put("text", e.text)
                put("stats", e.stats)
            })
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString(), Charsets.UTF_8)
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun load(): List<HistoryEntry> = try {
        if (!file.exists()) emptyList()
        else {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                HistoryEntry(
                    id = o.getString("id"),
                    fileName = o.getString("name"),
                    audioDurationMs = if (o.has("duration") && !o.isNull("duration")) o.getLong("duration") else null,
                    sharedAtMs = o.getLong("shared"),
                    transcribedAtMs = o.getLong("transcribed"),
                    text = o.getString("text"),
                    stats = o.optString("stats", ""),
                )
            }.sortedByDescending { it.transcribedAtMs }
        }
    } catch (e: Exception) {
        emptyList()  // unreadable history must never stop the app from starting
    }
}

/** One store per process, shared by every ViewModel that touches the history. */
object HistoryStores {
    @Volatile private var instance: HistoryStore? = null

    fun get(context: Context): HistoryStore = instance ?: synchronized(this) {
        instance ?: JsonHistoryStore(File(context.applicationContext.filesDir, "history.json")).also { instance = it }
    }
}

object HistoryFilter {
    /** Every word of [query] must appear in the file name or text, ignoring case and accents. */
    fun filter(entries: List<HistoryEntry>, query: String): List<HistoryEntry> {
        val words = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return entries
        return entries.filter { e ->
            val haystack = normalize(e.fileName + " " + e.text)
            words.all { haystack.contains(it) }
        }
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
