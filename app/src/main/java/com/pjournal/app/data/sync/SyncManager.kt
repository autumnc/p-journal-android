package com.pjournal.app.data.sync

import com.pjournal.app.data.PreferencesManager
import com.pjournal.app.data.db.JournalEntryEntity
import com.pjournal.app.data.db.SyncLogEntity
import com.pjournal.app.data.db.SyncLogDao
import com.pjournal.app.data.repository.JournalRepository
import com.pjournal.app.network.WebDavClient
import com.pjournal.app.network.WebDavFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

data class SyncResult(
    val uploadCount: Int = 0,
    val downloadCount: Int = 0,
    val deletedLocal: Int = 0,
    val deletedRemote: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val configSynced: Boolean = false
) {
    fun toMessage(): String {
        val parts = mutableListOf<String>()
        if (uploadCount > 0) parts.add("上传 ${uploadCount} 篇")
        if (downloadCount > 0) parts.add("下载 ${downloadCount} 篇")
        if (deletedLocal > 0) parts.add("本地删除 ${deletedLocal} 篇")
        if (deletedRemote > 0) parts.add("远程删除 ${deletedRemote} 篇")
        if (skipped > 0) parts.add("跳过 ${skipped} 篇")
        if (failed > 0) parts.add("失败 ${failed} 篇")
        if (configSynced) parts.add("配置已同步")
        return if (parts.isEmpty()) "无需同步，本地和远程一致" else parts.joinToString(" · ")
    }
}

