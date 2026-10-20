package com.waris4ly.soundy;

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plays a real SoundCloud track end to end and counts decoded frames. SoundCloud
 * serves fragmented-MP4 HLS only, so this exercises the full new path: client_id
 * harvest, track resolve, transcoding selection, playlist parse, chained
 * init+segment download and fMP4 decoding.
 *
 * Network test: enable with SOUNDY_IT=1.
 */
@EnabledIfEnvironmentVariable(named = "SOUNDY_IT", matches = "1")
class SoundCloudPlaybackIT {

    private static final String TRACK_URL =
            System.getProperty("soundy.it.track", "https://soundcloud.com/jhfly/poetic-justice");

    private static final long MIN_FRAMES = Long
            .parseLong(System.getProperty("soundy.it.minFrames", "50")); // 50 frames = 1s of audio

    @Test
    void playsFragmentedMp4HlsTrack() throws Exception {
        AudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.registerSourceManager(new SoundySourceManager());
        manager.getConfiguration().setOpusEncodingQuality(AudioConfiguration.OPUS_QUALITY_MAX);

        CompletableFuture<AudioItem> loaded = new CompletableFuture<>();
        manager.loadItem(new AudioReference(TRACK_URL, null), resultHandler(loaded));

        AudioItem item = loaded.get(60, TimeUnit.SECONDS);
        assertTrue(item instanceof AudioTrack, "expected a track, got: " + item.getClass());
        AudioTrack track = (AudioTrack) item;
        assertNotNull(track.getInfo().title, "track should have a title");
        assertTrue(track.getDuration() > 1000, "track should have a duration");

        AudioPlayer player = manager.createPlayer();
        player.playTrack(track);

        long frames = 0;
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);
        while (frames < MIN_FRAMES && System.currentTimeMillis() < deadline) {
            AudioFrame frame = player.provide();
            if (frame != null && frame.getDataLength() > 0) {
                frames++;
            } else {
                Thread.sleep(5);
            }
        }

        player.stopTrack();
        manager.shutdown();

        assertTrue(frames >= MIN_FRAMES,
                "expected at least " + MIN_FRAMES + " decoded frames, got " + frames);
    }

    @Test
    void seekSurvivesSegmentRestart() throws Exception {
        AudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.registerSourceManager(new SoundySourceManager());

        CompletableFuture<AudioItem> loaded = new CompletableFuture<>();
        manager.loadItem(new AudioReference(TRACK_URL, null), resultHandler(loaded));
        AudioItem item = loaded.get(60, TimeUnit.SECONDS);
        assertTrue(item instanceof AudioTrack, "expected a track, got: " + item.getClass());

        AudioPlayer player = manager.createPlayer();
        AudioTrack track = (AudioTrack) item;
        track.setPosition(100); // let a few frames decode first
        player.playTrack(track);

        long frames = 0;
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        while (frames < 10 && System.currentTimeMillis() < deadline) {
            if (player.provide() != null) frames++; else Thread.sleep(5);
        }

        track.setPosition(TimeUnit.SECONDS.toMillis(150));

        long framesAfterSeek = 0;
        long maxPosition = 0;
        deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);
        while (framesAfterSeek < 50 && System.currentTimeMillis() < deadline) {
            if (player.provide() != null) {
                framesAfterSeek++;
                maxPosition = Math.max(maxPosition, track.getPosition());
            } else {
                Thread.sleep(5);
            }
        }

        player.stopTrack();
        manager.shutdown();

        assertTrue(framesAfterSeek >= 50,
                "expected frames to continue after seek, got " + framesAfterSeek);
        assertTrue(maxPosition >= TimeUnit.SECONDS.toMillis(149),
                "position after seek should be >= 149s, was " + maxPosition + "ms");
    }

    @Test
    void searchesSoundCloud() throws Exception {
        AudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.registerSourceManager(new SoundySourceManager());

        CompletableFuture<AudioItem> loaded = new CompletableFuture<>();
        manager.loadItem(new AudioReference("scsearch:brent faiyaz", null), resultHandler(loaded));

        try {
            AudioItem item = loaded.get(60, TimeUnit.SECONDS);
            assertNotNull(item, "scsearch should return a playlist");
        } catch (Exception e) {
            throw new AssertionError("scsearch failed", e instanceof FriendlyException ? e.getCause() : e);
        } finally {
            manager.shutdown();
        }
    }

    private static AudioLoadResultHandler resultHandler(CompletableFuture<AudioItem> future) {
        return new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                future.complete(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                future.complete(playlist);
            }

            @Override
            public void noMatches() {
                future.complete(null);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.completeExceptionally(exception);
            }
        };
    }
}
