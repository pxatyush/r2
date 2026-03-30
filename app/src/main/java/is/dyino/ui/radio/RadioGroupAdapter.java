package is.dyino.ui.radio;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import is.dyino.R;
import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;

public class RadioGroupAdapter extends RecyclerView.Adapter<RadioGroupAdapter.VH> {

    public interface StationClickListener  { void onStation(RadioStation s); }
    public interface SwipeActionListener  {
        void onFavourite(RadioStation s);
        void onArchive(RadioStation s);
    }

    private final List<RadioGroup>    groups;
    private final StationClickListener clickL;
    private SwipeActionListener        swipeL;
    private RadioStation               activeStation;
    private final ColorConfig          colors;
    private final AppPrefs             prefs;

    public RadioGroupAdapter(List<RadioGroup> groups, StationClickListener click,
                             AppPrefs prefs, ColorConfig colors) {
        this.groups = groups; this.clickL = click;
        this.prefs = prefs; this.colors = colors;
    }
    public void setSwipeListener(SwipeActionListener l) { this.swipeL = l; }
    public void setActiveStation(RadioStation s) { activeStation = s; notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_radio_group, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        RadioGroup group = groups.get(pos);
        h.tvGroupName.setText(group.getName());
        h.tvGroupName.setTextColor(colors.accent());
        h.stationsContainer.removeAllViews();

        List<RadioStation> stations = group.getStations();
        for (RadioStation station : stations) {
            if (prefs.isArchived(AppPrefs.stationKey(station.getName(), station.getUrl(), station.getGroup())))
                continue; // skip archived

            View sv = LayoutInflater.from(h.itemView.getContext())
                    .inflate(R.layout.item_radio_station, h.stationsContainer, false);

            TextView tvName  = sv.findViewById(R.id.tvStationName);
            View eqView      = sv.findViewById(R.id.equalizerView);
            View root        = sv.findViewById(R.id.stationRoot);

            tvName.setText(station.getName());
            boolean active = activeStation != null
                    && activeStation.getUrl().equals(station.getUrl());

            if (active) {
                applyActiveStyle(root, tvName);
                eqView.setVisibility(View.VISIBLE);
                animEq(sv.findViewById(R.id.eq1), 400, 5, 15);
                animEq(sv.findViewById(R.id.eq2), 600, 9, 20);
                animEq(sv.findViewById(R.id.eq3), 500, 4, 13);
            } else {
                applyInactiveStyle(root, tvName);
                eqView.setVisibility(View.GONE);
            }

            sv.setOnClickListener(v -> {
                activeStation = station;
                notifyDataSetChanged();
                clickL.onStation(station);
            });

            // Swipe gesture on this item view
            attachSwipe(sv, station, h.stationsContainer);

            h.stationsContainer.addView(sv);
        }

        // Archived show/hide toggle at bottom of each group
        if (pos == groups.size() - 1) {
            addArchivedSection(h.stationsContainer);
        }
    }

