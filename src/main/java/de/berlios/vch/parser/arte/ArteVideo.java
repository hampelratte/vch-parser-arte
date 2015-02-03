package de.berlios.vch.parser.arte;

public class ArteVideo {
    int width;
    int bitrate;
    String format;
    String streamer;
    String uri;

    @Override
    public String toString() {
        return width + " " + bitrate + "kbps " + format + " " + streamer + " " + uri;
    }
}
