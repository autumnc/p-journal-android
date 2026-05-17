package com.pjournal.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pjournal.app.data.repository.JournalRepository
import com.pjournal.app.network.WebDavClient
import kotlinx.coroutines.flow.first
import org.json.JSONObject

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        val url = prefs.getStringFlow("webdav_url").first()
        val username = prefs.getStringFlow("webdav_username").first()
        val password = prefs.getStringFlow("webdav_password").first()

        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            return Result.failure()
        }

        val client = WebDavClient()
        val repository = JournalRepository(
            com.pjournal.app.PJournalApp.instance.database.journalEntryDao()
        )

        // Ensure remote directory exists
        if (!client.mkdir(url, username, password)) {
            return Result.retry()
        }

        val localEntries = repository.getAllEntries().first()
        val remoteFiles = client.listFiles(url, username, password)

        val localMap = localEntries.associateBy { it.filename }
        val remoteMap = remoteFiles.associateBy { it.filename }

        // Load previous sync state
        val prevStateJson = prefs.getStringFlow("webdav_sync_state").first()
        val prevState = try {
            val obj = JSONObject(prevStateJson)
            val map = mutableMapOf<String, Long>()
            for (key in obj.keys()) {
                val normalizedKey = if (key.endsWith(".txt")) key else key + ".txt"
                map[normalizedKey] = obj.getLong(key)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }

        val newState = mutableMapOf<String, Long>()
        val allFilenames = (localMap.keys + remoteMap.keys).toSet()

        for (filename in allFilenames) {
            val localEntry = localMap[filename]
            val remoteFile = remoteMap[filename]
            val inPrev = filename in prevState
            val localExists = localEntry != null
            val remoteExists = remoteFile != null

            if (!localExists && !remoteExists) continue

            if (!localExists && remoteExists) {
                if (inPrev) {
                    client.delete(url, username, password, filename)
                } else {
                    val content = client.download(url, username, password, filename)
                    if (content != null) {
                        try {
                            repository.insertEntry(filename, content)
                            remoteFile?.lastModified?.let { newState[filename] = it }
                        } catch (_: Exception) {}
                    }
                }
            } else if (localExists && !remoteExists) {
                if (inPrev) {
                    repository.deleteEntry(filename)
                } else {
                    val success = client.upload(url, username, password, filename, localEntry!!.content)
                    if (success) {
                        newState[filename] = localEntry.createdAt
                    }
                }
            } else {
                val localMtime = localEntry!!.createdAt
                val remoteMtime = remoteFile!!.lastModified

                if (remoteMtime == null) {
                    newState[filename] = prevState[filename] ?: localMtime
                    continue
                }

                val diffMs = localMtime - remoteMtime

                if (kotlin.math.abs(diffMs) <= 1000) {
                    newState[filename] = localMtime
                } else if (diffMs > 0) {
                    val success = client.upload(url, username, password, filename, localEntry.content)
                    if (success) newState[filename] = localMtime
                    else newState[filename] = prevState[filename] ?: remoteMtime
                } else {
                    val content = client.download(url, username, password, filename)
                    if (content != null) {
                        try {
                            repository.insertEntry(filename, content)
                            newState[filename] = remoteMtime
                        } catch (_: Exception) {
                            newState[filename] = prevState[filename] ?: remoteMtime
                        }
                    } else {
                        newState[filename] = prevState[filename] ?: remoteMtime
                    }
                }
            }
        }

        // Save new sync state
        val newStateJson = JSONObject()
        for ((k, v) in newState) {
            newStateJson.put(k, v)
        }
        prefs.setString("webdav_sync_state", newStateJson.toString())

        return Result.success()
    }
}
