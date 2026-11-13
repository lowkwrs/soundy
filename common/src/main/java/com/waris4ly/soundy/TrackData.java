package com.waris4ly.soundy;

public class TrackData {

	private final long id;
	private final String title;
	private final String permalinkUrl;
	private final long duration;
	private final String artworkUrl;
	private final String username;
	private final boolean streamable;

	public TrackData(long id, String title, String permalinkUrl, long duration, String artworkUrl, String username,
			boolean streamable) {
		this.id = id;
		this.title = title;
		this.permalinkUrl = permalinkUrl;
		this.duration = duration;
		this.artworkUrl = artworkUrl;
		this.username = username;
		this.streamable = streamable;
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

	public long getDuration() {
		return duration;
	}

	public String getArtworkUrl() {
		return artworkUrl;
	}

	public String getUsername() {
		return username;
	}

	public boolean isStreamable() {
		return streamable;
	}
}
