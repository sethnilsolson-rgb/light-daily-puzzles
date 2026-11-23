package com.setholson.dailypuzzles.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "puzzle_progress")
data class PuzzleProgressEntity(
    @PrimaryKey
    val progressKey: String, // e.g. "word_2026-11-23"
    val dateIso: String,
    val puzzleType: String,
    val progressJson: String,
    val completed: Boolean,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "puzzle_settings")
data class PuzzleSettingEntity(
    @PrimaryKey
    val key: String,
    val value: String,
)

@Dao
interface PuzzleDao {
    @Query("SELECT * FROM puzzle_progress WHERE progressKey = :progressKey LIMIT 1")
    suspend fun getProgress(progressKey: String): PuzzleProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(entity: PuzzleProgressEntity)

    @Query("DELETE FROM puzzle_progress WHERE dateIso != :keepDateIso")
    suspend fun pruneOldProgress(keepDateIso: String)

    @Query("SELECT * FROM puzzle_settings WHERE key = :key LIMIT 1")
    suspend fun getSetting(key: String): PuzzleSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSetting(entity: PuzzleSettingEntity)
}

@Database(
    entities = [
        PuzzleProgressEntity::class,
        PuzzleSettingEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PuzzleDatabase : RoomDatabase() {
    abstract fun dao(): PuzzleDao
}
