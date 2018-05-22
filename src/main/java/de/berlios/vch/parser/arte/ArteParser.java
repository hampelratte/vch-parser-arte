package de.berlios.vch.parser.arte;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;
import de.berlios.vch.parser.arte.sorting.VideoSorter;

@Component
@Provides
public class ArteParser implements IWebParser {

    public static final String CHARSET = "UTF-8";

    public static final String BASE_URI = "https://www.arte.tv";

    public static final String ID = ArteParser.class.getName();

    @Requires
    private LogService logger;

    public ArteParser(BundleContext ctx) {
    }

    @Override
    public IOverviewPage getRoot() throws Exception {
        OverviewPage page = new OverviewPage();
        page.setParser(ID);
        page.setTitle(getTitle());
        page.setUri(new URI("vchpage://localhost/" + getId()));

        String pageTmpl = BASE_URI + "/guide/api/api/zones/de/web/listing_MAGAZINES/?page={page}&limit=50";
        String url = BASE_URI + "/guide/api/api/zones/de/web/listing_MAGAZINES/?page=1&limit=50";
        int pageNo = 1;
        while(url != null) {
            String result = HttpUtils.get(url, null, CHARSET);
            JSONObject json = new JSONObject(result);
            JSONArray data = json.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject program = data.getJSONObject(i);
                OverviewPage opage = new OverviewPage();
                opage.setParser(ArteParser.ID);
                opage.setTitle(program.getString("title"));
                opage.setUri(new URI("arte://program/" + program.getString("programId")));
                opage.getUserData().put("referer", program.getString("url"));
                page.getPages().add(opage);
            }
            if(json.has("nextPage") && !json.isNull("nextPage")) {
                url = pageTmpl.replaceAll("\\{page\\}", Integer.toString(++pageNo));
            } else {
                url = null;
            }
        }
        return page;
    }

    private Calendar getPubDate(JSONObject video) {
        if (video.has("VRA")) {
            try {
                String timestamp = video.getString("VRA");
                Calendar pubDate = Calendar.getInstance();
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss Z");
                pubDate.setTime(sdf.parse(timestamp));
                return pubDate;
            } catch (Exception e) {
                logger.log(LogService.LOG_WARNING, "Couldn't get video publish date", e);
            }
        }
        return null;
    }

    private URI getThumbnail(JSONObject video) throws JSONException {
        if (video.has("VTU")) {
            JSONObject vtu = video.getJSONObject("VTU");
            if(vtu.has("IUR")) {
                try {
                    return new URI(vtu.getString("IUR"));
                } catch (Exception e) {
                    logger.log(LogService.LOG_WARNING, "Couldn't get video thumbnail", e);
                }
            }
        }
        return null;
    }

    @Override
    public String getTitle() {
        return "Arte+7";
    }

    @Override
    public IWebPage parse(IWebPage page) throws Exception {
        if (page instanceof IVideoPage) {
            IVideoPage video = (IVideoPage) page;
            return parseVideo(video);
        } else if (page instanceof IOverviewPage) {
            IOverviewPage opage = (IOverviewPage) page;
            if (opage.getUri().toString().startsWith("arte://program/")) {
                parseProgram(opage);
            }
            return page;
        } else {
            return page;
        }
    }

    private IWebPage parseVideo(IVideoPage video) throws IOException, JSONException, URISyntaxException {
        if(video.getPublishDate() == null) {
            String id = video.getUri().getPath().substring(1);
            String uri = "https://api.arte.tv/api/player/v1/config/de/" + id;
            Map<String, String> header = HttpUtils.createFirefoxHeader();
            String result = HttpUtils.get(uri, header, CHARSET);
            logger.log(LogService.LOG_DEBUG, result);
            JSONObject json = new JSONObject(result).getJSONObject("videoJsonPlayer");
            video.setThumbnail(getThumbnail(json));
            video.setPublishDate(getPubDate(json));
            if(json.has("VTR")) {
                video.setUri(new URI(json.getString("VTR")));
            }
            video.setVideoUri(getBestVideo(json));
        }
        return video;
    }

    private URI getBestVideo(JSONObject json) throws JSONException, URISyntaxException {
        JSONObject vsr = json.getJSONObject("VSR");
        List<ArteVideo> videos = new ArrayList<ArteVideo>();
        for (String name : JSONObject.getNames(vsr)) {
            JSONObject videoSource = vsr.getJSONObject(name);
            ArteVideo video = new ArteVideo();
            video.width = videoSource.getInt("width");
            video.format = videoSource.getString("mediaType");
            video.type = videoSource.getString("versionShortLibelle");
            video.uri = videoSource.getString("url");
            video.bitrate = videoSource.getInt("bitrate");
            videos.add(video);
            logger.log(LogService.LOG_DEBUG, "Found media: " + video);
        }

        new VideoSorter().sort(videos);
        ArteVideo best = videos.get(0);
        logger.log(LogService.LOG_DEBUG, "Best video: " + best);

        return new URI(best.uri);
    }

    private void parseProgram(IOverviewPage opage) throws Exception {
        String id = opage.getUri().getPath().substring(1);
        String referer = (String) opage.getUserData().get("referer");
        Map<String, String> header = HttpUtils.createFirefoxHeader();
        header.put("Referer", referer);

        int page = 1;
        String pageTemplate = BASE_URI + "/guide/api/api/zones/de/web/listing_"+id+"/?page={page}";
        String nextPage = BASE_URI + "/guide/api/api/zones/de/web/listing_"+id+"/?page="+page;
        while(nextPage != null) {
            try {
                String result = HttpUtils.get(nextPage, header, CHARSET);
                JSONObject json = new JSONObject(result);
                JSONArray data = json.getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject program = data.getJSONObject(i);
                    VideoPage video = new VideoPage();
                    video.setParser(ArteParser.ID);
                    video.setTitle(getTitle(opage.getTitle(), program));
                    video.setDescription(program.getString("description"));
                    video.setUri(new URI("arte://video/" + program.getString("programId")));
                    video.setDuration(program.getInt("duration"));
                    opage.getPages().add(video);
                }
                if(json.has("nextPage") && !json.isNull("nextPage")) {
                    nextPage = pageTemplate.replaceAll("\\{page\\}", Integer.toString(++page));
                } else {
                    nextPage = null;
                }
            } catch(Exception e) {
                logger.log(LogService.LOG_ERROR, "Couldn't load page", e);
                break;
            }
        }
    }

    private String getTitle(String parentTitle, JSONObject program) throws JSONException {
        String title = program.getString("title");
        String subtitle = program.getString("subtitle");
        if(title.equalsIgnoreCase(parentTitle) && program.has("subtitle") && !program.isNull("subtitle")) {
            return subtitle;
        } else {
            if(program.has("subtitle") && !program.isNull("subtitle")) {
                title = title + " - " + subtitle;
            }
        }
        return title;
    }

    @Validate
    public void start() {
    }

    @Invalidate
    public void stop() {
    }

    @Override
    public String getId() {
        return ID;
    }
}