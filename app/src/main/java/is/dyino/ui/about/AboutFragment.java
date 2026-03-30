package is.dyino.ui.about;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import is.dyino.R;

public class AboutFragment extends Fragment {

    public interface OnBackListener { void onAboutBack(); }
    private OnBackListener backListener;
    public void setOnBackListener(OnBackListener l) { this.backListener = l; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_about, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnAboutBack).setOnClickListener(v -> {
            if (backListener != null) backListener.onAboutBack();
        });

        link(view, R.id.linkGithub,    "https://github.com/pxatyush");
        link(view, R.id.linkInstagram, "https://instagram.com/pxatyush");
        link(view, R.id.linkTelegram,  "https://t.me/pxatyush");
        link(view, R.id.linkLinkedin,  "https://linkedin.com/in/pxatyush");
        link(view, R.id.linkTwitter,   "https://twitter.com/pxatyush");
    }

    private void link(View root, int id, String url) {
        root.findViewById(id).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {}
        });
    }
}
