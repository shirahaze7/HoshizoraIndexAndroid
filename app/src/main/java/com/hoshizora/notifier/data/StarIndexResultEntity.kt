package com.hoshizora.notifier.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 地点ごとの、直近取得分の時間別星空指数。
 * Workerが取得するたびに、対象地点の古いデータを削除してから
 * 最新のデータをまとめて保存する。
 */
@Entity(
    tableName = "star_index_results",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("locationId")]
)
data class StarIndexResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val locationId: Long,
    /** ISO8601形式の日時文字列 (例: 2026-08-10T02:00:00+09:00) */
    val datetime: String,
    val hour: Int,
    /** 実行日の翌日にあたる時刻かどうか(0時〜8時想定) */
    val isNextDay: Boolean,
    val index: Int,
    /** この結果を取得した(Workerを実行した)時刻(epoch millis) */
    val fetchedAt: Long
)
