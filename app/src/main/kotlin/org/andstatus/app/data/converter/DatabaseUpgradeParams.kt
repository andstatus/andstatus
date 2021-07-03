package org.andstatus.app.data.converter

import android.database.sqlite.SQLiteDatabase

class DatabaseUpgradeParams(
    val db: SQLiteDatabase,
    val oldVersion: Int,
    val newVersion: Int
)
