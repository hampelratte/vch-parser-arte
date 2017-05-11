package de.berlios.vch.parser.arte;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class ArteVideoComparator implements Comparator<ArteVideo> {

    private Map<String, Integer> formatPriorities = new HashMap<String, Integer>();

    public ArteVideoComparator() {
        // @formatter:off
        formatPriorities.put("hls",  0);
        formatPriorities.put("rtmp", 1);
        formatPriorities.put("mp4", 2);
        // @formatter:on
    }

    @Override
    public int compare(ArteVideo a1, ArteVideo a2) {
        if (a1.width > a2.width) {
            return 1;
        } else if (a1.width < a2.width) {
            return -1;
        } else if (a1.bitrate > a2.bitrate) {
            return 1;
        } else if (a1.bitrate < a2.bitrate) {
            return -1;
        }

        // check the format
        Integer format1 = getPriority(a1.format);
        Integer format2 = getPriority(a2.format);
        if (format1 > format2) {
            return 1;
        } else if (format1 < format2) {
            return -1;
        }

        return 0;
    }

    private int getPriority(String format) {
        Integer priority = formatPriorities.get(format);
        return priority != null ? priority : -1;
    }

}
