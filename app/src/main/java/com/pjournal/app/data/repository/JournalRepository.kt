package com.pjournal.app.data.repository

import com.pjournal.app.data.db.JournalEntryDao
import com.pjournal.app.data.db.JournalEntryEntity
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JournalRepository(private val dao: JournalEntryDao) {

    fun getAllEntries(): Flow<List<JournalEntryEntity>> = dao.getAllEntries()

    suspend fun getEntry(filename: String): JournalEntryEntity? = dao.getEntry(filename)

    suspend fun countByDate(dateKey: String): Int = dao.countByDate(dateKey)

    suspend fun getTotalCount(): Int = dao.getTotalCount()

    fun getTotalCountFlow(): Flow<Int> = dao.getTotalCountFlow()

    suspend fun getDistinctDates(): List<String> = dao.getDistinctDates()

    suspend fun saveEntry(text: String, prompt: String? = null): String {
        val timestamp = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
        val filename = sdf.format(Date(timestamp)) + ".txt"
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

        val chineseChars = text.count { it in '一'..'鿿' || it in '　'..'〿' || it in '＀'..'￯' }
        val englishWords = Regex("[a-zA-Z]+").findAll(text).count()
        val wordCount = chineseChars + englishWords

        val header = buildString {
            append("日期: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))}\n")
            append("字数: $wordCount\n")
            append("\n")
            if (prompt != null) {
                append("提示词: $prompt\n\n")
            } else {
                append("自由写作\n\n")
            }
        }

        val fullContent = header + text
        val checksum = computeMd5(fullContent)
        val entry = JournalEntryEntity(
            filename = filename,
            content = fullContent,
            createdAt = timestamp,
            prompt = prompt,
            wordCount = wordCount,
            dateKey = dateKey,
            checksum = checksum
        )
        dao.insertEntry(entry)
        return filename
    }

    suspend fun deleteEntry(filename: String) {
        dao.deleteEntry(filename)
    }

    suspend fun getStreak(): Int {
        val dates = dao.getDistinctDates()
        if (dates.isEmpty()) return 0

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())
        val dateSet = dates.toSet()

        var streak = 0
        val cal = java.util.Calendar.getInstance()
        var checkDate = today

        while (checkDate in dateSet) {
            streak++
            val d = sdf.parse(checkDate)!!
            cal.time = d
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
            checkDate = sdf.format(cal.time)

            if (streak > 365) break
        }
        return streak
    }

    suspend fun insertEntry(filename: String, content: String) {
        val lines = content.split('\n')
        var prompt: String? = null
        var createdAt = System.currentTimeMillis()
        var wordCount = 0

        for (line in lines) {
            val stripped = line.trim()
            if (stripped.startsWith("日期:")) {
                val dateStr = stripped.removePrefix("日期:").trim()
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    createdAt = sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
                } catch (_: Exception) {}
            } else if (stripped.startsWith("字数:")) {
                wordCount = stripped.removePrefix("字数:").trim().toIntOrNull() ?: 0
            } else if (stripped.startsWith("提示词:")) {
                prompt = stripped.removePrefix("提示词:").trim()
            }
        }

        val dateKey = filename.substring(0, 10)

        val entity = JournalEntryEntity(
            filename = filename,
            content = content,
            createdAt = createdAt,
            prompt = prompt,
            wordCount = wordCount,
            dateKey = dateKey,
            checksum = computeMd5(content)
        )
        dao.insertEntry(entity)
    }

    suspend fun updateEntry(filename: String, text: String, prompt: String? = null): String {
        val existing = dao.getEntry(filename)
        val createdAt = existing?.createdAt ?: System.currentTimeMillis()

        val chineseChars = text.count { it in '一'..'鿿' || it in '　'..'〿' || it in '＀'..'￯' }
        val englishWords = Regex("[a-zA-Z]+").findAll(text).count()
        val wordCount = chineseChars + englishWords

        val dateKey = filename.substring(0, 10)
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(createdAt))

        val header = buildString {
            append("日期: $dateStr\n")
            append("字数: $wordCount\n")
            append("\n")
            if (prompt != null) {
                append("提示词: $prompt\n\n")
            } else {
                append("自由写作\n\n")
            }
        }

        val fullContent = header + text
        val checksum = computeMd5(fullContent)

        val entry = JournalEntryEntity(
            filename = filename,
            content = fullContent,
            createdAt = createdAt,
            prompt = prompt,
            wordCount = wordCount,
            dateKey = dateKey,
            checksum = checksum
        )
        dao.insertEntry(entry)
        return filename
    }

    fun extractBody(content: String): String {
        val lines = content.split('\n')
        var inMetadata = true
        val bodyLines = mutableListOf<String>()
        for (line in lines) {
            val stripped = line.trim()
            if (inMetadata) {
                if (stripped.startsWith("日期:") || stripped.startsWith("字数:") ||
                    stripped.startsWith("提示词:") || stripped == "自由写作" || stripped.isEmpty()
                ) {
                    continue
                } else {
                    inMetadata = false
                    bodyLines.add(line)
                }
            } else {
                bodyLines.add(line)
            }
        }
        return bodyLines.joinToString("\n").trim()
    }

    companion object {
        fun computeMd5(content: String): String {
            val md5 = MessageDigest.getInstance("MD5")
            return md5.digest(content.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
