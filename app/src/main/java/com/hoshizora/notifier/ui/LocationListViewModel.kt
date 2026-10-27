package com.hoshizora.notifier.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hoshizora.notifier.data.AppDatabase
import com.hoshizora.notifier.data.LocationEntity
import com.hoshizora.notifier.data.StarIndexResultEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 地点情報と、その地点の直近取得結果(時間別)をまとめたもの */
data class LocationWithResults(
    val location: LocationEntity,
    val results: List<StarIndexResultEntity>,
)

class LocationListViewModel(context: Context) : ViewModel() {

    private val locationDao = AppDatabase.getInstance(context).locationDao()
    private val resultDao = AppDatabase.getInstance(context).starIndexResultDao()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, LocationEntity::class.java)
    private val adapter = moshi.adapter<List<LocationEntity>>(listType)

    val locationsWithResults: Flow<List<LocationWithResults>> =
        combine(locationDao.observeAll(), resultDao.observeAll()) { locations, results ->
            val grouped = results.groupBy { it.locationId }
            locations.map { loc ->
                LocationWithResults(
                    location = loc,
                    results = grouped[loc.id].orEmpty().sortedBy { it.datetime },
                )
            }
        }

    fun delete(location: LocationEntity) {
        viewModelScope.launch { locationDao.delete(location) }
    }

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val locations = locationDao.getAll()
        adapter.toJson(locations)
    }

    fun importFromJson(json: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val locations = withContext(Dispatchers.IO) {
                    adapter.fromJson(json)
                }
                if (locations != null) {
                    // IDを0にリセットして新規登録扱いにする
                    val newLocations = locations.map { it.copy(id = 0) }
                    locationDao.insertAll(newLocations)
                    onSuccess()
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LocationListViewModel(context) as T
                }
            }
    }
}
