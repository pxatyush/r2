package is.dyino.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;

public class RadioLoader {

    public static List<RadioGroup> load(Context context) {
        Map<String, List<RadioStation>> map = new LinkedHashMap<>();
        parse(readAsset(context, "radio/config.txt"), map);
        // Merge any extra fetched config
        File extra = new File(context.getFilesDir(), "radio_extra.cfg");
        if (extra.exists()) parse(readFile(extra), map);

        List<RadioGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<RadioStation>> e : map.entrySet())
            if (!e.getValue().isEmpty()) result.add(new RadioGroup(e.getKey(), e.getValue()));

        return result.isEmpty() ? getDefaults() : result;
    }

    private static void parse(String text, Map<String, List<RadioStation>> map) {
        if (text == null || text.isEmpty()) return;
        String currentGroup = "General";
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                currentGroup = line.substring(1, line.length()-1).trim();
                if (!map.containsKey(currentGroup)) map.put(currentGroup, new ArrayList<>());
            } else if (line.contains("|")) {
                String[] p = line.split("\\|", 2);
                if (!map.containsKey(currentGroup)) map.put(currentGroup, new ArrayList<>());
                map.get(currentGroup).add(new RadioStation(p[0].trim(), p[1].trim(), currentGroup));
            }
        }
    }

    private static String readAsset(Context ctx, String path) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(ctx.getAssets().open(path)));
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close(); return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private static String readFile(File f) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close(); return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private static List<RadioGroup> getDefaults() {
        List<RadioGroup> groups = new ArrayList<>();
        List<RadioStation> lilo = new ArrayList<>();
        lilo.add(new RadioStation("Breathe.fm","https://lilo.systhetics.com/lilo/station-main/station.xml","Lilo"));
        lilo.add(new RadioStation("Theta Chill Radio","https://streams.calmradio.com/api/7/128/stream","Lilo"));
        lilo.add(new RadioStation("Zion Chillout","https://listen.openstream.co/2148/audio","Lilo"));
        lilo.add(new RadioStation("Delta Radio","https://listen.openstream.co/4380/audio","Lilo"));
        groups.add(new RadioGroup("Lilo", lilo));
        List<RadioStation> india = new ArrayList<>();
        india.add(new RadioStation("Radio Mirchi 98.3","https://prclive1.listenon.in/","India FM"));
        india.add(new RadioStation("Big FM 92.7","https://bigfm.out.airtime.pro/bigfm_a","India FM"));
        india.add(new RadioStation("Red FM 93.5","https://redfm.out.airtime.pro/redfm_a","India FM"));
        groups.add(new RadioGroup("India FM", india));
        return groups;
    }
}
