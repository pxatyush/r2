package is.dyino.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;

/**
 * Parses assets/radio/config.txt
 *
 * Format:
 *   [Group Name]
 *   Station Name | https://stream.url
 */
public class RadioLoader {

    public static List<RadioGroup> load(Context context) {
        Map<String, List<RadioStation>> map = new LinkedHashMap<>();
        String currentGroup = "General";

        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(context.getAssets().open("radio/config.txt")));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentGroup = line.substring(1, line.length() - 1).trim();
                    if (!map.containsKey(currentGroup)) map.put(currentGroup, new ArrayList<>());
                } else if (line.contains("|")) {
                    String[] p = line.split("\\|", 2);
                    if (!map.containsKey(currentGroup)) map.put(currentGroup, new ArrayList<>());
                    map.get(currentGroup).add(
                            new RadioStation(p[0].trim(), p[1].trim(), currentGroup));
                }
            }
            br.close();
        } catch (Exception e) { /* fall through to defaults */ }

        List<RadioGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<RadioStation>> e : map.entrySet())
            if (!e.getValue().isEmpty()) result.add(new RadioGroup(e.getKey(), e.getValue()));

        return result.isEmpty() ? getDefaults() : result;
    }

    private static List<RadioGroup> getDefaults() {
        List<RadioGroup> groups = new ArrayList<>();

        List<RadioStation> lilo = new ArrayList<>();
        lilo.add(new RadioStation("Breathe.fm",         "https://lilo.systhetics.com/lilo/station-main/station.xml", "Lilo"));
        lilo.add(new RadioStation("Theta Chill Radio",  "https://streams.calmradio.com/api/7/128/stream",            "Lilo"));
        lilo.add(new RadioStation("Zion Chillout",      "https://listen.openstream.co/2148/audio",                   "Lilo"));
        lilo.add(new RadioStation("Delta Radio",        "https://listen.openstream.co/4380/audio",                   "Lilo"));
        groups.add(new RadioGroup("Lilo", lilo));

        List<RadioStation> india = new ArrayList<>();
        india.add(new RadioStation("Radio Mirchi 98.3", "https://prclive1.listenon.in/",               "India FM"));
        india.add(new RadioStation("Big FM 92.7",       "https://bigfm.out.airtime.pro/bigfm_a",       "India FM"));
        india.add(new RadioStation("Red FM 93.5",       "https://redfm.out.airtime.pro/redfm_a",       "India FM"));
        india.add(new RadioStation("AIR National",      "https://air.pc.cdn.bitgravity.com/air/live/pbaudio001/chunklist.m3u8", "India FM"));
        groups.add(new RadioGroup("India FM", india));

        List<RadioStation> ambient = new ArrayList<>();
        ambient.add(new RadioStation("Lofi Hip Hop",       "https://lofi.stream.laut.fm/lofi",              "Ambient"));
        ambient.add(new RadioStation("SomaFM Drone Zone",  "https://ice1.somafm.com/dronezone-128-mp3",     "Ambient"));
        ambient.add(new RadioStation("SomaFM Deep Space",  "https://ice1.somafm.com/deepspaceone-128-mp3",  "Ambient"));
        groups.add(new RadioGroup("Ambient", ambient));

        return groups;
    }
}
