package com.waris4ly.soundy;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.springframework.stereotype.Service;

@Service
public class SoundyPlugin implements AudioPlayerManagerConfiguration {

	private final PluginConfig config;

	public SoundyPlugin(PluginConfig config) {
		this.config = config;
	}

	@Override
	public AudioPlayerManager configure(AudioPlayerManager manager) {
		if (config.isEnabled()) {
			manager.registerSourceManager(new SoundySourceManager());
		}
		return manager;
	}
}
