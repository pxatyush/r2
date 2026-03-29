package is.dyino.model;

import java.util.List;

public class RadioGroup {
    private final String name;
    private final List<RadioStation> stations;

    public RadioGroup(String name, List<RadioStation> stations) {
        this.name     = name;
        this.stations = stations;
    }

    public String             getName()    { return name;     }
    public List<RadioStation> getStations(){ return stations; }
}
