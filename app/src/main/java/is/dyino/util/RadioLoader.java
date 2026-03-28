package is.dyino.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;

public class RadioLoader {

    /**
     * Loads radio stations from assets/radio/config.txt
     * Format:
     *   [GroupName]
     *   Station Name | URL | tag
     *   ...
     */
    public static List<RadioGroup> loadFromAssets(Context context) {
        List<RadioGroup> groups = new ArrayList<>();
        Map<String, RadioGroup> groupMap = new LinkedHashMap<>();

        try {
            InputStream is = context.getAssets().open("radio/config.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            String currentGroup = "General";

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentGroup = line.substring(1, line.length() - 1).trim();
                    if (!groupMap.containsKey(currentGroup)) {
                        RadioGroup rg = new RadioGroup(currentGroup);
                        groupMap.put(currentGroup, rg);
                        groups.add(rg);
                    }
                } else if (line.contains("|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 2) {
                        String name = parts[0].trim();
                        String url = parts[1].trim();
                        String tag = parts.length >= 3 ? parts[2].trim() : "";

                        if (!groupMap.containsKey(currentGroup)) {
                            RadioGroup rg = new RadioGroup(currentGroup);
                            groupMap.put(currentGroup, rg);
                            groups.add(rg);
                        }
                        groupMap.get(currentGroup).stations.add(
                                new RadioStation(name, url, currentGroup, tag));
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            // If file not found, return built-in defaults
            groups = getDefaultStations();
        }

        if (groups.isEmpty()) {
            groups = getDefaultStations();
        }

        return groups;
    }

    private static List<RadioGroup> getDefaultStations() {
        List<RadioGroup> groups = new ArrayList<>();

        RadioGroup lilo = new RadioGroup("Lilo");
        lilo.stations.add(new RadioStation("Breathe.fm", "https://lilo.systhetics.com/lilo/station-main/station.xml", "Lilo", "chill"));
        lilo.stations.add(new RadioStation("Theta Chill Radio", "https://streams.ilovemusic.de/iloveradio17.mp3", "Lilo", "theta"));
        lilo.stations.add(new RadioStation("Zion Chillout", "http://stream.zeno.fm/yn65fsaurfhvv", "Lilo", "ambient"));
        lilo.stations.add(new RadioStation("Brainwave Positive", "http://stream.zeno.fm/f3wvbbqmdg8uv", "Lilo", "binaural"));
        lilo.stations.add(new RadioStation("Delta Radio", "http://stream.zeno.fm/0r0xa792kwzuv", "Lilo", "delta"));
        lilo.stations.add(new RadioStation("Divine Waves", "http://stream.zeno.fm/mvt4ydg9g28uv", "Lilo", "healing"));
        groups.add(lilo);

        RadioGroup india = new RadioGroup("India FM");
        india.stations.add(new RadioStation("Radio Mirchi 98.3", "http://peridot.streamguys1.com:7150/Mirchi", "India FM", "hindi"));
        india.stations.add(new RadioStation("Radio City 91.1", "https://prclive1.listenon.in/", "India FM", "hindi"));
        india.stations.add(new RadioStation("Big FM 92.7", "https://bbcwssc.ic.llnwd.net/stream/bbcwssc_mp1_ws-eieuk", "India FM", "hindi"));
        india.stations.add(new RadioStation("All India Radio", "https://air.pc.cdn.bitgravity.com/air/live/pbaudio001/playlist.m3u8", "India FM", "news"));
        india.stations.add(new RadioStation("Red FM 93.5", "http://icecast.hbr1.com:8000/trance.ogg", "India FM", "pop"));
        groups.add(india);

        RadioGroup ambient = new RadioGroup("Ambient & Focus");
        ambient.stations.add(new RadioStation("Lofi Hip Hop", "http://stream.zeno.fm/f3wvbbqmdg8uv", "Ambient & Focus", "lofi"));
        ambient.stations.add(new RadioStation("Deep Focus", "http://ice2.somafm.com/deepspaceone-128-mp3", "Ambient & Focus", "focus"));
        ambient.stations.add(new RadioStation("Drone Zone", "http://ice2.somafm.com/dronezone-128-mp3", "Ambient & Focus", "drone"));
        ambient.stations.add(new RadioStation("Space Station", "http://ice2.somafm.com/spacestation-128-mp3", "Ambient & Focus", "space"));
        ambient.stations.add(new RadioStation("Groove Salad", "http://ice2.somafm.com/groovesalad-128-mp3", "Ambient & Focus", "chill"));
        groups.add(ambient);

        RadioGroup binaural = new RadioGroup("Binaural Beats");
        binaural.stations.add(new RadioStation("Alpha Waves 10Hz", "https://stream.zeno.fm/yn65fsaurfhvv", "Binaural Beats", "alpha"));
        binaural.stations.add(new RadioStation("Theta 6Hz Study", "https://stream.zeno.fm/0r0xa792kwzuv", "Binaural Beats", "theta"));
        binaural.stations.add(new RadioStation("Delta Sleep 2Hz", "https://stream.zeno.fm/mvt4ydg9g28uv", "Binaural Beats", "delta"));
        binaural.stations.add(new RadioStation("Gamma Focus 40Hz", "https://stream.zeno.fm/f3wvbbqmdg8uv", "Binaural Beats", "gamma"));
        groups.add(binaural);

        return groups;
    }
}
