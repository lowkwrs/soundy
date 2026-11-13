package com.waris4ly.soundy;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SOUNDY_IT", matches = "1")
class SoundCloudPlaylistIT {

	private static final String PLAYLIST_URL = "https://soundcloud.com/37dme5m3a58o/sets/alan-walker";

	@Test
	void loadsPlaylistFromUrl() throws Exception {
		AudioPlayerManager manager = new DefaultAudioPlayerManager();
		manager.registerSourceManager(new SoundySourceManager());

		CompletableFuture<AudioItem> loaded = new CompletableFuture<>();
		manager.loadItem(new AudioReference(PLAYLIST_URL, null), new AudioLoadResultHandler() {
			@Override
			public void trackLoaded(AudioTrack track) {
				loaded.complete(track);
			}
			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				loaded.complete(playlist);
			}
			@Override
			public void noMatches() {
				loaded.complete(null);
			}
			@Override
			public void loadFailed(FriendlyException exception) {
				loaded.completeExceptionally(exception);
			}
		});

		AudioItem item = loaded.get(60, TimeUnit.SECONDS);
		assertNotNull(item, "playlist URL should resolve to something");
		assertInstanceOf(AudioPlaylist.class, item, "expected a playlist");

		AudioPlaylist playlist = (AudioPlaylist) item;
		assertNotNull(playlist.getName(), "playlist should have a name");
		assertFalse(playlist.getTracks().isEmpty(), "playlist should contain tracks");

		assertEquals(145, playlist.getTracks().size());

		manager.shutdown();
	}

	@Test
	void loadsMobileUrl() throws Exception {
		AudioPlayerManager manager = new DefaultAudioPlayerManager();
		manager.registerSourceManager(new SoundySourceManager());

		CompletableFuture<AudioItem> loaded = new CompletableFuture<>();
		manager.loadItem(new AudioReference("https://m.soundcloud.com/alanwalker/faded-slushii-remix-1", null),
				new AudioLoadResultHandler() {
					@Override
					public void trackLoaded(AudioTrack track) {
						loaded.complete(track);
					}
					@Override
					public void playlistLoaded(AudioPlaylist playlist) {
						loaded.complete(playlist);
					}
					@Override
					public void noMatches() {
						loaded.complete(null);
					}
					@Override
					public void loadFailed(FriendlyException exception) {
						loaded.completeExceptionally(exception);
					}
				});

		AudioItem item = loaded.get(60, TimeUnit.SECONDS);
		assertNotNull(item, "mobile URL should resolve to track");
		assertInstanceOf(AudioTrack.class, item);
		AudioTrack track = (AudioTrack) item;
		assertEquals("Faded (SLUSHII Remix)", track.getInfo().title);

		manager.shutdown();
	}

	@Test
	void handlesNonExistentUrlGracefully() throws Exception {
		AudioPlayerManager manager = new DefaultAudioPlayerManager();
		manager.registerSourceManager(new SoundySourceManager());

		CompletableFuture<AudioItem> loaded = new CompletableFuture<>();
		manager.loadItem(
				new AudioReference("https://soundcloud.com/nonexistent-user-1234567/fake-track-not-found", null),
				new AudioLoadResultHandler() {
					@Override
					public void trackLoaded(AudioTrack track) {
						loaded.complete(track);
					}
					@Override
					public void playlistLoaded(AudioPlaylist playlist) {
						loaded.complete(playlist);
					}
					@Override
					public void noMatches() {
						loaded.complete(null);
					}
					@Override
					public void loadFailed(FriendlyException exception) {
						loaded.completeExceptionally(exception);
					}
				});

		AudioItem item = loaded.get(60, TimeUnit.SECONDS);
		assertNull(item, "non-existent URL should call noMatches");

		manager.shutdown();
	}
}
