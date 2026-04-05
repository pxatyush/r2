package is.dyino.ui.radio;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import is.dyino.R;
import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;

public class RadioGroupAdapter extends RecyclerView.Adapter<RadioGroupAdapter.VH> {

    public interface StationClickListener { void onStation(RadioStation s); }
    public interface FavouriteListener    { void onFavouriteToggled(RadioStation s, boolean isFav); }
    public interface SwipeActionListener  {
        void onArchive(RadioStation s);
        void onUnarchive(String key);
    }

    private final List<RadioGroup>     groups;
    private final StationClickListener clickL;
    private final FavouriteListener    favL;
    private final AppPrefs             prefs;
    private final ColorConfig          colors;
    private final SwipeActionListener  swipeL;
    private RadioStation activeStation;

    // Expanded/collapsed state per group (default: collapsed)
    private final Map<String, Boolean> expandedMap = new HashMap<>();

    // Double-tap detection
    private final Map<String, Long> lastTapTime = new HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;

    // ItemTouchHelper for long-press drag reorder
    private ItemTouchHelper touchHelper;

    public RadioGroupAdapter(List<RadioGroup> groups, StationClickListener click,
                             FavouriteListener fav, AppPrefs prefs, ColorConfig colors,
                             SwipeActionListener swipe) {
        this.groups = groups; this.clickL = click; this.favL = fav;
        this.prefs  = prefs;  this.colors = colors; this.swipeL = swipe;
    }

