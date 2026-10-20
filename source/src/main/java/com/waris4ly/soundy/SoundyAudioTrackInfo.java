package com.waris4ly.soundy;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class SoundyAudioTrackInfo extends AudioTrackInfo {

    private final long trackId;

    public SoundyAudioTrackInfo(String title, String author, long length,
                                String identifier, String uri, long trackId,
                                String artworkUrl) {
        super(title, author, length, identifier, false, uri, artworkUrl, null);
        this.trackId = trackId;
    }

    public long getTrackId() {
        return trackId;
    }
}
