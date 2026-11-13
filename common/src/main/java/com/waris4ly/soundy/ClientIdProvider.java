package com.waris4ly.soundy;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientIdProvider {

	private static final String SOUNDCLOUD_HOME = "https://soundcloud.com";
	private static final Pattern SCRIPT_SRC = Pattern
			.compile("<script[^>]+src=\"(https://a-v2\\.sndcdn\\.com/assets/[^\"]+\\.js)\"");
	private static final Pattern CLIENT_ID_PATTERN = Pattern
			.compile("client_id[\"']?\\s*[:=]\\s*[\"']([a-zA-Z0-9]{32})[\"']");
	private static final long CACHE_TTL_MS = 6 * 60 * 60 * 1000L;
	private static final long FETCH_TIMEOUT_SECONDS = 30L;

	private final AtomicReference<CompletableFuture<String>> inflight = new AtomicReference<>(null);

	private volatile String cached;
	private volatile long cacheTime = 0;

	public void invalidate() {
		cached = null;
		cacheTime = 0;
	}

	public String getClientId(HttpInterface http) throws IOException {
		String c = cached;
		if (c != null && System.currentTimeMillis() - cacheTime < CACHE_TTL_MS) {
			return c;
		}

		CompletableFuture<String> mine = new CompletableFuture<>();
		CompletableFuture<String> existing = inflight.compareAndExchange(null, mine);

		if (existing != null) {
			try {
				return existing.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new IOException("client_id fetch failed", e);
			}
		}

		try {
			String id = fetchClientId(http);
			cached = id;
			cacheTime = System.currentTimeMillis();
			mine.complete(id);
			return id;
		} catch (IOException e) {
			mine.completeExceptionally(e);
			throw e;
		} finally {
			inflight.set(null);
		}
	}

	private String fetchClientId(HttpInterface http) throws IOException {
		String html = get(http, SOUNDCLOUD_HOME);
		Matcher scriptMatcher = SCRIPT_SRC.matcher(html);
		List<String> scripts = new ArrayList<>();
		while (scriptMatcher.find()) {
			scripts.add(scriptMatcher.group(1));
		}
		if (scripts.isEmpty()) {
			throw new IOException("No SoundCloud asset scripts found in homepage");
		}
		for (int i = scripts.size() - 1; i >= 0; i--) {
			String scriptUrl = scripts.get(i);
			String js = get(http, scriptUrl);
			Matcher idMatcher = CLIENT_ID_PATTERN.matcher(js);
			if (idMatcher.find()) {
				return idMatcher.group(1);
			}
		}
		throw new IOException("client_id not found in any of " + scripts.size() + " scripts");
	}

	private String get(HttpInterface http, String url) throws IOException {
		HttpGet request = new HttpGet(url);
		request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
		request.setHeader("Accept", "text/html,application/xhtml+xml,*/*;q=0.8");

		try (CloseableHttpResponse response = http.execute(request)) {
			int status = response.getStatusLine().getStatusCode();
			if (status < 200 || status >= 300) {
				throw new IOException("HTTP " + status + " from: " + url);
			}
			return EntityUtils.toString(response.getEntity());
		}
	}
}
