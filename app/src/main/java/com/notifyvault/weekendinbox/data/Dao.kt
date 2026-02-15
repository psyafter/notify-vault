package com.notifyvault.weekendinbox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CapturedNotificationEntity)

    @Query(
        """
        SELECT * FROM captured_notifications
        WHERE (:packageFilter IS NULL OR packageName = :packageFilter)
        AND (:fromDate IS NULL OR capturedAt >= :fromDate)
        AND (:toDate IS NULL OR capturedAt <= :toDate)
        AND (
            :search IS NULL OR :search = '' OR
            title LIKE '%' || :search || '%' OR
            text LIKE '%' || :search || '%' OR
            subText LIKE '%' || :search || '%'
        )
        ORDER BY capturedAt DESC
        """
    )
    fun observeAll(
        packageFilter: String?,
        fromDate: Long?,
        toDate: Long?,
        search: String?
    ): Flow<List<CapturedNotificationEntity>>

    @Query("SELECT * FROM captured_notifications ORDER BY id DESC LIMIT 1")
    suspend fun latest(): CapturedNotificationEntity?

    @Query("UPDATE captured_notifications SET handled = 1 WHERE id = :id")
    suspend fun markHandled(id: Long)

    @Query("DELETE FROM captured_notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT DISTINCT packageName FROM captured_notifications ORDER BY packageName")
    fun observeKnownPackages(): Flow<List<String>>
}

@Dao
interface RuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ruleEntity: RuleEntity)

    @Update
    suspend fun update(ruleEntity: RuleEntity)

    @Query("SELECT * FROM capture_rules ORDER BY id DESC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM capture_rules WHERE isActive = 1")
    suspend fun activeRules(): List<RuleEntity>

    @Query("DELETE FROM capture_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM capture_rules WHERE id = :id")
    suspend fun byId(id: Long): RuleEntity?
}
