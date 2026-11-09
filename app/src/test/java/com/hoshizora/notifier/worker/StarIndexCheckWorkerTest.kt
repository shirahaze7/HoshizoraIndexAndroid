package com.hoshizora.notifier.worker

import com.hoshizora.notifier.data.StarIndexResultEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class StarIndexCheckWorkerTest {

    private fun createEntity(datetime: String, index: Int, forecastDate: String? = null, moonAge: String? = null, sunIn: String? = null, sunOut: String? = null): StarIndexResultEntity {
        return StarIndexResultEntity(
            locationId = 1,
            datetime = datetime,
            hour = OffsetDateTime.parse(datetime).hour,
            isNextDay = false,
            index = index,
            fetchedAt = System.currentTimeMillis(),
            sunIn = sunIn,
            sunOut = sunOut,
            moonAge = moonAge,
            forecastDate = forecastDate,
        )
    }

    @Test
    fun filterTonightResults_eveningExecution_selectsTonightAndNextMorningOnly() {
        // 11/4 18:00 実行
        val now = OffsetDateTime.parse("2026-11-04T18:00:00+09:00")

        val results = listOf(
            createEntity("2026-11-04T20:00:00+09:00", 2),
            createEntity("2026-11-04T22:00:00+09:00", 3),
            createEntity("2026-11-05T01:00:00+09:00", 4), // 翌朝01時 (今夜の範囲内)
            createEntity("2026-11-05T20:00:00+09:00", 4), // 明日の夜 (今夜ではない)
            createEntity("2026-11-06T20:00:00+09:00", 4)  // 明後日の夜
        )

        val filtered = StarIndexCheckWorker.filterTonightResults(results, now)

        assertEquals(3, filtered.size)
        assertEquals("2026-11-04T20:00:00+09:00", filtered[0].datetime)
        assertEquals("2026-11-04T22:00:00+09:00", filtered[1].datetime)
        assertEquals("2026-11-05T01:00:00+09:00", filtered[2].datetime)
    }

    @Test
    fun filterTonightResults_lateNightExecution_includesCurrentHourAndRestOfNight() {
        // 11/5 01:15 実行 (午前1時15分)
        val now = OffsetDateTime.parse("2026-11-05T01:15:00+09:00")

        val results = listOf(
            createEntity("2026-11-04T22:00:00+09:00", 2), // 昨夜 (過去)
            createEntity("2026-11-05T01:00:00+09:00", 3), // 01:00 (現時刻の時間枠 - 含めるべき)
            createEntity("2026-11-05T02:00:00+09:00", 4), // 02:00 (今夜のこれからの時間)
            createEntity("2026-11-05T20:00:00+09:00", 4)  // 今夜の次の夜 (今夜のタイムウィンドウ外)
        )

        val filtered = StarIndexCheckWorker.filterTonightResults(results, now)

        assertEquals(2, filtered.size)
        assertEquals("2026-11-05T01:00:00+09:00", filtered[0].datetime)
        assertEquals("2026-11-05T02:00:00+09:00", filtered[1].datetime)
    }

    @Test
    fun filterTonightResults_morningExecution_selectsUpcomingNight() {
        // 11/5 06:00 実行 (午前6時 - 朝5時を超えたので新しい日の夜を対象とする)
        val now = OffsetDateTime.parse("2026-11-05T06:00:00+09:00")

        val results = listOf(
            createEntity("2026-11-05T01:00:00+09:00", 3), // 過ぎ去った夜
            createEntity("2026-11-05T20:00:00+09:00", 4), // これから来る11/5夜
            createEntity("2026-11-06T01:00:00+09:00", 3)  // 11/6未明 (11/5夜のサイクル内)
        )

        val filtered = StarIndexCheckWorker.filterTonightResults(results, now)

        assertEquals(2, filtered.size)
        assertEquals("2026-11-05T20:00:00+09:00", filtered[0].datetime)
        assertEquals("2026-11-06T01:00:00+09:00", filtered[1].datetime)
    }

    @Test
    fun filterNightResults_futureDays_selectsTomorrowAndDayAfterTomorrow() {
        val now = OffsetDateTime.parse("2026-11-04T18:00:00+09:00")

        val results = listOf(
            createEntity("2026-11-04T22:00:00+09:00", 2),
            createEntity("2026-11-05T22:00:00+09:00", 4), // 明日
            createEntity("2026-11-06T22:00:00+09:00", 4)  // 明後日
        )

        val tomorrow = StarIndexCheckWorker.filterNightResults(results, now, dayOffset = 1)
        assertEquals(1, tomorrow.size)
        assertEquals("2026-11-05T22:00:00+09:00", tomorrow[0].datetime)

        val dayAfterTomorrow = StarIndexCheckWorker.filterNightResults(results, now, dayOffset = 2)
        assertEquals(1, dayAfterTomorrow.size)
        assertEquals("2026-11-06T22:00:00+09:00", dayAfterTomorrow[0].datetime)
    }

    @Test
    fun formatTimeRange_acrossMidnight_formatsChronologicallyWithNextDayPrefix() {
        val results = listOf(
            createEntity("2026-11-04T22:00:00+09:00", 4),
            createEntity("2026-11-04T23:00:00+09:00", 4),
            createEntity("2026-11-05T00:00:00+09:00", 4),
            createEntity("2026-11-05T01:00:00+09:00", 4)
        )

        val formatted = StarIndexCheckWorker.formatTimeRange(results, 4)
        assertEquals("22時〜翌1時", formatted)
    }

    @Test
    fun forecastDate_correctlyMatchesDateForSunAndMoonInfo() {
        // 11/4夜発の予報: 日没 16:45 (11/4夕), 日の出 06:05 (11/5朝)
        val nov4Night01 = createEntity("2026-11-05T01:00:00+09:00", 3, forecastDate = "2026-11-04", moonAge = "24.5", sunIn = "2026-11-04T16:45:00+09:00", sunOut = "2026-11-05T06:05:00+09:00")
        // 11/5夜発の予報: 日没 16:44 (11/5夕), 日の出 06:06 (11/6朝)
        val nov5Night20 = createEntity("2026-11-05T20:00:00+09:00", 4, forecastDate = "2026-11-05", moonAge = "25.5", sunIn = "2026-11-05T16:44:00+09:00", sunOut = "2026-11-06T06:06:00+09:00")

        val itemsForNov5 = listOf(nov4Night01, nov5Night20)
        val dateStr = "2026-11-05"

        val sunInEntity = itemsForNov5.find { it.forecastDate == dateStr }
            ?: itemsForNov5.find { OffsetDateTime.parse(it.datetime).hour >= 12 }
            ?: itemsForNov5.lastOrNull()

        val sunOutEntity = itemsForNov5.find { OffsetDateTime.parse(it.datetime).hour < 12 }
            ?: sunInEntity

        assertEquals("25.5", sunInEntity?.moonAge)
        assertEquals("2026-11-05T16:44:00+09:00", sunInEntity?.sunIn)
        assertEquals("2026-11-05T06:05:00+09:00", sunOutEntity?.sunOut)
    }

    @Test
    fun formatTimeOnly_convertsIso8601ToTimeOnlyFormat() {
        val sunIn = "2026-11-05T16:51:00+09:00"
        val sunOut = "2026-11-06T06:06:00+09:00"

        assertEquals("16:51", com.hoshizora.notifier.util.DateTimeUtils.formatTimeOnly(sunIn))
        assertEquals("06:06", com.hoshizora.notifier.util.DateTimeUtils.formatTimeOnly(sunOut))
        assertEquals("16:45", com.hoshizora.notifier.util.DateTimeUtils.formatTimeOnly("16:45"))
        assertEquals("06:06", com.hoshizora.notifier.util.DateTimeUtils.formatTimeOnly("11-6 06:06"))
        assertEquals("06:05", com.hoshizora.notifier.util.DateTimeUtils.formatTimeOnly("11/6 6:05"))
        assertEquals("06:06", com.hoshizora.notifier.util.DateTimeUtils.formatTimeOnly("2026/11/06 06:06"))
    }

    @Test
    fun placeholderEntity_retainsSunOutWhenTargetHoursDoNotMatch() {
        // 11/5 03:10 実行時。前日(11/4)予報の starlightIndex (03:00) が対象時間外でも、index = -1 のダミーエンティティが保持される
        val nov4Placeholder = createEntity(
            datetime = "2026-11-05T03:00:00+09:00",
            index = -1,
            forecastDate = "2026-11-04",
            sunIn = "2026-11-04T16:45:00+09:00",
            sunOut = "2026-11-05T06:05:00+09:00"
        )
        val nov5Item = createEntity(
            datetime = "2026-11-05T20:00:00+09:00",
            index = 4,
            forecastDate = "2026-11-05",
            sunIn = "2026-11-05T16:44:00+09:00",
            sunOut = "2026-11-06T06:06:00+09:00"
        )

        val results = listOf(nov4Placeholder, nov5Item)
        val date = java.time.LocalDate.parse("2026-11-05")

        val sunOutRaw = results.firstNotNullOfOrNull { item ->
            val sunOutDt = com.hoshizora.notifier.util.DateTimeUtils.parseDateTime(item.sunOut)
            if (sunOutDt != null && sunOutDt.toLocalDate() == date) {
                item.sunOut
            } else null
        }

        assertEquals("2026-11-05T06:05:00+09:00", sunOutRaw)
        assertEquals("06:05", com.hoshizora.notifier.util.DateTimeUtils.formatTimeOnly(sunOutRaw))
    }

    @Test
    fun groupedResults_filtersOutPreviousDayGroups_whileRetainingSunOutForToday() {
        val nov4Placeholder = createEntity(
            datetime = "2026-11-05T03:00:00+09:00",
            index = -1,
            forecastDate = "2026-11-04",
            sunIn = "2026-11-04T16:45:00+09:00",
            sunOut = "2026-11-05T06:05:00+09:00"
        )
        val nov5Item = createEntity(
            datetime = "2026-11-05T20:00:00+09:00",
            index = 4,
            forecastDate = "2026-11-05",
            sunIn = "2026-11-05T16:44:00+09:00",
            sunOut = "2026-11-06T06:06:00+09:00"
        )

        val nowLocalDate = java.time.LocalDate.parse("2026-11-05")
        val parsedResults = listOf(nov4Placeholder, nov5Item).map { entity ->
            val parsedDt = com.hoshizora.notifier.util.DateTimeUtils.parseDateTime(entity.datetime)
            val obsDate = entity.forecastDate?.let { fDateStr ->
                try { java.time.LocalDate.parse(fDateStr) } catch (_: Exception) { null }
            } ?: parsedDt?.let { dt ->
                if (dt.hour < 5) dt.toLocalDate().minusDays(1) else dt.toLocalDate()
            }
            obsDate to entity
        }

        val groupedMap = parsedResults.groupBy({ it.first }, { it.second })
            .toSortedMap(nullsLast(naturalOrder()))

        val formattedGrouped = groupedMap.mapNotNull { (date, _) ->
            if (date != null && date.isBefore(nowLocalDate)) {
                return@mapNotNull null
            }
            val dateStr = date?.toString()
            val prevDateStr = date?.minusDays(1)?.toString()

            val sunOutRaw = parsedResults.map { it.second }.firstNotNullOfOrNull { item ->
                if (item.forecastDate == prevDateStr && !item.sunOut.isNullOrBlank()) {
                    item.sunOut
                } else null
            } ?: parsedResults.map { it.second }.firstNotNullOfOrNull { item ->
                val sunOutDt = com.hoshizora.notifier.util.DateTimeUtils.parseDateTime(item.sunOut)
                if (sunOutDt != null && sunOutDt.toLocalDate() == date && item.forecastDate != dateStr) {
                    item.sunOut
                } else null
            }

            date to sunOutRaw
        }

        // 11/4 (前日) のグループは除外され、11/5 (今日) のみ存在すること
        assertEquals(1, formattedGrouped.size)
        assertEquals(java.time.LocalDate.parse("2026-11-05"), formattedGrouped[0].first)
        // 11/5 の日の出は 11/4 予報から引き継いだ 06:05 であること
        assertEquals("2026-11-05T06:05:00+09:00", formattedGrouped[0].second)
    }

    @Test
    fun groupedResults_handlesTimeOnlySunOutStrings_properlyUsesPreviousDayForecast() {
        val nov4Placeholder = createEntity(
            datetime = "2026-11-05T03:00:00+09:00",
            index = -1,
            forecastDate = "2026-11-04",
            sunIn = "16:45",
            sunOut = "06:05" // 前日(11/4)予報における11/5朝の日の出
        )
        val nov5Item = createEntity(
            datetime = "2026-11-05T20:00:00+09:00",
            index = 4,
            forecastDate = "2026-11-05",
            sunIn = "16:44",
            sunOut = "06:06" // 当日(11/5)予報における11/6朝の日の出
        )

        val nowLocalDate = java.time.LocalDate.parse("2026-11-05")
        val parsedResults = listOf(nov4Placeholder, nov5Item).map { entity ->
            val parsedDt = com.hoshizora.notifier.util.DateTimeUtils.parseDateTime(entity.datetime)
            val obsDate = entity.forecastDate?.let { fDateStr ->
                try { java.time.LocalDate.parse(fDateStr) } catch (_: Exception) { null }
            } ?: parsedDt?.let { dt ->
                if (dt.hour < 5) dt.toLocalDate().minusDays(1) else dt.toLocalDate()
            }
            obsDate to entity
        }

        val groupedMap = parsedResults.groupBy({ it.first }, { it.second })
            .toSortedMap(nullsLast(naturalOrder()))

        val formattedGrouped = groupedMap.mapNotNull { (date, _) ->
            if (date != null && date.isBefore(nowLocalDate)) {
                return@mapNotNull null
            }
            val dateStr = date?.toString()
            val prevDateStr = date?.minusDays(1)?.toString()

            val sunOutRaw = parsedResults.map { it.second }.firstNotNullOfOrNull { item ->
                if (item.forecastDate == prevDateStr && !item.sunOut.isNullOrBlank()) {
                    item.sunOut
                } else null
            } ?: parsedResults.map { it.second }.firstNotNullOfOrNull { item ->
                val sunOutDt = com.hoshizora.notifier.util.DateTimeUtils.parseDateTime(item.sunOut)
                if (sunOutDt != null && sunOutDt.toLocalDate() == date && item.forecastDate != dateStr) {
                    item.sunOut
                } else null
            } ?: parsedResults.map { it.second }.firstNotNullOfOrNull { item ->
                if (item.forecastDate == dateStr && !item.sunOut.isNullOrBlank()) {
                    item.sunOut
                } else null
            }

            date to sunOutRaw
        }

        assertEquals(1, formattedGrouped.size)
        assertEquals(java.time.LocalDate.parse("2026-11-05"), formattedGrouped[0].first)
        // 時刻のみ文字列 "06:05" が11/5(今日)の日の出として正しく取得できること (11/5予報の "06:06" と誤認しない)
        assertEquals("06:05", formattedGrouped[0].second)
    }

    @Test
    fun groupedResults_fallbackToTodayForecast_whenPreviousDayForecastIsMissing() {
        // 前日(11/4)の予報が存在せず、当日(11/5)の予報のみ取得された場合
        val nov5Item = createEntity(
            datetime = "2026-11-05T20:00:00+09:00",
            index = 4,
            forecastDate = "2026-11-05",
            sunIn = "16:44",
            sunOut = "06:06"
        )

        val nowLocalDate = java.time.LocalDate.parse("2026-11-05")
        val parsedResults = listOf(nov5Item).map { entity ->
            val parsedDt = com.hoshizora.notifier.util.DateTimeUtils.parseDateTime(entity.datetime)
            val obsDate = entity.forecastDate?.let { fDateStr ->
                try { java.time.LocalDate.parse(fDateStr) } catch (_: Exception) { null }
            } ?: parsedDt?.let { dt ->
                if (dt.hour < 5) dt.toLocalDate().minusDays(1) else dt.toLocalDate()
            }
            obsDate to entity
        }

        val groupedMap = parsedResults.groupBy({ it.first }, { it.second })
            .toSortedMap(nullsLast(naturalOrder()))

        val formattedGrouped = groupedMap.mapNotNull { (date, _) ->
            if (date != null && date.isBefore(nowLocalDate)) {
                return@mapNotNull null
            }
            val dateStr = date?.toString()
            val prevDateStr = date?.minusDays(1)?.toString()

            val sunOutRaw = parsedResults.map { it.second }.firstNotNullOfOrNull { item ->
                if (item.forecastDate == prevDateStr && !item.sunOut.isNullOrBlank()) {
                    item.sunOut
                } else null
            } ?: parsedResults.map { it.second }.firstNotNullOfOrNull { item ->
                val sunOutDt = com.hoshizora.notifier.util.DateTimeUtils.parseDateTime(item.sunOut)
                if (sunOutDt != null && sunOutDt.toLocalDate() == date && item.forecastDate != dateStr) {
                    item.sunOut
                } else null
            } ?: parsedResults.map { it.second }.firstNotNullOfOrNull { item ->
                if (item.forecastDate == dateStr && !item.sunOut.isNullOrBlank()) {
                    item.sunOut
                } else null
            }

            date to sunOutRaw
        }

        assertEquals(1, formattedGrouped.size)
        assertEquals(java.time.LocalDate.parse("2026-11-05"), formattedGrouped[0].first)
        // 前日予報が存在しない場合でも、当日予報の日の出 "06:06" がフォールバック表示され、空欄（日の出非表示）にならないこと
        assertEquals("06:06", formattedGrouped[0].second)
    }
}
