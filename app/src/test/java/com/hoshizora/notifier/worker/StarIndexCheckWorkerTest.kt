package com.hoshizora.notifier.worker

import com.hoshizora.notifier.data.StarIndexResultEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class StarIndexCheckWorkerTest {

    private fun createEntity(datetime: String, index: Int): StarIndexResultEntity {
        return StarIndexResultEntity(
            locationId = 1,
            datetime = datetime,
            hour = OffsetDateTime.parse(datetime).hour,
            isNextDay = false,
            index = index,
            fetchedAt = System.currentTimeMillis()
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
}
