package com.hoshizora.notifier.ui

import android.content.Context
import android.location.Location
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hoshizora.notifier.data.AppDatabase
import com.hoshizora.notifier.data.LocationEntity
import com.hoshizora.notifier.data.LocationSortOption
import com.hoshizora.notifier.data.SettingsRepository
import com.hoshizora.notifier.data.StarIndexResultEntity
import com.hoshizora.notifier.data.UserSettings
import com.hoshizora.notifier.util.DateTimeUtils
import com.hoshizora.notifier.worker.RefreshResult
import com.hoshizora.notifier.worker.StarIndexCheckRunner
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** パース済みで軽量比較が可能なエンティティラッパー */
@Immutable
data class ParsedStarIndexResult(
    val entity: StarIndexResultEntity,
    val parsedDateTime: OffsetDateTime?,
    val localDate: LocalDate?
)

/** 日付ごとのグループ化・事前フォーマット済みデータ */
@Immutable
data class FormattedGroupedResults(
    val dateText: String,
    val relativeDayLabel: String?,
    val results: List<StarIndexResultEntity>,
    val sunIn: String? = null,
    val sunOut: String? = null,
    val moonAge: String? = null,
)

/** 地点情報と、その地点の直近取得結果(時間別)、および現在地からの距離(km)をまとめたもの */
@Immutable
data class LocationWithResults(
    val location: LocationEntity,
    val results: List<StarIndexResultEntity>,
    val groupedResults: List<FormattedGroupedResults> = emptyList(),
    val fetchedAtText: String? = null,
    val hasHighIndex: Boolean = false,
    val distanceKm: Double? = null,
    val distanceText: String? = null
)

private data class FilterAndSortState(
    val query: String,
    val currentLocation: Location?,
    val maxDistanceKm: Double?,
    val userSettings: UserSettings
)

class LocationListViewModel(context: Context) : ViewModel() {

    private val locationDao = AppDatabase.getInstance(context).locationDao()
    private val resultDao = AppDatabase.getInstance(context).starIndexResultDao()
    private val settingsRepository = SettingsRepository(context)

    private val moshi = Moshi.Builder().build()
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // リアルタイム設定Flow
    val userSettings: Flow<UserSettings> = settingsRepository.settingsFlow

