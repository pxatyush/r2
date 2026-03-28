package is.dyino.util;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import is.dyino.model.SoundCategory;
import is.dyino.model.SoundItem;

/**
 * Loads sound items from assets/sounds/ folder.
 * Falls back to built-in list if folder is empty.
 * File naming convention: category_name.mp3  e.g. natural_rain.mp3
 * Or just rain.mp3 - placed in the "Natural" category by default.
 */
public class SoundLoader {

    public static List<SoundCategory> load(Context context) {
        // Try to discover from assets
        Map<String, List<SoundItem>> catMap = new LinkedHashMap<>();

        try {
            String[] files = context.getAssets().list("sounds");
            if (files != null && files.length > 0) {
                for (String file : files) {
                    if (!file.endsWith(".mp3") && !file.endsWith(".ogg")) continue;
                    String baseName = file.replaceAll("\\.(mp3|ogg)$", "");
                    String category = "Natural";
                    String name = capitalize(baseName);
                    String emoji = emojiFor(baseName);

                    if (baseName.contains("_")) {
                        String[] parts = baseName.split("_", 2);
                        category = capitalize(parts[0]);
                        name = capitalize(parts[1]);
                    }

                    if (!catMap.containsKey(category)) catMap.put(category, new ArrayList<>());
                    catMap.get(category).add(new SoundItem(name, file, emoji, category));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (catMap.isEmpty()) {
            return getDefaults();
        }

        List<SoundCategory> result = new ArrayList<>();
        for (Map.Entry<String, List<SoundItem>> e : catMap.entrySet()) {
            result.add(new SoundCategory(e.getKey(), e.getValue()));
        }
        return result;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).replace("_", " ");
    }

    private static String emojiFor(String name) {
        name = name.toLowerCase();
        if (name.contains("rain")) return "🌧";
        if (name.contains("fire")) return "🔥";
        if (name.contains("forest") || name.contains("jungle")) return "🌲";
        if (name.contains("ocean") || name.contains("sea") || name.contains("wave")) return "🌊";
        if (name.contains("water") || name.contains("river") || name.contains("stream")) return "💧";
        if (name.contains("wind")) return "🌬";
        if (name.contains("bird") || name.contains("birds")) return "🐦";
        if (name.contains("thunder") || name.contains("storm")) return "⛈";
        if (name.contains("night") || name.contains("cricket")) return "🌙";
        if (name.contains("zen") || name.contains("bowl") || name.contains("bell")) return "🔔";
        if (name.contains("flute")) return "🎵";
        if (name.contains("alpha") || name.contains("beta") || name.contains("theta") || name.contains("delta")) return "🧠";
        if (name.contains("cafe") || name.contains("coffee")) return "☕";
        if (name.contains("space")) return "🌌";
        if (name.contains("white") || name.contains("noise") || name.contains("pink") || name.contains("brown")) return "〰";
        if (name.contains("beach")) return "🏖";
        if (name.contains("snow")) return "❄";
        if (name.contains("cat")) return "🐱";
        return "🎶";
    }

    private static List<SoundCategory> getDefaults() {
        List<SoundCategory> categories = new ArrayList<>();

        // Natural
        List<SoundItem> natural = new ArrayList<>();
        natural.add(new SoundItem("Rain", "rain.mp3", "🌧", "Natural"));
        natural.add(new SoundItem("Forest", "forest.mp3", "🌲", "Natural"));
        natural.add(new SoundItem("Ocean", "ocean.mp3", "🌊", "Natural"));
        natural.add(new SoundItem("River", "river.mp3", "💧", "Natural"));
        natural.add(new SoundItem("Wind", "wind.mp3", "🌬", "Natural"));
        natural.add(new SoundItem("Birds", "birds.mp3", "🐦", "Natural"));
        natural.add(new SoundItem("Thunder", "thunder.mp3", "⛈", "Natural"));
        natural.add(new SoundItem("Beach", "beach.mp3", "🏖", "Natural"));
        categories.add(new SoundCategory("Natural", natural));

        // Binaural
        List<SoundItem> binaural = new ArrayList<>();
        binaural.add(new SoundItem("Alpha", "alpha.mp3", "🧠", "Binaural"));
        binaural.add(new SoundItem("Beta", "beta.mp3", "🧠", "Binaural"));
        binaural.add(new SoundItem("Theta", "theta.mp3", "🧠", "Binaural"));
        binaural.add(new SoundItem("Delta", "delta.mp3", "🧠", "Binaural"));
        binaural.add(new SoundItem("Gamma", "gamma.mp3", "🧠", "Binaural"));
        binaural.add(new SoundItem("432 Hz", "432hz.mp3", "🎵", "Binaural"));
        categories.add(new SoundCategory("Binaural", binaural));

        // Ambient
        List<SoundItem> ambient = new ArrayList<>();
        ambient.add(new SoundItem("Fire", "fire.mp3", "🔥", "Ambient"));
        ambient.add(new SoundItem("White Noise", "whitenoise.mp3", "〰", "Ambient"));
        ambient.add(new SoundItem("Cafe", "cafe.mp3", "☕", "Ambient"));
        ambient.add(new SoundItem("Zen Bowl", "zenbowl.mp3", "🔔", "Ambient"));
        ambient.add(new SoundItem("Space", "space.mp3", "🌌", "Ambient"));
        ambient.add(new SoundItem("Night", "night.mp3", "🌙", "Ambient"));
        categories.add(new SoundCategory("Ambient", ambient));

        return categories;
    }
}
