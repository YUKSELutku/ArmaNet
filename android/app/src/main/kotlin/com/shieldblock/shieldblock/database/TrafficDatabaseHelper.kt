package com.shieldblock.shieldblock.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TrafficDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE traffic_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uid INTEGER,
                package_name TEXT,
                app_name TEXT,
                domain TEXT NOT NULL,
                destination_ip TEXT,
                destination_port INTEGER,
                protocol TEXT NOT NULL,
                network_name TEXT,
                category TEXT NOT NULL,
                blocked INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                attribution_confidence TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_traffic_timestamp ON traffic_events(timestamp DESC)")
        db.execSQL("CREATE INDEX idx_traffic_package ON traffic_events(package_name)")
        db.execSQL("CREATE INDEX idx_traffic_domain ON traffic_events(domain)")
        db.execSQL(
            """
            CREATE TABLE allowlist (
                domain TEXT PRIMARY KEY,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        createCustomBlocklistTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createCustomBlocklistTable(db)
    }

    private fun createCustomBlocklistTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS custom_blocklist (
                domain TEXT PRIMARY KEY,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    companion object {
        private const val DATABASE_NAME = "shieldblock.db"
        private const val DATABASE_VERSION = 2
    }
}
