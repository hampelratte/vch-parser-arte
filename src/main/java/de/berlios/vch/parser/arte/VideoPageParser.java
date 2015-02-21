package de.berlios.vch.parser.arte;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.log.LogService;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.IVideoPage;

public class VideoPageParser {

    private static final String SWF_URI = "http://www.arte.tv/arte_vp/jwplayer6/6.9.4867/jwplayer.flash.6.9.4867.swf";

    private LogService logger;

    private ArteParser arteParser;

    public VideoPageParser(LogService logger, ArteParser arteParser) {
        this.logger = logger;
        this.arteParser = arteParser;
    }

    public IVideoPage parse(IVideoPage videoPage) throws Exception {
        logger.log(LogService.LOG_DEBUG, "Getting media link in media page: " + videoPage.getUri());

        String uri = videoPage.getVideoUri().toString();
        if (uri.endsWith(".json")) {
            String content = HttpUtils.get(uri, null, ArteParser.CHARSET);
            JSONObject json = new JSONObject(content);
            JSONObject video = json.getJSONObject("videoJsonPlayer");
            videoPage = arteParser.parseVideo(video);

            JSONObject formats = video.getJSONObject("VSR");
            ArteVideo best = getBestVideo(formats);
            logger.log(LogService.LOG_INFO, "Best video is " + best.uri);
            if ("rtmp".equals(best.format)) {
                videoPage.setVideoUri(new URI(best.streamer + best.uri));
                videoPage.getUserData().put("streamName", best.uri);
                videoPage.getUserData().put("swfUri", new URI(SWF_URI));

                logger.log(LogService.LOG_DEBUG, "Video URI: " + videoPage.getVideoUri().toString());
                logger.log(LogService.LOG_DEBUG, "SWF URI: " + videoPage.getUserData().get("swfUri"));
            } else {
                videoPage.setVideoUri(new URI(best.uri));
            }
        }

        return videoPage;
    }

    private ArteVideo getBestVideo(JSONObject formats) throws JSONException {
        List<ArteVideo> videos = new ArrayList<ArteVideo>();

        for (Iterator<?> iterator = formats.keys(); iterator.hasNext();) {
            String key = (String) iterator.next();
            JSONObject vsr = formats.getJSONObject(key);

            // ignore videos with french language
            String lang = vsr.getString("versionShortLibelle");
            if ("FR".equalsIgnoreCase(lang)) {
                continue;
            }

            ArteVideo video = new ArteVideo();
            video.width = vsr.getInt("width");
            video.bitrate = vsr.getInt("bitrate");
            video.format = vsr.getString("mediaType");
            video.format = "".equals(video.format) ? "http" : video.format;
            video.uri = vsr.getString("url");
            if ("rtmp".equals(video.format)) {
                video.streamer = vsr.getString("streamer");
            }
            videos.add(video);
        }

        Collections.sort(videos, new ArteVideoComparator());
        logger.log(LogService.LOG_INFO, "Videos " + videos);
        ArteVideo best = videos.get(videos.size() - 1);
        return best;
    }
}
