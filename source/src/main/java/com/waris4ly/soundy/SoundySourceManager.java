package com.waris4ly.soundy;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SoundySourceManager implements AudioSourceManager {

    private static final Pattern SOUNDCLOUD_URL = Pattern.compile(
            "^https?://(?:www\\.)?soundcloud\\.com/.+");
    private static final String SEARCH_PREFIX = "scsearch:";

    private final SoundCloudService service;
    private final HttpInterfaceManager httpInterfaceManager;

    public SoundySourceManager() {
        ClientIdProvider clientIdProvider = new ClientIdProvider();
        SoundCloudHttpClient soundCloudHttpClient = new SoundCloudHttpClient(clientIdProvider);
        this.service = new SoundCloudService(soundCloudHttpClient);
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    public SoundCloudService getService() {
        return service;
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    @Override
    public String getSourceName() {
        return "soundcloud";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;

        if (identifier.startsWith(SEARCH_PREFIX)) {
            return loadSearch(identifier.substring(SEARCH_PREFIX.length()).trim());
        }

        if (SOUNDCLOUD_URL.matcher(identifier).matches()) {
            return loadFromUrl(identifier);
        }

        return null;
    }

    private AudioItem loadFromUrl(String url) {
        try (HttpInterface http = getHttpInterface()) {
            Object resolved = service.resolveUrl(http, url);

            if (resolved instanceof TrackData track) {
                return buildTrack(track);
            }

            if (resolved instanceof PlaylistData playlist) {
                List<AudioTrack> tracks = playlist.getTracks().stream()
                        .filter(TrackData::isStreamable)
                        .map(this::buildTrack)
                        .collect(Collectors.toList());
                return tracks.isEmpty() ? AudioReference.NO_TRACK
                        : new BasicAudioPlaylist(playlist.getTitle(), tracks, null, false);
            }

            return AudioReference.NO_TRACK;
        } catch (IOException e) {
            throw new FriendlyException("Failed to load SoundCloud URL: " + url,
                    FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioItem loadSearch(String query) {
        if (query.isEmpty()) {
            return AudioReference.NO_TRACK;
        }
        try (HttpInterface http = getHttpInterface()) {
            List<TrackData> results = service.search(http, query);
            if (results.isEmpty()) {
                return AudioReference.NO_TRACK;
            }
            List<AudioTrack> tracks = results.stream()
                    .filter(TrackData::isStreamable)
                    .map(this::buildTrack)
                    .collect(Collectors.toList());
            return tracks.isEmpty() ? AudioReference.NO_TRACK
                    : new BasicAudioPlaylist("Search results for: " + query, tracks, null, true);
        } catch (IOException e) {
            throw new FriendlyException("Search failed for: " + query,
                    FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private AudioTrack buildTrack(TrackData data) {
        SoundyAudioTrackInfo info = new SoundyAudioTrackInfo(
                data.getTitle(),
                data.getUsername(),
                data.getDuration(),
                String.valueOf(data.getId()),
                data.getPermalinkUrl(),
                data.getId(),
                data.getArtworkUrl()
        );
        return new SoundyAudioTrack(info, this);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        SoundyAudioTrackInfo info = (SoundyAudioTrackInfo) track.getInfo();
        output.writeLong(info.getTrackId());
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo info, DataInput input) throws IOException {
        long trackId = input.readLong();
        SoundyAudioTrackInfo soundyInfo = new SoundyAudioTrackInfo(
                info.title, info.author, info.length, info.identifier, info.uri,
                trackId, info.artworkUrl);
        return new SoundyAudioTrack(soundyInfo, this);
    }

    @Override
    public void shutdown() {
        try {
            httpInterfaceManager.close();
        } catch (Exception ignored) {}
    }
}
