package com.waris4ly.soundy;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PlaylistParser {

	private static final Logger log = LoggerFactory.getLogger(PlaylistParser.class);
	private static final int BATCH_SIZE = 50;
	private static final int MAX_FETCHED_TRACKS = 500;

	private final TrackParser trackParser = new TrackParser();

	public PlaylistData parse(JsonBrowser playlist) {
		return parse(null, null, playlist);
	}

	public PlaylistData parse(HttpInterface http, SoundCloudHttpClient client, JsonBrowser playlist) {
		long id = playlist.get("id").asLong(0);
		String title = playlist.get("title").text();
		String permalinkUrl = playlist.get("permalink_url").text();
		String username = playlist.get("user").get("username").text();

		List<Long> orderedTrackIds = new ArrayList<>();
		List<Long> missingIds = new ArrayList<>();
		Map<Long, TrackData> resolvedTracks = new HashMap<>();

		for (JsonBrowser track : playlist.get("tracks").values()) {
			long trackId = track.get("id").asLong(0);
			if (trackId == 0) {
				continue;
			}
			orderedTrackIds.add(trackId);

			if (!track.get("title").isNull() && !track.get("user").isNull()) {
				resolvedTracks.put(trackId, trackParser.parse(track));
			} else {
				missingIds.add(trackId);
			}
		}

		if (http != null && client != null && !missingIds.isEmpty()) {
			int maxMissing = Math.min(missingIds.size(), MAX_FETCHED_TRACKS);
			for (int i = 0; i < maxMissing; i += BATCH_SIZE) {
				List<Long> batch = missingIds.subList(i, Math.min(i + BATCH_SIZE, maxMissing));
				String idsParam = batch.stream().map(String::valueOf).collect(Collectors.joining(","));
				try {
					String json = client.get(http, "/tracks?ids=" + idsParam);
					JsonBrowser list = JsonBrowser.parse(json);
					for (JsonBrowser item : list.values()) {
						TrackData data = trackParser.parse(item);
						resolvedTracks.put(data.getId(), data);
					}
				} catch (Exception e) {
					log.debug("Failed to fetch tracks batch: {}", idsParam, e);
				}
			}
		}

		List<TrackData> tracks = new ArrayList<>();
		for (Long trackId : orderedTrackIds) {
			TrackData track = resolvedTracks.get(trackId);
			if (track != null) {
				tracks.add(track);
			}
		}

		return new PlaylistData(id, title != null && !title.isBlank() ? title : "SoundCloud Playlist",
				permalinkUrl != null ? permalinkUrl : "",
				username != null && !username.isBlank() ? username : "Unknown", tracks);
	}
}
