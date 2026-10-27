package com.hoshizora.notifier.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 監視対象の地点。
 * lat/lon/alt は、お天気ナビゲータのサイトで地点検索したときの
 * APIレスポンス(pref/name/lat/lon/alt)を参照して入力する。
 */
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val alt: Double = 0.0
)