    private val _sortOption = MutableStateFlow(settingsRepository.sortOption)
    val sortOption: StateFlow<LocationSortOption> = _sortOption.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPAN)
    private val fetchedAtFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.JAPAN)
        .withZone(ZoneId.systemDefault())

    private val filterAndSortState: Flow<FilterAndSortState> =
        combine(_searchQuery, _currentLocation, _maxDistanceKm, userSettings) { query, curLoc, maxDist, settings ->
            _sortOption.value = settings.sortOption
            FilterAndSortState(query, curLoc, maxDist, settings)
        }

    val locationsWithResults: Flow<List<LocationWithResults>> =
        combine(
            locationDao.observeAll(),
            resultDao.observeAll(),
            filterAndSortState
        ) { locations, results, filterAndSort ->
            val query = filterAndSort.query
            val curLoc = filterAndSort.currentLocation
            val maxDist = filterAndSort.maxDistanceKm
            val settings = filterAndSort.userSettings
            val sortOpt = settings.sortOption
            val threshold = settings.threshold

            val nowLocalDate = LocalDate.now()
            val nowOffsetDateTime = OffsetDateTime.now()
            val cutoff = nowOffsetDateTime.minusHours(1)

            // 各エンティティのパースを一括事前計算 (O(N) 1回のみ)
            // 観測夜(forecastDate)を優先し、無ければ朝5時切り替えの観測日でグループ化
            val parsedResultsGrouped = results.groupBy { it.locationId }.mapValues { (_, list) ->
                list.map { entity ->
                    val parsedDt = DateTimeUtils.parseDateTime(entity.datetime)
                    val obsDate = entity.forecastDate?.let { fDateStr ->
                        try { LocalDate.parse(fDateStr) } catch (_: Exception) { null }
                    } ?: parsedDt?.let { dt ->
                        if (dt.hour < 5) dt.toLocalDate().minusDays(1) else dt.toLocalDate()
                    }
                    ParsedStarIndexResult(
                        entity = entity,
                        parsedDateTime = parsedDt,
                        localDate = obsDate
                    )
                }.sortedWith(compareBy(nullsLast()) { it.parsedDateTime })
            }

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

                val parsedLocResults = parsedResultsGrouped[loc.id].orEmpty()
                val locEntities = parsedLocResults.map { it.entity }

                val hasHighIndex = locEntities.any { it.index >= threshold }
                val distanceText = distanceKm?.let { String.format(Locale.JAPAN, "約%.1f km", it) }
                val fetchedAtText = locEntities.firstOrNull()?.fetchedAt?.let { epochMillis ->
                    fetchedAtFormatter.format(Instant.ofEpochMilli(epochMillis))
                }

                // 観測対象日(観測夜)ごとのグループ化
                val groupedMap = parsedLocResults.groupBy { it.localDate }
                    .toSortedMap(nullsLast(naturalOrder()))

                val formattedGrouped = groupedMap.mapNotNull { (date, itemsForDate) ->
                    // 前日以前（今日より過去の日付）の段落は表示不要
                    if (date != null && date.isBefore(nowLocalDate)) {
                        return@mapNotNull null
                    }

                    val dateText = date?.format(dateFormatter) ?: ""
                    val relativeLabel = when (date) {
                        nowLocalDate -> "今日"
                        nowLocalDate.plusDays(1) -> "明日"
                        nowLocalDate.plusDays(2) -> "明後日"
                        else -> null
                    }
                    val dateStr = date?.toString()
                    val prevDateStr = date?.minusDays(1)?.toString()

                    // 日没(sunIn): 当日 date の夕方の日没
                    // 1) sunIn の日付が date と一致するもの
                    // 2) なければ forecastDate == dateStr の sunIn
                    val sunInRaw = parsedLocResults.firstNotNullOfOrNull { item ->
                        val sunInDt = DateTimeUtils.parseDateTime(item.entity.sunIn)
                        if (sunInDt != null && sunInDt.toLocalDate() == date) {
                            item.entity.sunIn
                        } else null
                    } ?: parsedLocResults.firstNotNullOfOrNull { item ->
                        if (item.entity.forecastDate == dateStr) item.entity.sunIn else null
                    }

                    // 日の出(sunOut): 当日 date の早朝の日の出
                    // 1) sunOut の日付が date と一致するもの
                    // 2) なければ forecastDate == prevDateStr (前日夜の予報) の sunOut
                    val sunOutRaw = parsedLocResults.firstNotNullOfOrNull { item ->
                        val sunOutDt = DateTimeUtils.parseDateTime(item.entity.sunOut)
                        if (sunOutDt != null && sunOutDt.toLocalDate() == date) {
                            item.entity.sunOut
                        } else null
                    } ?: parsedLocResults.firstNotNullOfOrNull { item ->
                        if (item.entity.forecastDate == prevDateStr) item.entity.sunOut else null
                    }

                    // 月齢(moonAge): 当日 date の夜の予報 (forecastDate == dateStr) から取得
                    val moonAge = parsedLocResults.firstNotNullOfOrNull { item ->
                        if (item.entity.forecastDate == dateStr) item.entity.moonAge else null
                    }

                    val validResults = itemsForDate.map { it.entity }.filter { it.index >= 0 }

                    // 星空指数データ(index >= 0)も太陽・月情報も無い空の段落は表示しない
                    if (validResults.isEmpty() && sunInRaw == null && sunOutRaw == null && moonAge == null) {
                        return@mapNotNull null
                    }

                    FormattedGroupedResults(
                        dateText = dateText,
                        relativeDayLabel = relativeLabel,
                        results = itemsForDate.map { it.entity },
                        sunIn = DateTimeUtils.formatTimeOnly(sunInRaw),
                        sunOut = DateTimeUtils.formatTimeOnly(sunOutRaw),
                        moonAge = moonAge,
                    )
                }

                Pair(
                    LocationWithResults(
                        location = loc,
                        results = locEntities,
                        groupedResults = formattedGrouped,
                        fetchedAtText = fetchedAtText,
                        hasHighIndex = hasHighIndex,
                        distanceKm = distanceKm,
                        distanceText = distanceText
                    ),
                    parsedLocResults
                )
            }.sortedWith { aPair, bPair ->
                val a = aPair.first
                val b = bPair.first
                val aParsed = aPair.second
                val bParsed = bPair.second

                when (sortOpt) {
                    LocationSortOption.REGISTERED -> {
                        a.location.id.compareTo(b.location.id)
                    }
                    LocationSortOption.STAR_INDEX -> {
                        val aFuture = aParsed.filter { it.entity.index >= 0 && (it.parsedDateTime == null || !it.parsedDateTime.isBefore(cutoff)) }
                        val bFuture = bParsed.filter { it.entity.index >= 0 && (it.parsedDateTime == null || !it.parsedDateTime.isBefore(cutoff)) }

                        // 1. これからの最高指数 (高い順)
                        val aMax = aFuture.maxOfOrNull { it.entity.index } ?: -1
                        val bMax = bFuture.maxOfOrNull { it.entity.index } ?: -1
                        if (aMax != bMax) {
                            return@sortedWith bMax.compareTo(aMax)
                        }

                        // 2. 最高指数に達する最速の日時 (より早く綺麗な星空が見られる地点優先)
                        val aEarliestMaxDt = aFuture
                            .filter { it.entity.index == aMax }
                            .minOfOrNull { it.parsedDateTime ?: OffsetDateTime.MAX }
                            ?: OffsetDateTime.MAX
                        val bEarliestMaxDt = bFuture
                            .filter { it.entity.index == bMax }
                            .minOfOrNull { it.parsedDateTime ?: OffsetDateTime.MAX }
                            ?: OffsetDateTime.MAX
                        if (aEarliestMaxDt != bEarliestMaxDt) {
                            return@sortedWith aEarliestMaxDt.compareTo(bEarliestMaxDt)
                        }

                        // 3. これからの平均指数 (高い順)
                        val aAvg = if (aFuture.isNotEmpty()) aFuture.map { it.entity.index }.average() else -1.0
                        val bAvg = if (bFuture.isNotEmpty()) bFuture.map { it.entity.index }.average() else -1.0
                        if (aAvg != bAvg) {
                            return@sortedWith bAvg.compareTo(aAvg)
                        }

                        // 4. 現在地からの距離 (近い順)
                        val aDist = a.distanceKm ?: Double.MAX_VALUE
                        val bDist = b.distanceKm ?: Double.MAX_VALUE
                        if (aDist != bDist) {
                            return@sortedWith aDist.compareTo(bDist)
                        }

                        // 5. 地点ID順
                        a.location.id.compareTo(b.location.id)
                    }
                    LocationSortOption.DISTANCE -> {
                        val aDist = a.distanceKm ?: Double.MAX_VALUE
                        val bDist = b.distanceKm ?: Double.MAX_VALUE
                        if (aDist != bDist) {
                            aDist.compareTo(bDist)
                        } else {
                            a.location.id.compareTo(b.location.id)
                        }
                    }
                    LocationSortOption.NAME -> {
                        val nameCompare = a.location.name.compareTo(b.location.name, ignoreCase = true)
                        if (nameCompare != 0) {
                            nameCompare
                        } else {
                            a.location.id.compareTo(b.location.id)
                        }
                    }
                }
            }.map { it.first }
        }.flowOn(Dispatchers.Default)

    fun updateSortOption(option: LocationSortOption) {
        _sortOption.value = option
        settingsRepository.sortOption = option
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

    fun refreshData(context: Context, onResult: (RefreshResult) -> Unit = {}) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = StarIndexCheckRunner.runCheck(context)
            _isRefreshing.value = false
            onResult(result)
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
