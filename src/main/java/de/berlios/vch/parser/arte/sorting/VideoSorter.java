package de.berlios.vch.parser.arte.sorting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.berlios.vch.parser.arte.ArteVideo;
import de.berlios.vch.parser.arte.ArteVideoComparator;

public class VideoSorter {
    private Map<String, Bucket> buckets = new HashMap<String, Bucket>();

    public void sort(List<ArteVideo> videos) {
        // sort videos into buckets
        for (ArteVideo v : videos) {
            Bucket b = buckets.get(v.type);
            if(b == null) {
                b = new Bucket();
                b.prio = getSortPriority(v);
                buckets.put(v.type, b);
            }
            b.videos.add(v);
        }

        // sort the videos in the buckets
        for (Bucket bucket : buckets.values()) {
            Collections.sort(bucket.videos, new ArteVideoComparator());
            Collections.reverse(bucket.videos);
        }

        // sort the buckets
        List<Bucket> sortedBuckets = new ArrayList<Bucket>();
        sortedBuckets.addAll(buckets.values());
        Collections.sort(sortedBuckets, new Comparator<Bucket>() {
            @Override
            public int compare(Bucket o1, Bucket o2) {
                return o2.prio - o1.prio;
            }
        });

        videos.clear();
        for (Bucket bucket : sortedBuckets) {
            videos.addAll(bucket.videos);
        }
    }

    private int getSortPriority(ArteVideo v) {
        if("DE".equals(v.type)) {
            return 4;
        } else if("UT".equals(v.type)) {
            return 3;
        } else if("OV".equals(v.type)) {
            return 2;
        } else if("AD".equals(v.type)) {
            return 1;
        } else if("FR".equals(v.type)) {
            return 0;
        }
        return -1;
    }
}
