package com.pjournal.app.data.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FontManager private constructor(private val context: Context) {

    private val fontsDir = File(context.filesDir, "fonts")
    private val metadataFile = File(fontsDir, "fonts_metadata.json")
    private val gson = Gson()
    private val typefaceCache = mutableMapOf<String, Typeface>()

    private val _importedFonts = MutableStateFlow<List<ImportedFont>>(emptyList())
    val importedFonts: StateFlow<List<ImportedFont>> = _importedFonts.asStateFlow()

    init {
        loadMetadata()
    }

    private fun loadMetadata() {
        try {
            fontsDir.mkdirs()
            if (metadataFile.exists()) {
                val json = metadataFile.readText()
                val type = object : TypeToken<List<ImportedFont>>() {}.type
                val fonts: List<ImportedFont> = gson.fromJson(json, type)
                _importedFonts.value = fonts
            }
        } catch (_: Exception) {
            _importedFonts.value = emptyList()
        }
    }

    private fun saveMetadata() {
        try {
            fontsDir.mkdirs()
            metadataFile.writeText(gson.toJson(_importedFonts.value))
        } catch (_: Exception) { }
    }

    suspend fun importFont(uri: Uri, displayName: String): Result<ImportedFont> =
        withContext(Dispatchers.IO) {
            try {
                fontsDir.mkdirs()
                val extension = uri.lastPathSegment
                    ?.substringAfterLast('.', "")
                    ?.takeIf { it.isNotEmpty() && it.length <= 5 }
                    ?: "ttf"
                val id = UUID.randomUUID().toString()
                val fileName = "$id.$extension"
                val destFile = File(fontsDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(Exception("无法读取文件"))

                // Validate the copied file is a valid font
                val typeface = Typeface.createFromFile(destFile)
                if (typeface == Typeface.DEFAULT) {
                    destFile.delete()
                    return@withContext Result.failure(Exception("不是有效的字体文件"))
                }

                val name = displayName
                    .removeSuffix(".ttf")
                    .removeSuffix(".otf")
                    .removeSuffix(".TTF")
                    .removeSuffix(".OTF")
                    .ifBlank { uri.lastPathSegment?.removeSuffix(".ttf")?.removeSuffix(".otf") ?: "未命名字体" }

                val font = ImportedFont(id = id, name = name, fileName = fileName)
                _importedFonts.value = _importedFonts.value + font
                saveMetadata()

                Result.success(font)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteFont(fontId: String) {
        withContext(Dispatchers.IO) {
            val font = _importedFonts.value.find { it.id == fontId } ?: return@withContext
            _importedFonts.value = _importedFonts.value.filter { it.id != fontId }
            saveMetadata()
            File(fontsDir, font.fileName).delete()
            typefaceCache.remove(fontId)
        }
    }

    fun getFontFamily(fontId: String): FontFamily? {
        val font = _importedFonts.value.find { it.id == fontId } ?: return null
        return try {
            val typeface = typefaceCache.getOrPut(fontId) {
                Typeface.createFromFile(File(fontsDir, font.fileName))
            }
            FontFamily(typeface)
        } catch (_: Exception) {
            typefaceCache.remove(fontId)
            null
        }
    }

    companion object {
        @Volatile
        private var instance: FontManager? = null

        fun getInstance(context: Context): FontManager {
            return instance ?: synchronized(this) {
                instance ?: FontManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
