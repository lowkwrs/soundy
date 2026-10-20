# soundy

A SoundCloud source plugin for [Lavalink](https://github.com/lavalink-devs/Lavalink) built on [lavaplayer](https://github.com/lavalink-devs/lavaplayer).

## Format support

SoundCloud dropped progressive streams and the legacy HLS `audio/mpegurl`
transcoding (its resolver now returns 404). soundy plays the only format
SoundCloud still serves — fragmented-MP4 HLS (`audio/mp4`, AAC) — by streaming
the `EXT-X-MAP` init segment and all media segments through lavaplayer's
fragmented MP4 demuxer. Seeking within a track is currently not supported
(the playlists carry no seek index).

## Table of Contents

- [Plugin](#plugin)
- [Common](#common)
- [Configuration](#configuration)
- [Usage](#usage)

---

## Plugin

Drop the jar into your Lavalink `plugins/` folder.

```
lavalink/
└── plugins/
    └── soundy-plugin-1.1.0.jar
```

Then declare it in your `application.yml`:

```yaml
lavalink:
  plugins:
    - dependency: "com.waris4ly.soundy:plugin:1.1.0"
      repository: "https://maven.lavalink.dev/releases"
```

Or if you are loading it manually from the folder, just add the config block:

```yaml
plugins:
  soundy:
    enabled: true
```

---

## Common

The `common` module can be used standalone with any Lavaplayer setup outside of Lavalink.

```kotlin
repositories {
    maven("https://maven.lavalink.dev/releases")
}

dependencies {
    implementation("com.waris4ly.soundy:common:1.1.0")
}
```

Example usage:

```java
HttpClient http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

ClientIdProvider clientIdProvider = new ClientIdProvider(http);
SoundCloudHttpClient scClient = new SoundCloudHttpClient(http, clientIdProvider);
SoundCloudService service = new SoundCloudService(scClient);

// register with lavaplayer
AudioPlayerManager manager = new DefaultAudioPlayerManager();
manager.registerSourceManager(new SoundySourceManager());
```

---

## Configuration

```yaml
plugins:
  soundy:
    enabled: true  # set to false to disable the plugin without removing the jar
```

---

## Usage

### Play by URL

```
https://soundcloud.com/alanwalker/faded-slushii-remix-1
```

### Search

```
scsearch:alan walker faded
```

Returns up to 10 tracks as a search playlist.

### Playlist

```
https://soundcloud.com/someuser/sets/some-playlist
```

---

## Build from source

```bash
./gradlew build
```

Output:

```
plugin/build/libs/soundy-plugin-1.1.0.jar
```
