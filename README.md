# soundy-plugin
A SoundCloud source manager for Lavaplayer and Lavalink.

SoundCloud dropped their old progressive MP3 streams and switched entirely to fragmented MP4 (AAC) HLS. This source adds proper support for those streams so playback, seeking, and playlists work without issues.

## Table of Contents
- [Common](#common)
  - Information about the `common` module and usage.
- [Source](#source)
  - Information about the `source` module and usage.
- [Plugin](#plugin)
  - Information about the `plugin` module and usage.
- [Configuration](#configuration)
  - Information on configuring the plugin in Lavalink.
- [Usage](#usage)
  - Supported URL formats and search syntax.
- [Building](#building)
  - Instructions for building from source.
- [Support](#support)
  - Where to get help or report issues.

## common
This module provides the core HTTP client, metadata parsers, and dynamic client ID retrieval.

<details>
<summary>Using in Gradle:</summary>

```kotlin
repositories {
  maven("https://maven.lavalink.dev/releases")
}

dependencies {
  // Replace VERSION with the current version from the Releases tab
  implementation("com.waris4ly.soundy:common:VERSION")
}
```

</details>

## source
This module provides the source manager for use with Lavaplayer.

<details>
<summary>Using in Gradle:</summary>

```kotlin
repositories {
  maven("https://maven.lavalink.dev/releases")
}

dependencies {
  // Replace VERSION with the current version from the Releases tab
  implementation("com.waris4ly.soundy:source:VERSION")
}
```

</details>

Example usage:

```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
playerManager.registerSourceManager(new SoundySourceManager());
```

## plugin
This module serves as the plugin for use with [Lavalink](https://github.com/lavalink-devs/Lavalink).

To use this plugin with Lavalink, declare the dependency in your `application.yml`:

<details>
<summary>Using with Lavalink v4:</summary>

```yaml
lavalink:
  plugins:
    # Replace VERSION with the current version from the Releases tab
    - dependency: "com.waris4ly.soundy:plugin:VERSION"
      repository: "https://maven.lavalink.dev/releases"
```

</details>

Alternatively, download the `soundy-plugin-x.x.x.jar` from the [Releases](https://github.com/lowkwrs/soundy-plugin/releases) page and place it into your `plugins` directory:

```
lavalink/
├── Lavalink.jar
├── application.yml
└── plugins/
    └── soundy-plugin-x.x.x.jar
```

## Configuration
To configure the plugin, add the `soundy` block to your `application.yml`:

```yaml
plugins:
  soundy:
    enabled: true # Set to false to disable the source without removing the jar
```

## Usage

### Tracks
Standard desktop and mobile URLs are supported:
- `https://soundcloud.com/artist/track`
- `https://m.soundcloud.com/artist/track`

### Playlists
SoundCloud sets and playlists are supported:
- `https://soundcloud.com/artist/sets/playlist`

SoundCloud returns only the first 5 tracks in initial responses. Soundy fetches the remaining tracks in batches of 50, supporting up to 500 tracks per playlist.

### Searching
You can search SoundCloud using the `scsearch:` prefix:
- `scsearch:song title`

Returns up to 10 matching tracks formatted as a search playlist.

## Building
To build the project from source:

```bash
./gradlew build
```

The plugin jar will be generated at:
```
plugin/build/libs/soundy-plugin-x.x.x.jar
```

To run all tests including integration tests against live SoundCloud endpoints:
```bash
SOUNDY_IT=1 ./gradlew :source:test
```

## Support
If you encounter any bugs, have questions, or need assistance, please open an issue on GitHub.
