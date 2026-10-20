package com.waris4ly.soundy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoundyAudioTrackSeekMappingTest {

    private static SoundyHlsPlaylistParser.Segment segment(Long durationMs) {
        return new SoundyHlsPlaylistParser.Segment("https://seg.example/" + durationMs + ".m4s", durationMs);
    }

    @Test
    void mapsPositionToContainingSegment() {
        List<SoundyHlsPlaylistParser.Segment> segments =
                Arrays.asList(segment(10007L), segment(9984L), segment(1407L));

        assertEquals(0, SoundyAudioTrack.segmentIndexFor(segments, 0));
        assertEquals(0, SoundyAudioTrack.segmentIndexFor(segments, 10006));
        assertEquals(1, SoundyAudioTrack.segmentIndexFor(segments, 10007));
        assertEquals(1, SoundyAudioTrack.segmentIndexFor(segments, 19990));
        assertEquals(2, SoundyAudioTrack.segmentIndexFor(segments, 19991));
    }

    @Test
    void beyondTotalLandsOnLastSegmentNotPastEnd() {
        List<SoundyHlsPlaylistParser.Segment> segments =
                Arrays.asList(segment(10007L), segment(9984L));

        // Metadata duration can exceed the EXTINF sum; must not return
        // segments.size(), which would open an init-only chain.
        assertEquals(1, SoundyAudioTrack.segmentIndexFor(segments, 19991));
        assertEquals(1, SoundyAudioTrack.segmentIndexFor(segments, 60000));
    }

    @Test
    void unknownDurationStopsFurtherMapping() {
        List<SoundyHlsPlaylistParser.Segment> segments =
                Arrays.asList(segment(10007L), segment(null), segment(1000L));

        assertEquals(1, SoundyAudioTrack.segmentIndexFor(segments, 15000));
    }

    @Test
    void emptyPlaylistMapsToZero() {
        assertEquals(0, SoundyAudioTrack.segmentIndexFor(Collections.emptyList(), 0));
        assertEquals(0, SoundyAudioTrack.segmentIndexFor(Collections.emptyList(), 12345));
    }

    @Test
    void totalDurationSumsKnownDurations() {
        List<SoundyHlsPlaylistParser.Segment> segments =
                Arrays.asList(segment(10007L), segment(null), segment(1407L));

        assertEquals(11414L, SoundyAudioTrack.totalDurationMs(segments));
        assertEquals(0L, SoundyAudioTrack.totalDurationMs(Collections.emptyList()));
    }
}
