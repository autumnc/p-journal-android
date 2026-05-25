package com.pjournal.app

import android.app.Application
import com.pjournal.app.data.PreferencesManager
import com.pjournal.app.data.font.FontManager
import com.pjournal.app.data.repository.JournalRepository
import com.pjournal.app.data.sync.SyncManager
import com.pjournal.app.network.WebDavClient

class PJournalApp : Application() {
    lateinit var database: com.pjournal.app.data.db.AppDatabase
        private set
    lateinit var syncManager: SyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        FontManager.getInstance(this)
        database = com.pjournal.app.data.db.AppDatabase.create(this)
        syncManager = SyncManager(
            prefs = PreferencesManager(this),
            repository = JournalRepository(database.journalEntryDao()),
            syncLogDao = database.syncLogDao(),
            client = WebDavClient()
        )
    }

    companion object {
        lateinit var instance: PJournalApp
            private set
    }
}
