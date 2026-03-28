package is.dyino.model;

public class SoundItem {
    public String name;
    public String emoji;
    public String assetPath; // e.g. "sounds/rain.mp3"
    public String category;
    public boolean isPlaying;
    public float volume; // 0.0 - 1.0

    public SoundItem(String name, String emoji, String assetPath, String category) {
        this.name = name;
        this.emoji = emoji;
        this.assetPath = assetPath;
        this.category = category;
        this.isPlaying = false;
        this.volume = 1.0f;
    }
}
