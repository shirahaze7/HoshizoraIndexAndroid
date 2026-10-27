package com.hoshizora.notifier.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StarIndexResultDao {

    @Query("SELECT * FROM star_index_results ORDER BY datetime ASC")
    fun observeAll(): Flow<List<StarIndexResultEntity>>

    @Query("DELETE FROM star_index_results WHERE locationId = :locationId")
    suspend fun deleteForLocation(locationId: Long)

    @Insert
    suspend fun insertAll(results: List<StarIndexResultEntity>)

    /** 指定地点の結果を、最新の内容で丸ごと置き換える */
    @Transaction
    suspend fun replaceForLocation(locationId: Long, results: List<StarIndexResultEntity>) {
        deleteForLocation(locationId)
        if (results.isNotEmpty()) {
            insertAll(results)
        }
    }
}
