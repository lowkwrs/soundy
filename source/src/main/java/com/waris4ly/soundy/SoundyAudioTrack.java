package com.waris4ly.soundy;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAacTrackConsumer;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegFileLoader;
import com.sedmelluq.discord.lavaplayer.container.mpeg.reader.MpegFileTrackProvider;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegTrackConsumer;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegTrackInfo;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.http.HttpStreamTools;
import com.sedmelluq.discord.lavaplayer.tools.io.ChainedInputStream;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.NonSeekableInputStream;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SoundyAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(SoundyAudioTrack.class);

    private final SoundySourceManager sourceManager;

    public SoundyAudioTrack(SoundyAudioTrackInfo trackInfo, SoundySourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        SoundyAudioTrackInfo info = (SoundyAudioTrackInfo) trackInfo;

        // Stream URLs are signed and expire, so they are resolved on every play
        // rather than cached.
        StreamData stream;
        try (HttpInterface http = sourceManager.getHttpInterface()) {
            stream = sourceManager.getService().getStream(http, info.getTrackId());
        }
        log.info("Track {} resolved to {} stream ({})", info.getTrackId(),
                stream.getProtocol(), stream.getMimeType());

        if (stream.getProtocol() == StreamData.Protocol.PROGRESSIVE) {
            processProgressive(stream, executor);
        } else {
            processHlsFmp4(stream, executor);
        }
    }

    private void processProgressive(StreamData stream, LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            try (PersistentHttpStream httpStream = new PersistentHttpStream(
                    httpInterface, new URI(stream.getUrl()), Long.MAX_VALUE)) {
                processDelegate(new Mp3AudioTrack(trackInfo, httpStream), executor);
            }
        }
    }

    /**
     * SoundCloud's remaining format is fragmented-MP4 HLS: a media playlist whose
     * EXT-X-MAP init segment (ftyp + moov) is followed by moof + mdat fragments.
     * Concatenated in playlist order they form a regular fragmented MP4 stream,
     * which lavaplayer's MPEG container reader decodes.
     *
     * Seeking restarts the stream at the segment containing the target position
     * (mapped via cumulative EXTINF durations); MP4 fragment timecodes are
     * absolute, so position reporting stays correct.
     */
    private void processHlsFmp4(StreamData stream, LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            SoundyHlsPlaylistParser.Playlist playlist = SoundyHlsPlaylistParser.parse(
                    httpInterface, new URI(stream.getUrl()));
            log.info("Parsed HLS playlist for track {}: init segment present = {}, {} segments",
                    trackInfo.identifier, playlist.initUrl != null, playlist.segments.size());

            Fmp4Player player = new Fmp4Player(httpInterface, playlist, executor);
            try {
                executor.executeProcessingLoop(player::provideFrames, player::performSeek, true);
            } finally {
                player.close();
            }
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new SoundyAudioTrack((SoundyAudioTrackInfo) trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }

    /**
     * Maps a position to the segment containing it. Positions beyond the last
     * known duration land on the last segment (play the tail) rather than past
     * the end, which would open an init-only chain that EOFs immediately.
     */
    static int segmentIndexFor(List<SoundyHlsPlaylistParser.Segment> segments, long positionMs) {
        if (segments.isEmpty()) {
            return 0;
        }

        long cumulativeMs = 0;
        for (int i = 0; i < segments.size(); i++) {
            Long duration = segments.get(i).durationMs;
            if (duration == null) {
                // Without a duration we cannot map positions any further.
                return i;
            }
            cumulativeMs += duration;
            if (positionMs < cumulativeMs) {
                return i;
            }
        }
        return segments.size() - 1;
    }

    /**
     * Sums the known EXTINF durations; unknown durations count as zero.
     */
    static long totalDurationMs(List<SoundyHlsPlaylistParser.Segment> segments) {
        long totalMs = 0;
        for (SoundyHlsPlaylistParser.Segment segment : segments) {
            if (segment.durationMs != null) {
                totalMs += segment.durationMs;
            }
        }
        return totalMs;
    }

    /**
     * Drives lavaplayer's MPEG container reader over the chained init+segment
     * stream and rebuilds that chain on seek.
     */
    private class Fmp4Player {
        private final HttpInterface httpInterface;
        private final SoundyHlsPlaylistParser.Playlist playlist;
        private final LocalAudioTrackExecutor executor;

        private ChainedInputStream chain;
        private MpegTrackConsumer consumer;
        private MpegFileTrackProvider reader;
        private Fmp4StreamProvider provider;

        private Fmp4Player(HttpInterface httpInterface,
                           SoundyHlsPlaylistParser.Playlist playlist,
                           LocalAudioTrackExecutor executor) {
            this.httpInterface = httpInterface;
            this.playlist = playlist;
            this.executor = executor;
        }

        private void provideFrames() throws Exception {
            ensureOpened();
            reader.provideFrames();
        }

        private void performSeek(long positionMs) throws Exception {
            long duration = trackInfo.length;
            long clamped = Math.max(0, positionMs);

            // Some clients send bogus positions on play (e.g. VoiceLink forwards the
            // track identifier). Seeking past the end would open a chain that EOFs
            // immediately, so out-of-range seeks fall back to the track start.
            long effectiveEnd = duration > 0
                    ? duration
                    : totalDurationMs(playlist.segments);
            if (effectiveEnd > 0 && clamped >= effectiveEnd) {
                log.warn("Track {}: clamping out-of-range seek {} ms (duration {} ms) to start",
                        trackInfo.identifier, positionMs, effectiveEnd);
                clamped = 0;
            }

            log.debug("Seeking track {} to {} ms", trackInfo.identifier, clamped);
            openChain(segmentIndexFor(playlist.segments, clamped), clamped);
        }

        private void ensureOpened() throws Exception {
            if (reader == null) {
                openChain(0, 0);
            }
        }

        private void openChain(int segmentIndex, long positionMs) throws Exception {
            closeChain();

            if (playlist.initUrl == null) {
                throw new FriendlyException("HLS playlist has no EXT-X-MAP init segment.",
                        FriendlyException.Severity.SUSPICIOUS, null);
            }

            provider = new Fmp4StreamProvider(sourceManager, playlist, segmentIndex);
            chain = new ChainedInputStream(provider);

            MpegFileLoader loader = new MpegFileLoader(new NonSeekableInputStream(chain));
            loader.parseHeaders();

            consumer = selectAudioTrack(loader.getTrackList());
            if (consumer == null) {
                throw new FriendlyException("No supported audio track in SoundCloud stream.",
                        FriendlyException.Severity.SUSPICIOUS, null);
            }
            consumer.initialise();

            // The fragmented provider only reports absolute fragment timecodes
            // via seekPerformed when it has a seek index (sidx); SoundCloud
            // playlists carry none. Seed the consumer with the chain's start
            // position so the output pipeline does not restart its timecode at
            // zero, which made clients' progress displays rewind after seeks.
            long segmentStartMs = totalDurationMs(
                    playlist.segments.subList(0, Math.min(segmentIndex, playlist.segments.size())));
            consumer.seekPerformed(positionMs, segmentStartMs);

            reader = loader.loadReader(consumer);
            if (reader == null) {
                throw new FriendlyException("Unknown MP4 format.", FriendlyException.Severity.SUSPICIOUS, null);
            }
        }

        private MpegTrackConsumer selectAudioTrack(List<MpegTrackInfo> tracks) {
            for (MpegTrackInfo track : tracks) {
                if ("soun".equals(track.handler) && "mp4a".equals(track.codecName)) {
                    log.debug("Starting to play track with codec {}", track.codecName);
                    return new MpegAacTrackConsumer(executor.getProcessingContext(), track);
                }
            }
            return null;
        }

        private void closeChain() {
            if (provider != null) {
                provider.close();
                provider = null;
            }
            if (consumer != null) {
                try {
                    consumer.close();
                } catch (Exception e) {
                    log.debug("Failed to close track consumer.", e);
                }
                consumer = null;
            }
            reader = null;
            chain = null;
        }

        private void close() {
            closeChain();
        }
    }

    /**
     * Serves the init segment first, then the media segments in playlist order
     * starting from the given index, fully buffered. While one resource plays,
     * the next one is prefetched on a background thread so segment boundaries
     * do not stall playback. Returning null terminates the chained stream.
     */
    private static class Fmp4StreamProvider implements ChainedInputStream.Provider, AutoCloseable {
        private final SoundySourceManager sourceManager;
        private final SoundyHlsPlaylistParser.Playlist playlist;
        private final int startSegment;
        private final ExecutorService prefetchExecutor;

        private int cursor = 0;
        private Future<byte[]> pending;

        private Fmp4StreamProvider(SoundySourceManager sourceManager,
                                   SoundyHlsPlaylistParser.Playlist playlist,
                                   int startSegment) {
            this.sourceManager = sourceManager;
            this.playlist = playlist;
            this.startSegment = startSegment;
            this.prefetchExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "soundy-prefetch");
                thread.setDaemon(true);
                return thread;
            });
        }

        @Override
        public InputStream next() throws IOException {
            String url = urlFor(cursor);
            if (url == null) {
                return null;
            }

            byte[] data;
            Future<byte[]> prefetched = pending;
            pending = null;

            if (prefetched != null) {
                try {
                    data = prefetched.get();
                } catch (ExecutionException e) {
                    throw new IOException("Prefetch failed for " + url, e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Prefetch interrupted", e);
                }
            } else {
                data = fetch(url);
            }

            cursor++;

            String upcoming = urlFor(cursor);
            if (upcoming != null) {
                pending = prefetchExecutor.submit(() -> fetch(upcoming));
            }

            return new ByteArrayInputStream(data);
        }

        private String urlFor(int index) {
            if (index == 0) {
                return playlist.initUrl;
            }
            int segment = startSegment + index - 1;
            if (segment >= playlist.segments.size()) {
                return null;
            }
            return playlist.segments.get(segment).url;
        }

        private byte[] fetch(String url) throws IOException {
            // A thread-local interface per fetch thread; the response is fully
            // buffered before the interface is closed.
            try (HttpInterface http = sourceManager.getHttpInterface()) {
                try (InputStream stream = HttpStreamTools.streamContent(http, new HttpGet(url))) {
                    return stream.readAllBytes();
                }
            }
        }

        @Override
        public void close() {
            prefetchExecutor.shutdownNow();
        }
    }
}
