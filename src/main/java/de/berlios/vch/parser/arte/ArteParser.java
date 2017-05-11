package de.berlios.vch.parser.arte;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

import de.berlios.vch.http.client.Base64;
import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.HtmlParserUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;
import de.berlios.vch.parser.WebPageTitleComparator;

@Component
@Provides
public class ArteParser implements IWebParser {

    public static final String CHARSET = "UTF-8";

    public static final String ARTE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    public static final String BASE_URI = "http://videos.arte.tv";

    public static final String ID = ArteParser.class.getName();

    @Requires
    private LogService logger;

    private VideoPageParser videoPageParser;

    public static Map<String, String> HTTP_HEADERS = new HashMap<String, String>();
    static {
        HTTP_HEADERS.put("User-Agent", "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.1.2) Gecko/20090821 Gentoo Firefox/3.5.2");
        HTTP_HEADERS.put("Accept-Language", "de-de,de;q=0.8,en-us;q=0.5,en;q=0.3");
    }

    public ArteParser(BundleContext ctx) {
    }

    @Override
    public IOverviewPage getRoot() throws Exception {
        OverviewPage page = new OverviewPage();
        page.setParser(ID);
        page.setTitle(getTitle());
        page.setUri(new URI("vchpage://localhost/" + getId()));

        String result = HttpUtils.get("http://www.arte.tv/papi/tvguide/videos/ARTE_PLUS_SEVEN/D.json", null, CHARSET);
        JSONObject json = new JSONObject(result);
        JSONObject pagination = json.getJSONObject("paginatedCollectionWrapper");
        JSONArray videos = pagination.getJSONArray("collection");

        Map<String, IOverviewPage> programs = new HashMap<String, IOverviewPage>();
        for (int i = 0; i < videos.length(); i++) {
            JSONObject video = videos.getJSONObject(i);
            IVideoPage videoPage = parseVideo(video);
            if (video.has("clusterTitle")) {
                String clusterTitle = video.getString("clusterTitle").trim();
                IOverviewPage opage = programs.get(clusterTitle);
                if (opage == null) {
                    opage = new OverviewPage();
                    programs.put(clusterTitle, opage);
                    opage.setParser(ArteParser.ID);
                    opage.setTitle(clusterTitle);
                    opage.setUri(new URI("arte://cluster/" + Base64.encodeBytes(clusterTitle.getBytes("UTF-8"))));
                    page.getPages().add(opage);
                }
                opage.getPages().add(videoPage);
            } else {
                page.getPages().add(videoPage);
            }

        }

        Collections.sort(page.getPages(), new WebPageTitleComparator());
        return page;
    }

    protected IVideoPage parseVideo(JSONObject video) throws JSONException, URISyntaxException {
        IVideoPage videoPage = new VideoPage();
        videoPage.setParser(ID);
        videoPage.setTitle(getTitle(video));
        videoPage.setVideoUri(new URI(video.getString("videoPlayerUrl")));
        videoPage.setUri(new URI(video.getString("VUP")));
        videoPage.setDuration(getDuration(video));
        videoPage.setThumbnail(getThumbnail(video));
        videoPage.setDescription(getDescription(video));
        videoPage.setPublishDate(getPubDate(video));
        return videoPage;
    }

    private Calendar getPubDate(JSONObject video) {
        if (video.has("videoBroadcastTimestamp")) {
            try {
                long timestamp = video.getLong("videoBroadcastTimestamp");
                Calendar pubDate = Calendar.getInstance();
                pubDate.setTimeInMillis(timestamp);
                return pubDate;
            } catch (Exception e) {
                logger.log(LogService.LOG_WARNING, "Couldn't get video publish date", e);
            }
        }
        return null;
    }

    private String getDescription(JSONObject video) {
        if (video.has("VDE")) {
            try {
                String desc = video.getString("VDE");
                if (video.has("VRU")) {
                    desc += " Verfügbar bis " + video.getString("VRU");
                }
                return desc;
            } catch (Exception e) {
                logger.log(LogService.LOG_WARNING, "Couldn't get video description", e);
            }
        }
        return null;
    }

    private URI getThumbnail(JSONObject video) {
        if (video.has("programImage")) {
            try {
                return new URI(video.getString("programImage"));
            } catch (Exception e) {
                logger.log(LogService.LOG_WARNING, "Couldn't get video thumbnail", e);
            }
        }
        return null;
    }

    private long getDuration(JSONObject video) {
        if (video.has("VDU")) {
            try {
                return video.getInt("VDU") * 50;
            } catch (JSONException e) {
                logger.log(LogService.LOG_WARNING, "Couldn't get video duration", e);
            }
        }
        return 0;
    }

    private String getTitle(JSONObject video) throws JSONException {
        String title = video.getString("VTI");

        String subtitle = null;
        if (video.has("VSU")) {
            subtitle = video.getString("VSU");
        }

        String clusterTitle = null;
        if (video.has("clusterTitle")) {
            clusterTitle = video.getString("clusterTitle");
        }

        if (clusterTitle != null && subtitle != null) {
            title = subtitle;
        } else {
            if (subtitle != null) {
                title += " - " + subtitle;
            }
        }

        return title;
    }

    @Override
    public String getTitle() {
        return "Arte+7";
    }

    @Override
    public IWebPage parse(IWebPage page) throws Exception {
        if (page instanceof IVideoPage) {
            IVideoPage video = (IVideoPage) page;
            return videoPageParser.parse(video);
        } else if (page instanceof IOverviewPage) {
            IOverviewPage opage = (IOverviewPage) page;
            if (!opage.getUri().toString().startsWith("arte://cluster")) {
                parseBroadcasts(opage);
            }
            return page;
        } else {
            return page;
        }
    }

    private void parseBroadcasts(IOverviewPage opage) throws Exception {
        String uri = opage.getUri().toString() + "#/de/list///1/150/";
        String content = HttpUtils.get(uri, HTTP_HEADERS, CHARSET);
        content = content.replaceAll("<noscript>", "");
        content = content.replaceAll("</noscript>", "");
        Elements videoDivs = HtmlParserUtils.getTags(content, "div.video");
        for (Iterator<Element> iterator = videoDivs.iterator(); iterator.hasNext();) {
            String nodeContent = iterator.next().html();

            IVideoPage video = new VideoPage();
            video.setParser(getId());

            // parse title and page uri
            Element link = HtmlParserUtils.getTag(nodeContent, "h2 a");
            video.setTitle(link.text());
            video.setUri(new URI(BASE_URI + link.attr("href")));

            // parse description
            video.setDescription(HtmlParserUtils.getText(nodeContent, "p.teaserText"));

            // parse thumbnail
            Element thumb = HtmlParserUtils.getTag(nodeContent, "img.thumbnail");
            video.setThumbnail(new URI(BASE_URI + thumb.attr("src")));

            // parse date (Di, 13. Apr 2010, 00:00)
            try {
                String format = "EE, dd. MMM yyyy, HH:mm";

                Element ps = (Element) HtmlParserUtils.getTag(nodeContent, "p.views").previousSibling().previousSibling();
                String dateString = ps.text();
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                Calendar pubDate = Calendar.getInstance();
                pubDate.setTime(sdf.parse(dateString));
                video.setPublishDate(pubDate);
            } catch (Exception e) {
                logger.log(LogService.LOG_WARNING, "Couldn't parse publish date", e);
            }

            opage.getPages().add(video);
        }
    }

    @Validate
    public void start() {
        videoPageParser = new VideoPageParser(logger, this);
    }

    @Invalidate
    public void stop() {
    }

    @Override
    public String getId() {
        return ID;
    }
}