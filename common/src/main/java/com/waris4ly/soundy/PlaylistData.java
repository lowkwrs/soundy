package com.waris4ly.soundy;

import java.util.List;

public class PlaylistData {

	private final long id;
	private final String title;
	private final String permalinkUrl;
	private final String username;
	private final List<TrackData> tracks;

	public PlaylistData(long id, String title, String permalinkUrl, String username, List<TrackData> tracks) {
		this.id = id;
		this.title = title;
		this.permalinkUrl = permalinkUrl;
		this.username = username;
		this.tracks = tracks;
	}

	public long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getPermalinkUrl() {
		return permalinkUrl;
	}

	public String getUsername() {
		return username;
	}

	public List<TrackData> getTracks() {
		return tracks;
	}
}