class SyncManager(
    private val prefs: PreferencesManager,
    private val repository: JournalRepository,
    private val syncLogDao: SyncLogDao,
    private val client: WebDavClient
) {
    private var lastSyncTimeMs: Long = 0
    private val cooldownMs: Long = 30_000
    private var isSyncing: Boolean = false

    companion object {
        private const val CONFIG_FILENAME = "pjournal_config.json"

        fun computeMd5(content: String): String {
            val md5 = MessageDigest.getInstance("MD5")
            return md5.digest(content.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    suspend fun triggerSyncIfReady(): SyncResult? {
        val now = System.currentTimeMillis()
        if (isSyncing || (now - lastSyncTimeMs) < cooldownMs) return null
        return performSync()
    }

    suspend fun performSync(): SyncResult {
        isSyncing = true
        try {
            val url = prefs.getStringFlow("webdav_url").first()
            val username = prefs.getStringFlow("webdav_username").first()
            val password = prefs.getStringFlow("webdav_password").first()

            if (url.isBlank() || username.isBlank() || password.isBlank()) {
                return SyncResult()
            }

            val deviceId = prefs.getOrCreateDeviceId()
            var configSynced = false

            val result = withContext(Dispatchers.IO) {
                if (!client.mkdir(url, username, password)) {
                    return@withContext SyncResult()
                }

                val localEntries = repository.getAllEntries().first()
                val remoteFiles = client.listFiles(url, username, password)

                val localMap = localEntries.associateBy { it.filename }
                val remoteMap = remoteFiles.associateBy { it.filename }

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
                var uploadCount = 0
                var downloadCount = 0
                var deletedLocal = 0
                var deletedRemote = 0
                var skipped = 0
                var failed = 0

                // Filter out config file from journal entries
                val journalFiles = remoteFiles.filter { it.filename.endsWith(".txt") }

                val allFilenames = (localMap.keys + journalFiles.map { it.filename }).toSet()

                for (filename in allFilenames.sorted()) {
                    if (filename == CONFIG_FILENAME) continue

                    val localEntry = localMap[filename]
                    val remoteFile = remoteMap[filename]
                    val inPrev = filename in prevState
                    val localExists = localEntry != null
                    val remoteExists = remoteFile != null

                    if (!localExists && !remoteExists) continue

                    if (!localExists && remoteExists) {
                        if (inPrev) {
                            val ok = client.delete(url, username, password, filename)
                            syncLogDao.insert(SyncLogEntity(
                                filename = filename,
                                operationType = "delete_remote",
                                sourceDeviceId = deviceId,
                                targetDeviceId = "remote",
                                success = ok,
                                details = "local deleted, propagating to remote"
                            ))
                            if (ok) deletedRemote++ else failed++
                        } else {
                            val content = client.download(url, username, password, filename)
                            if (content != null) {
                                try {
                                    repository.insertEntry(filename, content)
                                    downloadCount++
                                    remoteFile?.lastModified?.let { newState[filename] = it }
                                    syncLogDao.insert(SyncLogEntity(
                                        filename = filename,
                                        operationType = "download",
                                        sourceDeviceId = "remote",
                                        targetDeviceId = deviceId,
                                        checksum = computeMd5(content),
                                        success = true
                                    ))
                                } catch (_: Exception) {
                                    failed++
                                    syncLogDao.insert(SyncLogEntity(
                                        filename = filename,
                                        operationType = "download",
                                        sourceDeviceId = "remote",
                                        targetDeviceId = deviceId,
                                        success = false,
                                        details = "insert failed"
                                    ))
                                }
                            } else {
                                failed++
                            }
                        }
                    } else if (localExists && !remoteExists) {
                        if (inPrev) {
                            repository.deleteEntry(filename)
                            deletedLocal++
                            syncLogDao.insert(SyncLogEntity(
                                filename = filename,
                                operationType = "delete_local",
                                sourceDeviceId = "remote",
                                targetDeviceId = deviceId,
                                success = true,
                                details = "remote deleted, propagating to local"
                            ))
                        } else {
                            val ok = client.upload(url, username, password, filename, localEntry!!.content)
                            syncLogDao.insert(SyncLogEntity(
                                filename = filename,
                                operationType = "upload",
                                sourceDeviceId = deviceId,
                                targetDeviceId = "remote",
                                checksum = localEntry.checksum,
                                success = ok
                            ))
                            if (ok) {
                                uploadCount++
                                newState[filename] = localEntry.createdAt
                            } else {
                                failed++
                            }
                        }
                    } else {
                        val localMtime = localEntry!!.createdAt
                        val remoteMtime = remoteFile!!.lastModified

                        if (remoteMtime == null) {
                            skipped++
                            newState[filename] = prevState[filename] ?: localMtime
                            continue
                        }

                        val diffMs = localMtime - remoteMtime

                        if (kotlin.math.abs(diffMs) <= 1000) {
                            skipped++
                            newState[filename] = localMtime
                        } else if (diffMs > 0) {
                            val ok = client.upload(url, username, password, filename, localEntry.content)
                            syncLogDao.insert(SyncLogEntity(
                                filename = filename,
                                operationType = "upload",
                                sourceDeviceId = deviceId,
                                targetDeviceId = "remote",
                                checksum = localEntry.checksum,
                                success = ok,
                                details = "local newer by ${diffMs}ms"
                            ))
                            if (ok) {
                                uploadCount++
                                newState[filename] = localMtime
                            } else {
                                failed++
                                newState[filename] = prevState[filename] ?: remoteMtime
                            }
                        } else {
                            val content = client.download(url, username, password, filename)
                            if (content != null) {
                                try {
                                    repository.insertEntry(filename, content)
                                    downloadCount++
                                    newState[filename] = remoteMtime
                                    syncLogDao.insert(SyncLogEntity(
                                        filename = filename,
                                        operationType = "download",
                                        sourceDeviceId = "remote",
                                        targetDeviceId = deviceId,
                                        checksum = computeMd5(content),
                                        success = true,
                                        details = "remote newer by ${-diffMs}ms"
                                    ))
                                } catch (_: Exception) {
                                    failed++
                                    newState[filename] = prevState[filename] ?: remoteMtime
                                }
                            } else {
                                failed++
                                newState[filename] = prevState[filename] ?: remoteMtime
                            }
                        }
                    }
                }

                // Sync config file
                configSynced = syncConfigFile(url, username, password, deviceId)

                // Save new sync state
                val newStateJson = JSONObject()
                for ((k, v) in newState) newStateJson.put(k, v)
                prefs.setString("webdav_sync_state", newStateJson.toString())

                SyncResult(uploadCount, downloadCount, deletedLocal, deletedRemote, skipped, failed, configSynced)
            }

            lastSyncTimeMs = System.currentTimeMillis()
            return result
        } catch (e: Exception) {
            return SyncResult(failed = 1)
        } finally {
            isSyncing = false
        }
    }

    private suspend fun syncConfigFile(
        url: String, username: String, password: String, deviceId: String
    ): Boolean {
        val localJson = prefs.exportToJson()
        val localChecksum = computeMd5(localJson)

        val remoteContent = client.download(url, username, password, CONFIG_FILENAME)
        val remoteChecksum = remoteContent?.let { computeMd5(it) }

        if (remoteContent == null) {
            // No remote config, upload local
            val ok = client.upload(url, username, password, CONFIG_FILENAME, localJson)
            syncLogDao.insert(SyncLogEntity(
                filename = CONFIG_FILENAME,
                operationType = "upload",
                sourceDeviceId = deviceId,
                targetDeviceId = "remote",
                checksum = localChecksum,
                success = ok,
                details = "config initial upload"
            ))
            return ok
        }

        if (remoteChecksum == localChecksum) return false

        // Compare versions to decide direction
        val localVersion = try {
            JSONObject(localJson).getLong("config_version")
        } catch (_: Exception) { 0L }

        val remoteVersion = try {
            JSONObject(remoteContent).getLong("config_version")
        } catch (_: Exception) { 0L }

        if (localVersion > remoteVersion) {
            val ok = client.upload(url, username, password, CONFIG_FILENAME, localJson)
            syncLogDao.insert(SyncLogEntity(
                filename = CONFIG_FILENAME,
                operationType = "upload",
                sourceDeviceId = deviceId,
                targetDeviceId = "remote",
                checksum = localChecksum,
                success = ok,
                details = "config upload (v$localVersion > v$remoteVersion)"
            ))
            return ok
        } else if (remoteVersion > localVersion) {
            try {
                prefs.importFromJson(remoteContent)
                syncLogDao.insert(SyncLogEntity(
                    filename = CONFIG_FILENAME,
                    operationType = "download",
                    sourceDeviceId = "remote",
                    targetDeviceId = deviceId,
                    checksum = remoteChecksum,
                    success = true,
                    details = "config download (v$remoteVersion > v$localVersion)"
                ))
                return true
            } catch (_: Exception) {
                syncLogDao.insert(SyncLogEntity(
                    filename = CONFIG_FILENAME,
                    operationType = "download",
                    sourceDeviceId = "remote",
                    targetDeviceId = deviceId,
                    checksum = remoteChecksum,
                    success = false,
                    details = "config import failed"
                ))
                return false
            }
        }

        return false
    }
}
