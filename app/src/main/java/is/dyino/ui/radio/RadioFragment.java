package is.dyino.ui.radio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import is.dyino.MainActivity;
import is.dyino.R;
import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;
import is.dyino.util.RadioLoader;
import pl.droidsonroids.gif.GifImageView;

public class RadioFragment extends Fragment {

    private RadioAdapter adapter;
    private RadioStation currentStation;
    private TextView tvNowPlaying;
    private ImageButton btnPlayStop;
    private View gifContainer;
    private GifImageView gifView;
    private boolean isPlaying = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_radio, container, false);

        tvNowPlaying = root.findViewById(R.id.tv_now_playing);
        btnPlayStop = root.findViewById(R.id.btn_play_stop);
        gifContainer = root.findViewById(R.id.gif_container);
        gifView = root.findViewById(R.id.gif_view);

        RecyclerView rv = root.findViewById(R.id.rv_radio);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        List<RadioGroup> groups = RadioLoader.loadFromAssets(requireContext());
        List<Object> flatList = new ArrayList<>();
        for (RadioGroup g : groups) {
            flatList.add(g.name);
            flatList.addAll(g.stations);
        }

        adapter = new RadioAdapter(flatList, this::onStationSelected);
        rv.setAdapter(adapter);

        btnPlayStop.setOnClickListener(v -> {
            if (isPlaying) stopStation();
            else if (currentStation != null) playStation(currentStation);
        });

        MainActivity main = (MainActivity) getActivity();
        if (main != null) {
            boolean gifEnabled = main.getPrefs().isGifEnabled();
            gifContainer.setVisibility(gifEnabled ? View.VISIBLE : View.GONE);
            if (gifEnabled) main.loadGifInto(gifView);
        }

        return root;
    }

    private void onStationSelected(RadioStation station) {
        if (currentStation != null && currentStation.url.equals(station.url) && isPlaying) {
            stopStation();
        } else {
            currentStation = station;
            playStation(station);
        }
    }

    private void playStation(RadioStation station) {
        MainActivity main = (MainActivity) getActivity();
        if (main == null) return;
        currentStation = station;
        isPlaying = true;
        tvNowPlaying.setText("▶ " + station.name);
        btnPlayStop.setImageResource(android.R.drawable.ic_media_pause);
        adapter.setPlayingStation(station);
        if (main.getAudioService() != null)
            main.getAudioService().playRadio(station.url, station.name);
    }

    private void stopStation() {
        MainActivity main = (MainActivity) getActivity();
        if (main == null) return;
        isPlaying = false;
        tvNowPlaying.setText("— " + (currentStation != null ? currentStation.name : "Select a station"));
        btnPlayStop.setImageResource(android.R.drawable.ic_media_play);
        adapter.setPlayingStation(null);
        if (main.getAudioService() != null)
            main.getAudioService().stopRadio();
    }

    public void refreshGifVisibility() {
        if (gifContainer == null) return;
        MainActivity main = (MainActivity) getActivity();
        if (main == null) return;
        boolean enabled = main.getPrefs().isGifEnabled();
        gifContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (enabled) main.loadGifInto(gifView);
    }
}