    private void applyActiveStyle(View root, TextView tvName) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dpToPx(root, 12));
        gd.setColor(colors.stationActiveBg());
        gd.setStroke((int) dpToPx(root, 1), colors.stationActiveBorder());
        root.setBackground(gd);
        tvName.setTextColor(colors.stationTextActive());
    }

    private void applyInactiveStyle(View root, TextView tvName) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dpToPx(root, 12));
        gd.setColor(colors.bgCard2());
        root.setBackground(gd);
        tvName.setTextColor(colors.textPrimary());
    }

    private void attachSwipe(View sv, RadioStation station, LinearLayout container) {
        // Manual swipe detection using touch listener
        sv.setOnLongClickListener(v -> true); // consume long clicks

        final float[] startX = {0};
        final boolean[] swiped = {false};

        sv.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    startX[0] = event.getX();
                    swiped[0] = false;
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - startX[0];
                    if (Math.abs(dx) > 60 && !swiped[0]) {
                        swiped[0] = true;
                        // Animate slide
                        v.animate().translationX(dx > 0 ? 300 : -300).alpha(0f)
                                .setDuration(250).withEndAction(() -> {
                                    v.setTranslationX(0); v.setAlpha(1f);
                                    if (swipeL != null) {
                                        if (dx > 0) swipeL.onArchive(station);
                                        else        swipeL.onFavourite(station);
                                    }
                                    notifyDataSetChanged();
                                }).start();
                    }
                    break;
            }
            return false;
        });
    }

    private void addArchivedSection(LinearLayout container) {
        // Check if any archived stations exist
        if (prefs.getArchived().isEmpty()) return;

        View divider = new View(container.getContext());
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dlp.setMargins(0, 16, 0, 0);
        divider.setLayoutParams(dlp);
        divider.setBackgroundColor(colors.divider());
        container.addView(divider);

        TextView tvArchived = new TextView(container.getContext());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, 8, 0, 4);
        tvArchived.setLayoutParams(tlp);
        tvArchived.setText("Archived ▾");
        tvArchived.setTextColor(colors.textSecondary());
        tvArchived.setTextSize(13);
        tvArchived.setPadding((int)dpToPx(tvArchived, 8), (int)dpToPx(tvArchived, 8),
                (int)dpToPx(tvArchived, 8), (int)dpToPx(tvArchived, 8));
        tvArchived.setClickable(true);
        tvArchived.setFocusable(true);

        final LinearLayout archContainer = new LinearLayout(container.getContext());
        archContainer.setOrientation(LinearLayout.VERTICAL);
        archContainer.setVisibility(View.GONE);
        container.addView(tvArchived);
        container.addView(archContainer);

        // Populate archived list
        for (String key : prefs.getArchived()) {
            String[] parts = AppPrefs.splitKey(key);
            if (parts.length < 3) continue;
            RadioStation archived = new RadioStation(parts[0], parts[1], parts[2]);

            View sv = LayoutInflater.from(container.getContext())
                    .inflate(R.layout.item_radio_station, archContainer, false);
            TextView tvName = sv.findViewById(R.id.tvStationName);
            tvName.setText(archived.getName() + "  (archived)");
            tvName.setTextColor(colors.textSecondary());

            // Swipe to unarchive
            final float[] sx = {0};
            sv.setOnTouchListener((v, ev) -> {
                if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) sx[0] = ev.getX();
                if (ev.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                    float dx = ev.getX() - sx[0];
                    if (Math.abs(dx) > 60) {
                        v.animate().translationX(dx > 0 ? 300 : -300).alpha(0f)
                                .setDuration(250).withEndAction(() -> {
                                    v.setTranslationX(0); v.setAlpha(1f);
                                    prefs.removeArchived(key);
                                    notifyDataSetChanged();
                                }).start();
                    }
                }
                return false;
            });
            archContainer.addView(sv);
        }

        tvArchived.setOnClickListener(v -> {
            boolean show = archContainer.getVisibility() == View.GONE;
            archContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            tvArchived.setText(show ? "Archived ▴" : "Archived ▾");
        });
    }

    @Override public int getItemCount() { return groups.size(); }

    private void animEq(View bar, long dur, int minDp, int maxDp) {
        if (bar == null) return;
        float d = bar.getContext().getResources().getDisplayMetrics().density;
        ValueAnimator a = ValueAnimator.ofInt((int)(minDp*d), (int)(maxDp*d));
        a.setDuration(dur);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.addUpdateListener(anim -> {
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.height = (int) anim.getAnimatedValue();
            bar.setLayoutParams(lp);
        });
        a.start();
    }

    private float dpToPx(View v, float dp) {
        return dp * v.getContext().getResources().getDisplayMetrics().density;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvGroupName; LinearLayout stationsContainer;
        VH(View v) {
            super(v);
            tvGroupName        = v.findViewById(R.id.tvGroupName);
            stationsContainer  = v.findViewById(R.id.stationsContainer);
        }
    }
}
