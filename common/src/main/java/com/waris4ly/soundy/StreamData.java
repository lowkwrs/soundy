package com.waris4ly.soundy;

public class StreamData {

	public enum Protocol {
		PROGRESSIVE, HLS_FMP4
	}

	private final String url;
	private final Protocol protocol;
	private final String mimeType;

	public StreamData(String url, Protocol protocol, String mimeType) {
		this.url = url;
		this.protocol = protocol;
		this.mimeType = mimeType;
	}

	public String getUrl() {
		return url;
	}

	public Protocol getProtocol() {
		return protocol;
	}

	public String getMimeType() {
		return mimeType;
	}
}
