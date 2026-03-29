package is.dyino.model;

public class SoundItem {
    private final String name;
    private final String fileName;  // asset filename, e.g. "rain.mp3"
    private final String emoji;
    private final String category;
    private float   volume  = 0.8f;
    private boolean playing = false;

    public SoundItem(String name, String fileName, String emoji, String category) {
        this.name     = name;
        this.fileName = fileName;
        this.emoji    = emoji;
        this.category = category;
    }

    public String  getName()    { return name;     }
    public String  getFileName(){ return fileName; }
    public String  getEmoji()   { return emoji;    }
    public String  getCategory(){ return category; }
    public float   getVolume()  { return volume;   }
    public boolean isPlaying()  { return playing;  }

    public void setVolume(float v)    { this.volume  = Math.max(0f, Math.min(1f, v)); }
    public void setPlaying(boolean p) { this.playing = p; }
}
