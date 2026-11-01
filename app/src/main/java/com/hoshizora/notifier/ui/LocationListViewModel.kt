package com.hoshizora.notifier.ui

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hoshizora.notifier.data.AppDatabase
import com.hoshizora.notifier.data.LocationEntity
import com.hoshizora.notifier.data.SettingsRepository
import com.hoshizora.notifier.data.StarIndexResultEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 地点情報と、その地点の直近取得結果(時間別)、および現在地からの距離(km)をまとめたもの */
data class LocationWithResults(
    val location: LocationEntity,
    val results: List<StarIndexResultEntity>,
    val distanceKm: Double? = null
)

class LocationListViewModel(context: Context) : ViewModel() {

    private val locationDao = AppDatabase.getInstance(context).locationDao()
    private val resultDao = AppDatabase.getInstance(context).starIndexResultDao()
    private val settingsRepository = SettingsRepository(context)

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, LocationEntity::class.java)
    private val adapter = moshi.adapter<List<LocationEntity>>(listType)

    // 検索フィルタ用のState
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _maxDistanceKm = MutableStateFlow<Double?>(null)
    val maxDistanceKm: StateFlow<Double?> = _maxDistanceKm.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _isFetchingLocation = MutableStateFlow(false)
    val isFetchingLocation: StateFlow<Boolean> = _isFetchingLocation.asStateFlow()

    val locationsWithResults: Flow<List<LocationWithResults>> =
        combine(
            locationDao.observeAll(),
            resultDao.observeAll(),
            _searchQuery,
            _currentLocation,
            _maxDistanceKm
        ) { locations, results, query, curLoc, maxDist ->
            val grouped = results.groupBy { it.locationId }
            locations.mapNotNull { loc ->
                // 地点名でフィルター（部分一致）
                if (query.isNotBlank() && !loc.name.contains(query, ignoreCase = true)) {
                    return@mapNotNull null
                }

                // 現在地からの直線距離（km）計算
                val distanceKm = curLoc?.let {
                    calculateDistanceKm(it.latitude, it.longitude, loc.lat, loc.lon)
                }

                // 距離制限フィルター
                if (maxDist != null && distanceKm != null && distanceKm > maxDist) {
                    return@mapNotNull null
                }

                LocationWithResults(
                    location = loc,
                    results = grouped[loc.id].orEmpty().sortedBy { it.datetime },
                    distanceKm = distanceKm
                )
            }.sortedWith(
                compareByDescending<LocationWithResults> { item ->
                    // 星空指数の最大値が高い地点を上位に表示（データがない場合は -1）
                    item.results.maxOfOrNull { it.index } ?: -1
                }.thenBy { item ->
                    // 距離がある場合は近い順、なければID順
                    item.distanceKm ?: Double.MAX_VALUE
                }.thenBy { item ->
                    item.location.id
                }
            )
        }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateMaxDistanceKm(distance: Double?) {
        _maxDistanceKm.value = distance
    }

    fun toggleSearchOpen() {
        _isSearchOpen.value = !_isSearchOpen.value
    }

    fun setSearchOpen(isOpen: Boolean) {
        _isSearchOpen.value = isOpen
    }

    fun updateCurrentLocation(location: Location?) {
        _currentLocation.value = location
    }

    fun setFetchingLocation(isFetching: Boolean) {
        _isFetchingLocation.value = isFetching
    }

    fun clearSearchFilter() {
        _searchQuery.value = ""
        _maxDistanceKm.value = null
    }

    fun getThreshold(): Int = settingsRepository.threshold

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
        fun calculateDistanceKm(
            startLat: Double,
            startLon: Double,
            endLat: Double,
            endLon: Double
        ): Double {
            val results = FloatArray(1)
            Location.distanceBetween(startLat, startLon, endLat, endLon, results)
            return (results[0] / 1000.0)
        }

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LocationListViewModel(context) as T
                }
            }
    }
}
