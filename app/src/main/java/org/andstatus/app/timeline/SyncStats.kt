package org.andstatus.app.timeline

import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.RelativeTime
import java.util.stream.Stream

/**
 *
 */
internal class SyncStats private constructor(val syncSucceededDate: Long, val itemDate: Long) {
    companion object {
        fun fromYoungestDates(timelines: Stream<Timeline>): SyncStats {
            return timelines.reduce(SyncStats(0, 0),
                    { stats: SyncStats, timeline: Timeline -> youngestAccumulator(stats, timeline) },
                    { stats: SyncStats, stats2: SyncStats -> youngestCombiner(stats, stats2) })
        }

        private fun youngestAccumulator(stats: SyncStats, timeline: Timeline): SyncStats {
            return SyncStats(
                    java.lang.Long.max(timeline.getSyncSucceededDate(), stats.syncSucceededDate),
                    java.lang.Long.max(timeline.getYoungestItemDate(), stats.itemDate)
            )
        }

        private fun youngestCombiner(stats: SyncStats, stats2: SyncStats): SyncStats {
            return SyncStats(
                    java.lang.Long.max(stats2.syncSucceededDate, stats.syncSucceededDate),
                    java.lang.Long.max(stats2.itemDate, stats.itemDate)
            )
        }

        fun fromOldestDates(timelines: Stream<Timeline>): SyncStats {
            return timelines.reduce(SyncStats(0, 0), {
                stats: SyncStats, timeline: Timeline -> oldestAccumulator(stats, timeline) },
                    { stats: SyncStats, stats2: SyncStats -> oldestCombiner(stats, stats2) })
        }

        private fun oldestAccumulator(stats: SyncStats, timeline: Timeline): SyncStats {
            return SyncStats(
                    java.lang.Long.max(timeline.getSyncSucceededDate(), stats.syncSucceededDate),
                    RelativeTime.minDate(timeline.getOldestItemDate(), stats.itemDate)
            )
        }

        private fun oldestCombiner(stats: SyncStats, stats2: SyncStats): SyncStats {
            return SyncStats(
                    java.lang.Long.max(stats2.syncSucceededDate, stats.syncSucceededDate),
                    RelativeTime.minDate(stats2.itemDate, stats.itemDate)
            )
        }
    }
}