package is.dyino.util;

import android.content.Context;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import is.dyino.model.SoundCategory;
import is.dyino.model.SoundItem;

public class SoundLoader {

    public static List<SoundCategory> load(Context context) {
        Map<String, List<SoundItem>> catMap = new LinkedHashMap<>();
        try {
            String[] files = context.getAssets().list("sounds");
            if (files != null) {
                for (String file : files) {
                    if (!file.endsWith(".mp3") && !file.endsWith(".ogg")) continue;
                    if (file.equals("click.mp3")) continue;
                    String base     = file.replaceAll("\\.(mp3|ogg)$", "");
                    String category = "Natural";
                    String name     = prettify(base);
                    if (base.contains("_")) {
                        String[] p = base.split("_", 2);
                        category = prettify(p[0]);
                        name     = prettify(p[1]);
                    }
                    catMap.computeIfAbsent(category, k -> new ArrayList<>())
                          .add(new SoundItem(name, file, emoji(base), category));
                }
            }
        } catch (IOException e) { e.printStackTrace(); }

        if (catMap.isEmpty()) return getDefaults();

        List<SoundCategory> result = new ArrayList<>();
        for (Map.Entry<String, List<SoundItem>> e : catMap.entrySet())
            result.add(new SoundCategory(e.getKey(), e.getValue()));
        return result;
    }

    private static String prettify(String s) {
        if (s == null || s.isEmpty()) return s;
        s = s.replace("_", " ").replace("-", " ");
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0)))
                                .append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private static String emoji(String n) {
        n = n.toLowerCase();
        if (n.contains("rain"))   return "🌧";
        if (n.contains("fire"))   return "🔥";
        if (n.contains("forest") || n.contains("jungle")) return "🌲";
        if (n.contains("ocean") || n.contains("sea"))     return "🌊";
        if (n.contains("wave"))   return "🌊";
        if (n.contains("water") || n.contains("river") || n.contains("stream")) return "💧";
        if (n.contains("wind"))   return "🌬";
        if (n.contains("bird"))   return "🐦";
        if (n.contains("thunder") || n.contains("storm")) return "⛈";
        if (n.contains("night") || n.contains("cricket")) return "🌙";
        if (n.contains("bowl") || n.contains("bell") || n.contains("zen")) return "🔔";
        if (n.contains("flute"))  return "🎵";
        if (n.contains("alpha") || n.contains("beta") || n.contains("theta")
                || n.contains("delta") || n.contains("gamma") || n.contains("hz")) return "🧠";
        if (n.contains("cafe") || n.contains("coffee")) return "☕";
        if (n.contains("space"))  return "🌌";
        if (n.contains("white") || n.contains("pink") || n.contains("brown") || n.contains("noise")) return "〰";
        if (n.contains("beach"))  return "🏖";
        if (n.contains("snow"))   return "❄";
        if (n.contains("cat"))    return "🐱";
        if (n.contains("dog"))    return "🐶";
        if (n.contains("focus"))  return "🎵";
        return "🎶";
    }

    private static List<SoundCategory> getDefaults() {
        List<SoundCategory> cats = new ArrayList<>();

        List<SoundItem> natural = new ArrayList<>();
        natural.add(new SoundItem("Rain",    "rain.mp3",    "🌧", "Natural"));
        natural.add(new SoundItem("Forest",  "forest.mp3",  "🌲", "Natural"));
        natural.add(new SoundItem("Ocean",   "ocean.mp3",   "🌊", "Natural"));
        natural.add(new SoundItem("River",   "river.mp3",   "💧", "Natural"));
        natural.add(new SoundItem("Wind",    "wind.mp3",    "🌬", "Natural"));
        natural.add(new SoundItem("Birds",   "birds.mp3",   "🐦", "Natural"));
        natural.add(new SoundItem("Thunder", "thunder.mp3", "⛈", "Natural"));
        natural.add(new SoundItem("Beach",   "beach.mp3",   "🏖", "Natural"));
        cats.add(new SoundCategory("Natural", natural));

        List<SoundItem> binaural = new ArrayList<>();
        binaural.add(new SoundItem("Alpha",  "binaural_alpha.mp3",  "🧠", "Binaural"));
        binaural.add(new SoundItem("Beta",   "binaural_beta.mp3",   "🧠", "Binaural"));
        binaural.add(new SoundItem("Theta",  "binaural_theta.mp3",  "🧠", "Binaural"));
        binaural.add(new SoundItem("Delta",  "binaural_delta.mp3",  "🧠", "Binaural"));
        binaural.add(new SoundItem("Gamma",  "binaural_gamma.mp3",  "🧠", "Binaural"));
        binaural.add(new SoundItem("432 Hz", "binaural_432hz.mp3",  "🎵", "Binaural"));
        cats.add(new SoundCategory("Binaural", binaural));

        List<SoundItem> ambient = new ArrayList<>();
        ambient.add(new SoundItem("Fire",       "ambient_fire.mp3",       "🔥", "Ambient"));
        ambient.add(new SoundItem("White Noise", "ambient_whitenoise.mp3", "〰", "Ambient"));
        ambient.add(new SoundItem("Cafe",        "ambient_cafe.mp3",       "☕", "Ambient"));
        ambient.add(new SoundItem("Zen Bowl",    "ambient_zenbowl.mp3",    "🔔", "Ambient"));
        ambient.add(new SoundItem("Space",       "ambient_space.mp3",      "🌌", "Ambient"));
        ambient.add(new SoundItem("Night",       "ambient_night.mp3",      "🌙", "Ambient"));
        cats.add(new SoundCategory("Ambient", ambient));

        return cats;
    }
}
