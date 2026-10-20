package com.waris4ly.soundy;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.arbjerg.lavalink.api.AudioPluginInfoModifier;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

import static kotlinx.serialization.json.JsonElementKt.JsonPrimitive;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Exposes SoundCloud artwork in the track JSON the same way LavaSrc does for
 * its sources: Lavalink merges the artworkUrl key into the track info that
 * clients (VoiceLink and friends) read for thumbnails.
 */
@Service
public class SoundyPluginInfoModifier implements AudioPluginInfoModifier {

    @Override
    public JsonObject modifyAudioTrackPluginInfo(AudioTrack track) {
        if (!(track.getInfo() instanceof SoundyAudioTrackInfo)) {
            return null;
        }

        String artworkUrl = ((SoundyAudioTrackInfo) track.getInfo()).artworkUrl;
        if (artworkUrl == null || artworkUrl.isEmpty()) {
            return null;
        }

        Map<String, JsonElement> fields = new HashMap<>();
        fields.put("artworkUrl", JsonPrimitive(artworkUrl));
        return new JsonObject(fields);
    }

    @Override
    public JsonObject modifyAudioPlaylistPluginInfo(AudioPlaylist playlist) {
        return null;
    }
}
