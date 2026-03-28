package is.dyino.model;

import java.util.List;
import java.util.ArrayList;

public class RadioGroup {
    public String name;
    public List<RadioStation> stations;

    public RadioGroup(String name) {
        this.name = name;
        this.stations = new ArrayList<>();
    }
}
