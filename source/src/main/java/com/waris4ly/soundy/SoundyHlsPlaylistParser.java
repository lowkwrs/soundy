package com.waris4ly.soundy;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal parser for SoundCloud fragmented-MP4 HLS media playlists.
 *
 * Handles the subset of HLS that SoundCloud emits: the mandatory EXT-X-MAP init
 * segment (ftyp + moov), EXTINF durations and (absolute) segment URLs. lavaplayer's
 * built-in HlsStreamSegmentParser cannot be used here because it ignores EXT-X-MAP,
 * which is required to decode fMP4 fragments.
 */
public final class SoundyHlsPlaylistParser {

    private static final Pattern MAP_URI = Pattern.compile("URI=\"([^\"]+)\"");

    private SoundyHlsPlaylistParser() {
    }

    public static final class Segment {
        public final String url;
        public final Long durationMs;

        public Segment(String url, Long durationMs) {
            this.url = url;
            this.durationMs = durationMs;
        }
    }

    public static final class Playlist {
        public final String initUrl;
        public final List<Segment> segments;

        public Playlist(String initUrl, List<Segment> segments) {
            this.initUrl = initUrl;
            this.segments = segments;
        }
    }

    public static Playlist parse(HttpInterface httpInterface, URI playlistUri) throws IOException {
        String[] lines = HttpClientTools.fetchResponseLines(
                httpInterface, new HttpGet(playlistUri), "HLS media playlist");
        return parse(lines, playlistUri);
    }

    static Playlist parse(String[] lines, URI playlistUri) {
        String initUrl = null;
        List<Segment> segments = new ArrayList<>();
        Long pendingDurationMs = null;

        for (String lineText : lines) {
            String line = lineText.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("#")) {
                if (line.startsWith("#EXT-X-MAP:")) {
                    Matcher matcher = MAP_URI.matcher(line);
                    if (matcher.find()) {
                        initUrl = resolveUrl(playlistUri, matcher.group(1));
                    }
                } else if (line.startsWith("#EXTINF:")) {
                    pendingDurationMs = parseDurationMs(line.substring("#EXTINF:".length()));
                }
            } else {
                segments.add(new Segment(resolveUrl(playlistUri, line), pendingDurationMs));
                pendingDurationMs = null;
            }
        }

        return new Playlist(initUrl, segments);
    }

    private static String resolveUrl(URI playlistUri, String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return playlistUri.resolve(url).toString();
    }

    private static Long parseDurationMs(String directiveValue) {
        int commaIndex = directiveValue.indexOf(',');
        String raw = commaIndex >= 0 ? directiveValue.substring(0, commaIndex) : directiveValue;
        try {
            return (long) (Double.parseDouble(raw.trim()) * 1000.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
