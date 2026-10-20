package com.waris4ly.soundy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SoundyHlsPlaylistParserTest {

    @Test
    void parsesInitSegmentAndDurations() throws Exception {
        SoundyHlsPlaylistParser.Playlist playlist = parseFixture();

        assertEquals("https://playback.example.cloud/aac_160k/abc/init.mp4?expires=1792333911&Policy=fake",
                playlist.initUrl);
        assertEquals(3, playlist.segments.size());

        assertEquals("https://playback.example.cloud/aac_160k/abc/data000.m4s?expires=1792333911&Policy=fake",
                playlist.segments.get(0).url);
        assertEquals(10007L, playlist.segments.get(0).durationMs);
        assertEquals(9984L, playlist.segments.get(1).durationMs);
        assertEquals(1407L, playlist.segments.get(2).durationMs);
    }

    @Test
    void resolvesRelativeSegmentUrls() throws Exception {
        SoundyHlsPlaylistParser.Playlist playlist = parseFixture();

        assertEquals("https://cdn.example.com/sets/abc/data002.m4s?expires=1792333911&Policy=fake",
                playlist.segments.get(2).url);
    }

    @Test
    void segmentWithoutExtinfHasNullDuration() throws Exception {
        SoundyHlsPlaylistParser.Playlist playlist = SoundyHlsPlaylistParser.parse(
                new String[]{
                        "#EXTM3U",
                        "#EXT-X-MAP:URI=\"https://init.example/init.mp4\"",
                        "https://seg.example/first.m4s",
                        "#EXTINF:garbage,",
                        "https://seg.example/second.m4s"
                },
                URI.create("https://cdn.example.com/playlist.m3u8"));

        assertEquals("https://init.example/init.mp4", playlist.initUrl);
        assertNull(playlist.segments.get(0).durationMs);
        assertNull(playlist.segments.get(1).durationMs);
    }

    private SoundyHlsPlaylistParser.Playlist parseFixture() throws IOException, URISyntaxException {
        String fixture = Files.readString(
                Path.of(getClass().getResource("/fmp4-playlist.m3u8").toURI()),
                StandardCharsets.UTF_8);
        return SoundyHlsPlaylistParser.parse(
                fixture.split("\n"),
                URI.create("https://cdn.example.com/sets/abc/playlist.m3u8"));
    }
}
