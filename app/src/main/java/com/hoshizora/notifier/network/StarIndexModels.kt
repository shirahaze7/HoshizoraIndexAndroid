package com.hoshizora.notifier.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StarIndexResponse(
    @Json(name = "result_list") val resultList: ResultList
)

@JsonClass(generateAdapter = true)
data class ResultList(
    val data: List<DayEntry>
)

@JsonClass(generateAdapter = true)
data class DayEntry(
    @Json(name = "start_datetime") val startDatetime: String,
    @Json(name = "end_datetime") val endDatetime: String,
    @Json(name = "starlight_index") val starlightIndex: List<HourIndex>,
    @Json(name = "sun_in") val sunIn: String? = null,
    @Json(name = "sun_out") val sunOut: String? = null,
    @Json(name = "moon_age") val moonAge: String? = null,
    @Json(name = "overall_review") val overallReview: String? = null
)

@JsonClass(generateAdapter = true)
data class HourIndex(
    val datetime: String,
    val index: Int,
    val per: Int? = null
)
