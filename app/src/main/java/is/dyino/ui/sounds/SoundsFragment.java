package is.dyino.ui.sounds;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import is.dyino.MainActivity;
import is.dyino.R;
import is.dyino.model.SoundItem;
import is.dyino.service.AudioService;
import is.dyino.util.SoundLibrary;
import pl.droidsonroids.gif.GifImageView;

public class SoundsFragment extends Fragment {

    private SoundsAdapter adapter;
    private List<Object> soundItems;
    private TextView tvActiveSounds;
    private View gifContainer;
    private GifImageView gifView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_sounds, container, false);

        tvActiveSounds = root.findViewById(R.id.tv_active_sounds);
        gifContainer = root.findViewById(R.id.gif_container);
        gifView = root.findViewById(R.id.gif_view);
        Button btnStopAll = root.findViewById(R.id.btn_stop_all);

        RecyclerView rv = root.findViewById(R.id.rv_sounds);
        soundItems = SoundLibrary.getCategorizedSounds();

        GridLayoutManager glm = new GridLayoutManager(getContext(), 2);
        adapter = new SoundsAdapter(soundItems, new SoundsAdapter.OnSoundInteractionListener() {
            @Override
            public void onSoundToggle(SoundItem item, int position) {
                toggleSound(item, position);
            }
            @Override
            public void onVolumeChange(SoundItem item, float volume) {
                MainActivity main = (MainActivity) getActivity();
                if (main != null && main.getAudioService() != null) {
                    main.getAudioService().setSoundVolume(item.assetPath, volume);
                }
            }
        });
        glm.setSpanSizeLookup(adapter.getSpanSizeLookup());
        rv.setLayoutManager(glm);
        rv.setAdapter(adapter);

        btnStopAll.setOnClickListener(v -> stopAllSounds());

        // GIF visibility
        MainActivity main = (MainActivity) getActivity();
        if (main != null) {
            boolean gifEnabled = main.getPrefs().isGifEnabled();
            gifContainer.setVisibility(gifEnabled ? View.VISIBLE : View.GONE);
            if (gifEnabled) main.loadGifInto(gifView);
        }

        return root;
    }

    private void toggleSound(SoundItem item, int position) {
        MainActivity main = (MainActivity) getActivity();
        if (main == null || main.getAudioService() == null) return;

        AudioService svc = main.getAudioService();
        item.isPlaying = !item.isPlaying;

        if (item.isPlaying) {
            svc.playSound(item.assetPath);
        } else {
            svc.stopSound(item.assetPath);
        }

        adapter.notifyItemChanged(position);
        updateActiveSoundsLabel(svc);
    }

    private void stopAllSounds() {
        MainActivity main = (MainActivity) getActivity();
        if (main == null || main.getAudioService() == null) return;

        main.getAudioService().stopAllSounds();
        for (Object o : soundItems) {
            if (o instanceof SoundItem) ((SoundItem) o).isPlaying = false;
        }
        adapter.notifyDataSetChanged();
        tvActiveSounds.setText("No sounds playing");
    }

    private void updateActiveSoundsLabel(AudioService svc) {
        int count = svc.getActiveSoundCount();
        if (count == 0) tvActiveSounds.setText("No sounds playing");
        else tvActiveSounds.setText(count + " sound" + (count > 1 ? "s" : "") + " playing");
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
