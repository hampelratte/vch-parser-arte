package de.berlios.vch.parser.arte;

public class ArteVideo {
    public int width;
    public int bitrate;
    public String format;
    public String type;
    public String uri;
    public String desc;

    @Override
    public String toString() {
        return width + " " + bitrate + "kbps " + format + " " + uri;
    }
}
