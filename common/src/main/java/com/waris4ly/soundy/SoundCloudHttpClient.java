package com.waris4ly.soundy;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class SoundCloudHttpClient {

	private static final String API_BASE = "https://api-v2.soundcloud.com";
	private static final int MAX_RETRIES = 3;
	private static final long BACKOFF_BASE_MS = 500;
	private static final long MAX_JITTER_MS = 200;

	private final ClientIdProvider clientIdProvider;

	public SoundCloudHttpClient(ClientIdProvider clientIdProvider) {
		this.clientIdProvider = clientIdProvider;
	}

	public String get(HttpInterface http, String path) throws IOException {
		String sep = path.contains("?") ? "&" : "?";
		return executeWithRetry(http, API_BASE + path + sep + "client_id=");
	}

	public String getAbsolute(HttpInterface http, String url) throws IOException {
		String sep = url.contains("?") ? "&" : "?";
		return executeWithRetry(http, url + sep + "client_id=");
	}

	private String executeWithRetry(HttpInterface http, String urlBase) throws IOException {
		IOException lastException = null;

		for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
			if (attempt > 0) {
				long delay = BACKOFF_BASE_MS * (1L << (attempt - 1))
						+ ThreadLocalRandom.current().nextLong(MAX_JITTER_MS);
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException("Interrupted during backoff", e);
				}
			}

			String url = urlBase + clientIdProvider.getClientId(http);
			HttpGet request = new HttpGet(url);
			request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
			request.setHeader("Accept", "application/json, */*;q=0.8");
			request.setHeader("Referer", "https://soundcloud.com/");
			request.setHeader("Origin", "https://soundcloud.com");

			try (CloseableHttpResponse response = http.execute(request)) {
				int status = response.getStatusLine().getStatusCode();

				if (status == 200) {
					return EntityUtils.toString(response.getEntity());
				}

				lastException = new IOException("SoundCloud API HTTP " + status + " for: " + url);

				if (status == 401 || status == 403) {
					clientIdProvider.invalidate();
					continue;
				}

				if (status == 429 || status >= 500) {
					continue;
				}

				throw lastException;
			}
		}

		throw lastException != null
				? lastException
				: new IOException("SoundCloud request failed after " + MAX_RETRIES + " retries");
	}
}
