package is.dyino.model;

public class RadioStation {
    private final String name;
    private final String url;
    private final String group;

    public RadioStation(String name, String url, String group) {
        this.name  = name;
        this.url   = url;
        this.group = group;
    }

    public String getName()  { return name;  }
    public String getUrl()   { return url;   }
    public String getGroup() { return group; }
}
