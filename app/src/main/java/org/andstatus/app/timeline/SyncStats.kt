package org.andstatus.app.timeline;

import org.andstatus.app.timeline.meta.Timeline;

import java.util.stream.Stream;

import static org.andstatus.app.util.RelativeTime.minDate;

/**
 *
 */
class SyncStats {
    final long syncSucceededDate;
    final long itemDate;

    static SyncStats fromYoungestDates(Stream<Timeline> timelines) {
        return timelines.reduce(new SyncStats(0, 0),
                SyncStats::youngestAccumulator, SyncStats::youngestCombiner);
    }

    private SyncStats(long syncSucceededDate, long itemDate) {
        this.syncSucceededDate = syncSucceededDate;
        this.itemDate = itemDate;
    }

    private static SyncStats youngestAccumulator(SyncStats stats, Timeline timeline) {
        return new SyncStats(
                Long.max(timeline.getSyncSucceededDate(), stats.syncSucceededDate),
                Long.max(timeline.getYoungestItemDate(), stats.itemDate)
        );
    }

    private static SyncStats youngestCombiner(SyncStats stats, SyncStats stats2) {
        return new SyncStats(
                Long.max(stats2.syncSucceededDate, stats.syncSucceededDate),
                Long.max(stats2.itemDate, stats.itemDate)
        );
    }

    static SyncStats fromOldestDates(Stream<Timeline> timelines) {
        return timelines.reduce(new SyncStats(0, 0),
                SyncStats::oldestAccumulator, SyncStats::oldestCombiner);
    }

    private static SyncStats oldestAccumulator(SyncStats stats, Timeline timeline) {
        return new SyncStats(
                Long.max(timeline.getSyncSucceededDate(), stats.syncSucceededDate),
                minDate(timeline.getOldestItemDate(), stats.itemDate)
        );
    }

    private static SyncStats oldestCombiner(SyncStats stats, SyncStats stats2) {
        return new SyncStats(
                Long.max(stats2.syncSucceededDate, stats.syncSucceededDate),
                minDate(stats2.itemDate, stats.itemDate)
        );
    }
}
