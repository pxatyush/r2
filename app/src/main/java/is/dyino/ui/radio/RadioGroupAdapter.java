package is.dyino.ui.radio;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import is.dyino.R;
import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;

public class RadioGroupAdapter extends RecyclerView.Adapter<RadioGroupAdapter.VH> {

    public interface StationClickListener { void onStation(RadioStation s); }
    public interface SwipeActionListener {
        void onFavourite(RadioStation s);
        void onArchive(RadioStation s);
        void onUnarchive(String key);
    }

    private final List<RadioGroup>     groups;
    private final StationClickListener clickL;
    private final AppPrefs             prefs;
    private final ColorConfig          colors;
    private final SwipeActionListener  swipeL;
    private RadioStation activeStation;

    public RadioGroupAdapter(List<RadioGroup> groups, StationClickListener click,
                             AppPrefs prefs, ColorConfig colors, SwipeActionListener swipe) {
        this.groups = groups; this.clickL = click;
        this.prefs = prefs; this.colors = colors; this.swipeL = swipe;
    }

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
        boolean isArchivedGroup = group.getName().equals("__ARCHIVED__");

        // Group header
        if (isArchivedGroup) {
            h.tvGroupName.setText("Archived ▾");
            h.tvGroupName.setTextColor(colors.textSecondary());
        } else {
            h.tvGroupName.setText(group.getName());
            h.tvGroupName.setTextColor(colors.accent());
        }

        h.stationsContainer.removeAllViews();

        // For archived group, hide by default, toggle on header click
        if (isArchivedGroup) {
            h.stationsContainer.setVisibility(View.GONE);
            h.tvGroupName.setOnClickListener(v -> {
                boolean show = h.stationsContainer.getVisibility() == View.GONE;
                h.stationsContainer.setVisibility(show ? View.VISIBLE : View.GONE);
                h.tvGroupName.setText(show ? "Archived ▴" : "Archived ▾");
            });
            // Build archived station rows
            for (RadioStation station : group.getStations()) {
                // station.getGroup() holds the original key for archived items
                String archivedKey = station.getGroup();
                View sv = buildStationView(h, station, true, archivedKey);
                h.stationsContainer.addView(sv);
            }
        } else {
            h.tvGroupName.setOnClickListener(null);
            for (RadioStation station : group.getStations()) {
                if (prefs.isArchived(AppPrefs.stationKey(station.getName(),
                        station.getUrl(), station.getGroup()))) continue;
                View sv = buildStationView(h, station, false, null);
                h.stationsContainer.addView(sv);
            }
        }
    }

    private View buildStationView(VH h, RadioStation station, boolean isArchived, String archivedKey) {
        View sv = LayoutInflater.from(h.itemView.getContext())
                .inflate(R.layout.item_radio_station, h.stationsContainer, false);

        TextView tvName = sv.findViewById(R.id.tvStationName);
        View eqView     = sv.findViewById(R.id.equalizerView);
        View root       = sv.findViewById(R.id.stationRoot);

        tvName.setText(station.getName());

        boolean active = !isArchived && activeStation != null
                && activeStation.getUrl().equals(station.getUrl());

        if (active) {
            applyActive(root, tvName);
            eqView.setVisibility(View.VISIBLE);
            animEq(sv.findViewById(R.id.eq1), 400, 4, 14);
            animEq(sv.findViewById(R.id.eq2), 600, 8, 18);
            animEq(sv.findViewById(R.id.eq3), 500, 3, 12);
        } else {
            applyInactive(root, tvName);
            eqView.setVisibility(View.GONE);
        }

        if (isArchived) {
            tvName.setTextColor(colors.textSecondary());
            // Swipe left/right to unarchive
            attachSwipe(sv, station, isArchived, archivedKey);
        } else {
            sv.setOnClickListener(v -> {
                activeStation = station;
                notifyDataSetChanged();
                clickL.onStation(station);
            });
            attachSwipe(sv, station, false, null);
        }

        return sv;
    }

    @SuppressWarnings("ClickableViewAccessibility")
    private void attachSwipe(View sv, RadioStation station, boolean isArchived, String archivedKey) {
        final float[] startX = {0};
        final boolean[] swiped = {false};

        sv.setOnTouchListener((v, ev) -> {
            switch (ev.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    startX[0] = ev.getX();
                    swiped[0] = false;
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dx = ev.getX() - startX[0];
                    if (Math.abs(dx) > 70 && !swiped[0]) {
                        swiped[0] = true;
                        float targetX = dx > 0 ? sv.getWidth() : -sv.getWidth();
                        v.animate().translationX(targetX).alpha(0f).setDuration(200)
                                .withEndAction(() -> {
                                    v.setTranslationX(0); v.setAlpha(1f);
                                    if (swipeL != null) {
                                        if (isArchived) {
                                            swipeL.onUnarchive(archivedKey);
                                        } else {
                                            if (dx < 0) swipeL.onFavourite(station);
                                            else        swipeL.onArchive(station);
                                        }
                                    }
                                    notifyDataSetChanged();
                                }).start();
                    }
                    break;
            }
            return false;
        });
    }

    private void applyActive(View root, TextView tvName) {
        float dp = root.getContext().getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(12 * dp);
        gd.setColor(colors.stationActiveBg());
        gd.setStroke((int)(1.5f * dp), colors.stationActiveBorder());
        root.setBackground(gd);
        tvName.setTextColor(colors.stationTextActive());
    }

    private void applyInactive(View root, TextView tvName) {
        float dp = root.getContext().getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(12 * dp);
        gd.setColor(colors.stationBg());
        root.setBackground(gd);
        tvName.setTextColor(colors.stationText());
    }

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

    @Override public int getItemCount() { return groups.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvGroupName; LinearLayout stationsContainer;
        VH(View v) {
            super(v);
            tvGroupName       = v.findViewById(R.id.tvGroupName);
            stationsContainer = v.findViewById(R.id.stationsContainer);
        }
    }
}
