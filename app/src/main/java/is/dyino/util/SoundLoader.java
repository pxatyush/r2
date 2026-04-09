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
 * Loads sounds from assets/sounds/.
 *
 * NEW SUBFOLDER LOGIC (takes priority):
 *   assets/sounds/<CategoryName>/<SoundName>.mp3
 *   → category = "CategoryName", sound name = "SoundName" (prettified)
 *   → fileName passed to AudioService = "<CategoryName>/<SoundName>.mp3"
 *
 * LEGACY FALLBACK (flat files in assets/sounds/):
 *   assets/sounds/category_soundname.mp3
 *   → category derived from prefix before first '_'
 *
 * Files named "click.mp3" at the root level are always skipped.
 */
public class SoundLoader {

    private static final String SOUNDS_DIR = "sounds";

    public static List<SoundCategory> load(Context context) {
        Map<String, List<SoundItem>> catMap = new LinkedHashMap<>();

        try {
            String[] topLevel = context.getAssets().list(SOUNDS_DIR);
            if (topLevel == null) return getDefaults();

            boolean hasSubfolders = false;

            // ── Try subfolder structure first ──
            for (String entry : topLevel) {
                // Check if this entry is a directory (no extension)
                if (!entry.contains(".")) {
                    String[] files;
                    try { files = context.getAssets().list(SOUNDS_DIR + "/" + entry); }
                    catch (IOException e) { continue; }
                    if (files == null || files.length == 0) continue;

                    hasSubfolders = true;
                    String categoryName = prettify(entry);
                    List<SoundItem> items = catMap.computeIfAbsent(categoryName,
                            k -> new ArrayList<>());

                    // Sort files alphabetically inside each category
                    Arrays.sort(files);
                    for (String file : files) {
                        if (!isAudio(file)) continue;
                        String base      = stripExt(file);
                        String soundName = prettify(base);
                        // fileName = "subfolder/file.mp3" so AudioService opens correctly
                        String fileName  = entry + "/" + file;
                        items.add(new SoundItem(soundName, fileName, emoji(base), categoryName));
                    }
                }
            }

            // ── Legacy flat-file fallback ──
            if (!hasSubfolders) {
                Arrays.sort(topLevel);
                for (String file : topLevel) {
                    if (!isAudio(file)) continue;
                    if (file.equals("click.mp3")) continue;

                    String base     = stripExt(file);
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

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (catMap.isEmpty()) return getDefaults();

        List<SoundCategory> result = new ArrayList<>();
        for (Map.Entry<String, List<SoundItem>> e : catMap.entrySet())
            if (!e.getValue().isEmpty())
                result.add(new SoundCategory(e.getKey(), e.getValue()));
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static boolean isAudio(String f) {
        String l = f.toLowerCase();
        return l.endsWith(".mp3") || l.endsWith(".ogg") || l.endsWith(".wav") || l.endsWith(".flac");
    }

    private static String stripExt(String f) {
        int dot = f.lastIndexOf('.');
        return dot > 0 ? f.substring(0, dot) : f;
    }

    public static String prettify(String s) {
        if (s == null || s.isEmpty()) return s;
        s = s.replace("_", " ").replace("-", " ");
        String[] words = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty())
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private static String emoji(String n) {
        n = n.toLowerCase();
        if (n.contains("rain"))                              return "🌧";
        if (n.contains("fire") || n.contains("campfire"))   return "🔥";
        if (n.contains("forest") || n.contains("jungle"))   return "🌲";
        if (n.contains("ocean") || n.contains("sea"))       return "🌊";
        if (n.contains("wave"))                              return "🌊";
        if (n.contains("water") || n.contains("river") || n.contains("stream")) return "💧";
        if (n.contains("wind"))                              return "🌬";
        if (n.contains("bird"))                              return "🐦";
        if (n.contains("thunder") || n.contains("storm"))   return "⛈";
        if (n.contains("night") || n.contains("cricket"))   return "🌙";
        if (n.contains("bowl") || n.contains("bell") || n.contains("zen")) return "🔔";
        if (n.contains("flute"))                             return "🎵";
        if (n.contains("alpha") || n.contains("beta") || n.contains("theta")
                || n.contains("delta") || n.contains("gamma") || n.contains("hz")) return "🧠";
        if (n.contains("cafe") || n.contains("coffee"))     return "☕";
        if (n.contains("space"))                             return "🌌";
        if (n.contains("white") || n.contains("pink") || n.contains("brown")
                || n.contains("noise"))                      return "〰";
        if (n.contains("beach"))                             return "🏖";
        if (n.contains("snow"))                              return "❄";
        if (n.contains("cat"))                               return "🐱";
        if (n.contains("dog"))                               return "🐶";
        if (n.contains("focus"))                             return "🎯";
        if (n.contains("meditation") || n.contains("yoga")) return "🧘";
        if (n.contains("piano"))                             return "🎹";
        if (n.contains("guitar"))                            return "🎸";
        if (n.contains("lofi") || n.contains("lo-fi"))      return "🎧";
        if (n.contains("city") || n.contains("urban"))      return "🏙";
        if (n.contains("library") || n.contains("study"))   return "📚";
        if (n.contains("fan") || n.contains("ac") || n.contains("air")) return "💨";
        return "🎶";
    }

    // ── Built-in defaults (no assets present) ─────────────────────
    public static List<SoundCategory> getDefaults() {
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
        binaural.add(new SoundItem("Alpha",  "binaural/alpha.mp3",  "🧠", "Binaural"));
        binaural.add(new SoundItem("Beta",   "binaural/beta.mp3",   "🧠", "Binaural"));
        binaural.add(new SoundItem("Theta",  "binaural/theta.mp3",  "🧠", "Binaural"));
        binaural.add(new SoundItem("Delta",  "binaural/delta.mp3",  "🧠", "Binaural"));
        binaural.add(new SoundItem("Gamma",  "binaural/gamma.mp3",  "🧠", "Binaural"));
        binaural.add(new SoundItem("432 Hz", "binaural/432hz.mp3",  "🎵", "Binaural"));
        cats.add(new SoundCategory("Binaural", binaural));

        List<SoundItem> ambient = new ArrayList<>();
        ambient.add(new SoundItem("Fire",        "ambient/fire.mp3",       "🔥", "Ambient"));
        ambient.add(new SoundItem("White Noise",  "ambient/whitenoise.mp3", "〰", "Ambient"));
        ambient.add(new SoundItem("Cafe",         "ambient/cafe.mp3",       "☕", "Ambient"));
        ambient.add(new SoundItem("Zen Bowl",     "ambient/zenbowl.mp3",    "🔔", "Ambient"));
        ambient.add(new SoundItem("Space",        "ambient/space.mp3",      "🌌", "Ambient"));
        ambient.add(new SoundItem("Night",        "ambient/night.mp3",      "🌙", "Ambient"));
        cats.add(new SoundCategory("Ambient", ambient));

        return cats;
    }
}
