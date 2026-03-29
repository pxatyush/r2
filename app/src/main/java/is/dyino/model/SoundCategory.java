package is.dyino.model;

import java.util.List;

public class SoundCategory {
    private final String name;
    private final List<SoundItem> sounds;

    public SoundCategory(String name, List<SoundItem> sounds) {
        this.name   = name;
        this.sounds = sounds;
    }

    public String          getName()  { return name;   }
    public List<SoundItem> getSounds(){ return sounds; }
}