    public void attachToRecyclerView(RecyclerView rv) {
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            private int dragFrom = -1;
            private int dragTo   = -1;

            @Override
            public boolean isLongPressDragEnabled() { return true; }

            @Override
            public boolean onMove(@NonNull RecyclerView rv2,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int f = from.getAdapterPosition();
                int t = to.getAdapterPosition();
                if (dragFrom == -1) dragFrom = f;
                dragTo = t;
                // Only allow drag when group is collapsed
                String gName = groups.get(f).getName();
                Boolean exp  = expandedMap.get(gName);
                if (exp != null && exp) return false; // expanded: no move
                Collections.swap(groups, f, t);
                notifyItemMoved(f, t);
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView rv2,
                                  @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv2, vh);
                dragFrom = dragTo = -1;
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
        });
        touchHelper.attachToRecyclerView(rv);
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
        String gName     = group.getName();
        boolean isArchived = "__ARCHIVED__".equals(gName);

        boolean expanded = Boolean.TRUE.equals(expandedMap.get(gName));

        // Group header styling
        if (isArchived) {
            h.tvGroupName.setText(expanded ? "Archived ▴" : "Archived ▾");
            h.tvGroupName.setTextColor(colors.textSecondary());
            h.tvGroupName.setTextSize(13f);
        } else {
            h.tvGroupName.setText(gName + (expanded ? " ▴" : " ▾"));
            h.tvGroupName.setTextColor(expanded ? colors.radioGroupNameText()
                                                : colors.radioGroupCollapsed());
            h.tvGroupName.setTextSize(15f);
        }

        // Remove shared card background from container
        h.stationsContainer.setBackground(null);
        h.stationsContainer.setPadding(0, 0, 0, 0);
        h.stationsContainer.removeAllViews();
        h.stationsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);

        // Toggle expand on header click
        h.tvGroupName.setOnClickListener(v -> {
            haptic(v);
            boolean nowExpanded = !Boolean.TRUE.equals(expandedMap.get(gName));
            expandedMap.put(gName, nowExpanded);
            notifyItemChanged(pos);
        });

        if (expanded) {
            if (isArchived) {
                for (RadioStation s : group.getStations())
                    h.stationsContainer.addView(buildStationView(h, s, true, s.getGroup()));
            } else {
                for (RadioStation s : group.getStations()) {
                    if (prefs.isArchived(AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup()))) continue;
                    h.stationsContainer.addView(buildStationView(h, s, false, null));
                }
            }
        }
    }

    @SuppressWarnings("ClickableViewAccessibility")
    private View buildStationView(VH h, RadioStation station, boolean isArchived, String archivedKey) {
        View sv     = LayoutInflater.from(h.itemView.getContext())
                        .inflate(R.layout.item_radio_station, h.stationsContainer, false);
        TextView tv = sv.findViewById(R.id.tvStationName);
        View eq     = sv.findViewById(R.id.equalizerView);
        View root   = sv.findViewById(R.id.stationRoot);

        tv.setText(station.getName());

        boolean active = !isArchived && activeStation != null
                && activeStation.getUrl().equals(station.getUrl());

        if (active) {
            applyActive(root, tv);
            eq.setVisibility(View.VISIBLE);
            animEq(sv.findViewById(R.id.eq1), 400, 4, 14);
            animEq(sv.findViewById(R.id.eq2), 600, 8, 18);
            animEq(sv.findViewById(R.id.eq3), 500, 3, 12);
        } else {
            applyInactive(root, tv);
            eq.setVisibility(View.GONE);
        }

        if (isArchived) {
            tv.setTextColor(colors.textSecondary());
            attachSwipe(sv, station, true, archivedKey);
        } else {
            sv.setOnClickListener(v -> {
                String url = station.getUrl();
                long   now = System.currentTimeMillis();
                Long   last = lastTapTime.get(url);

                if (last != null && (now - last) < DOUBLE_TAP_MS) {
                    // Double-tap → favourite
                    lastTapTime.remove(url);
                    haptic(v);
                    String key  = AppPrefs.stationKey(station.getName(), station.getUrl(), station.getGroup());
                    boolean fav = prefs.isFavourite(key);
                    if (fav) prefs.removeFavourite(key); else prefs.addFavourite(key);
                    if (favL != null) favL.onFavouriteToggled(station, !fav);
                    notifyDataSetChanged();
                } else {
                    // Single tap → play / stop
                    lastTapTime.put(url, now);
                    // Instant glow feedback
                    applyClickGlow(root, tv);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        activeStation = station;
                        notifyDataSetChanged();
                    }, 80);
                    if (clickL != null) clickL.onStation(station);
                }
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
                case MotionEvent.ACTION_DOWN:
                    startX[0] = ev.getX(); swiped[0] = false; break;
                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getX() - startX[0];
                    if (Math.abs(dx) > 90 && !swiped[0]) {
                        swiped[0] = true;
                        haptic(v);
                        float target = dx > 0 ? sv.getWidth() : -sv.getWidth();
                        v.animate()
                         .translationX(target).alpha(0f)
                         .setDuration(400)
                         .setInterpolator(new DecelerateInterpolator(2f))
                         .withEndAction(() -> {
                             v.setTranslationX(0); v.setAlpha(1f);
                             if (swipeL != null) {
                                 if (isArchived) swipeL.onUnarchive(archivedKey);
                                 else            swipeL.onArchive(station);
                             }
                             notifyDataSetChanged();
                         }).start();
                    }
                    break;
            }
            return false;
        });
    }

    private void applyActive(View root, TextView tv) {
        float dp = dp(root);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(12 * dp);
        gd.setColor(colors.stationActiveBg());
        gd.setStroke((int)(1.5f * dp), colors.stationActiveBorder());
        root.setBackground(gd);
        tv.setTextColor(colors.stationTextActive());
    }

    private void applyInactive(View root, TextView tv) {
        float dp = dp(root);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(12 * dp);
        gd.setColor(colors.stationBg());
        root.setBackground(gd);
        tv.setTextColor(colors.stationText());
    }

    /** Instant click glow — shown for ~80ms before full selection applies */
    private void applyClickGlow(View root, TextView tv) {
        float dp = dp(root);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(12 * dp);
        gd.setColor(colors.stationActiveBg());
        gd.setStroke((int)(2f * dp), colors.stationClickGlow());
        root.setBackground(gd);
        tv.setTextColor(colors.stationTextActive());
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

    @SuppressWarnings("deprecation")
    private void haptic(View v) {
        try {
            Vibrator vib = (Vibrator) v.getContext()
                .getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vib.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
            else vib.vibrate(12);
        } catch (Exception ignored) {}
    }

    private float dp(View v) {
        return v.getContext().getResources().getDisplayMetrics().density;
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