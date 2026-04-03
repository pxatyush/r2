package is.dyino.model;

public class RadioStation {
    private final String name;
    private final String url;
    private final String group;
    private final String faviconUrl;

    public RadioStation(String name, String url, String group) {
        this(name, url, group, "");
    }

    public RadioStation(String name, String url, String group, String faviconUrl) {
        this.name       = name;
        this.url        = url;
        this.group      = group;
        this.faviconUrl = faviconUrl != null ? faviconUrl : "";
    }

    public String getName()       { return name; }
    public String getUrl()        { return url; }
    public String getGroup()      { return group; }
    public String getFaviconUrl() { return faviconUrl; }
}