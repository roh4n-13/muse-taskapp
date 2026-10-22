package com.tasktracker.pro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY lastUpdated DESC")
    fun observeActive(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND progress = 'Completed' ORDER BY taskCompleted DESC")
    fun observeCompleted(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Task?

    @Query("SELECT * FROM tasks WHERE deleted = 0")
    suspend fun getAllActive(): List<Task>

    /** Everything needing a remote write: local edits not yet synced + tombstones. */
    @Query("SELECT * FROM tasks WHERE syncState IN ('PENDING','ERROR')")
    suspend fun getUnsynced(): List<Task>

    @Query("SELECT COUNT(*) FROM tasks WHERE syncState IN ('PENDING','ERROR') AND deleted = 0")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: Task)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tasks: List<Task>)

    @Update
    suspend fun update(task: Task)

    /** Hard delete is only used for tombstone cleanup after a tombstone synced. */
    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun hardDelete(id: String)

    /** Marks every non-deleted task as needing upload (repair for stuck sync state). */
    @Query("UPDATE tasks SET syncState = 'PENDING' WHERE deleted = 0")
    suspend fun markAllPending()

    @Query("SELECT COUNT(*) FROM tasks WHERE deleted = 0")
    suspend fun countActive(): Int
}
