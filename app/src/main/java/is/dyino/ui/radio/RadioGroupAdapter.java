package is.dyino.ui.radio;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
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

import java.util.ArrayList;
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

    private final Map<String, Boolean> expandedMap = new HashMap<>();
    private final Map<String, Long>    lastTapTime = new HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;

    private ItemTouchHelper touchHelper;

    public RadioGroupAdapter(List<RadioGroup> groups, StationClickListener click,
                             FavouriteListener fav, AppPrefs prefs, ColorConfig colors,
                             SwipeActionListener swipe) {
        this.groups = groups; this.clickL = click; this.favL = fav;
        this.prefs  = prefs;  this.colors = colors; this.swipeL = swipe;
    }

    /** Apply saved group order from prefs, then attach drag helper */
    public void attachToRecyclerView(RecyclerView rv) {
        applySavedOrder();

        touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {

            @Override
            public int getMovementFlags(@NonNull RecyclerView rv2,
                                        @NonNull RecyclerView.ViewHolder vh) {
                int pos = vh.getAdapterPosition();
                if (pos < 0 || pos >= groups.size()) return 0;
                String gName = groups.get(pos).getName();
                // Only allow drag on collapsed groups
                boolean expanded = Boolean.TRUE.equals(expandedMap.get(gName));
                if (expanded) return 0;
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv2,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int f = from.getAdapterPosition();
                int t = to.getAdapterPosition();
                if (f < 0 || t < 0 || f >= groups.size() || t >= groups.size()) return false;
                Collections.swap(groups, f, t);
                notifyItemMoved(f, t);
                // Save order immediately
                List<String> names = new ArrayList<>();
                for (RadioGroup g : groups) names.add(g.getName());
                prefs.saveGroupOrder(names);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}

            @Override
            public boolean isLongPressDragEnabled() { return true; }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder vh, int state) {
                super.onSelectedChanged(vh, state);
                if (state == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    haptic(vh.itemView);
                    vh.itemView.setAlpha(0.85f);
                    vh.itemView.setScaleX(1.02f);
                    vh.itemView.setScaleY(1.02f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView rv2,
                                  @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv2, vh);
                vh.itemView.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(180).start();
            }
        });
        touchHelper.attachToRecyclerView(rv);
    }

    private void applySavedOrder() {
        List<String> saved = prefs.getGroupOrder();
        if (saved.isEmpty()) return;
        // Reorder groups list to match saved order
        List<RadioGroup> ordered = new ArrayList<>();
        for (String name : saved) {
            for (RadioGroup g : groups) {
                if (g.getName().equals(name)) { ordered.add(g); break; }
            }
        }
        // Append any new groups not in saved order
        for (RadioGroup g : groups) {
            if (!saved.contains(g.getName())) ordered.add(g);
        }
        groups.clear();
        groups.addAll(ordered);
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
        RadioGroup group    = groups.get(pos);
        String     gName    = group.getName();
        boolean    isArch   = "__ARCHIVED__".equals(gName);
        boolean    expanded = Boolean.TRUE.equals(expandedMap.get(gName));

        // Label
        if (isArch) {
            h.tvGroupLabel.setText("Archived");
            h.tvGroupLabel.setTextColor(colors.textSecondary());
            h.tvGroupLabel.setLetterSpacing(0.08f);
        } else {
            h.tvGroupLabel.setText(gName.toUpperCase());
            h.tvGroupLabel.setTextColor(expanded
                ? colors.radioGroupNameText() : colors.radioGroupCollapsed());
            h.tvGroupLabel.setLetterSpacing(0.10f);
        }
        h.tvGroupLabel.setTextSize(11f);

        // Arrow rotates to indicate expand state
        if (h.tvGroupArrow != null) {
            h.tvGroupArrow.setText("›");
            h.tvGroupArrow.setRotation(expanded ? 90f : 0f);
            h.tvGroupArrow.setTextColor(expanded ? colors.radioGroupNameText() : colors.radioGroupCollapsed());
        }

        h.stationsContainer.setBackground(null);
        h.stationsContainer.setPadding(0, 0, 0, 0);
        h.stationsContainer.removeAllViews();
        h.stationsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);

        h.headerRow.setOnClickListener(v -> {
            haptic(v);
            boolean nowExpanded = !Boolean.TRUE.equals(expandedMap.get(gName));
            expandedMap.put(gName, nowExpanded);
            // Animate arrow
            if (h.tvGroupArrow != null)
                h.tvGroupArrow.animate().rotation(nowExpanded ? 90f : 0f).setDuration(180).start();
            notifyItemChanged(pos);
        });

        if (expanded) {
            if (isArch) {
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
    private View buildStationView(VH h, RadioStation station, boolean isArch, String archKey) {
        View sv     = LayoutInflater.from(h.itemView.getContext())
                        .inflate(R.layout.item_radio_station, h.stationsContainer, false);
        TextView tv = sv.findViewById(R.id.tvStationName);
        View eq     = sv.findViewById(R.id.equalizerView);
        View root   = sv.findViewById(R.id.stationRoot);

        tv.setText(station.getName());

        boolean active = !isArch && activeStation != null
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

        if (isArch) {
            tv.setTextColor(colors.textSecondary());
            attachSwipe(sv, station, true, archKey);
        } else {
            sv.setOnClickListener(v -> {
                String url = station.getUrl();
                long   now = System.currentTimeMillis();
                Long   lst = lastTapTime.get(url);

                if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                    lastTapTime.remove(url);
                    haptic(v);
                    String key = AppPrefs.stationKey(station.getName(), station.getUrl(), station.getGroup());
                    boolean fav = prefs.isFavourite(key);
                    if (fav) prefs.removeFavourite(key); else prefs.addFavourite(key);
                    if (favL != null) favL.onFavouriteToggled(station, !fav);
                    notifyDataSetChanged();
                } else {
                    lastTapTime.put(url, now);
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
    private void attachSwipe(View sv, RadioStation station, boolean isArch, String archKey) {
        final float[] sx = {0};
        final boolean[] swiped = {false};
        sv.setOnTouchListener((v, ev) -> {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN: sx[0] = ev.getX(); swiped[0] = false; break;
                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getX() - sx[0];
                    if (Math.abs(dx) > 90 && !swiped[0]) {
                        swiped[0] = true; haptic(v);
                        v.animate()
                         .translationX(dx > 0 ? v.getWidth() : -v.getWidth()).alpha(0f)
                         .setDuration(400).setInterpolator(new DecelerateInterpolator(2f))
                         .withEndAction(() -> {
                             v.setTranslationX(0); v.setAlpha(1f);
                             if (swipeL != null) {
                                 if (isArch) swipeL.onUnarchive(archKey);
                                 else        swipeL.onArchive(station);
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
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(12 * dp);
        gd.setColor(colors.stationActiveBg()); gd.setStroke((int)(1.5f*dp), colors.stationActiveBorder());
        root.setBackground(gd); tv.setTextColor(colors.stationTextActive());
    }

    private void applyInactive(View root, TextView tv) {
        float dp = dp(root);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(12 * dp);
        gd.setColor(colors.stationBg());
        root.setBackground(gd); tv.setTextColor(colors.stationText());
    }

    private void applyClickGlow(View root, TextView tv) {
        float dp = dp(root);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(12 * dp);
        gd.setColor(colors.stationActiveBg()); gd.setStroke((int)(2f*dp), colors.stationClickGlow());
        root.setBackground(gd); tv.setTextColor(colors.stationTextActive());
    }

    private void animEq(View bar, long dur, int minDp, int maxDp) {
        if (bar == null) return;
        float d = bar.getContext().getResources().getDisplayMetrics().density;
        ValueAnimator a = ValueAnimator.ofInt((int)(minDp*d), (int)(maxDp*d));
        a.setDuration(dur); a.setRepeatMode(ValueAnimator.REVERSE); a.setRepeatCount(ValueAnimator.INFINITE);
        a.addUpdateListener(anim -> { ViewGroup.LayoutParams lp = bar.getLayoutParams(); lp.height=(int)anim.getAnimatedValue(); bar.setLayoutParams(lp); });
        a.start();
    }

    private void haptic(View v) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) v.getContext()
                    .getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(18, 200));
                    return;
                }
            }
            @SuppressWarnings("deprecation")
            Vibrator vib = (Vibrator) v.getContext()
                .getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vib.vibrate(VibrationEffect.createOneShot(18, 200));
            else vib.vibrate(18);
        } catch (Exception ignored) {}
    }

    private float dp(View v) { return v.getContext().getResources().getDisplayMetrics().density; }

    @Override public int getItemCount() { return groups.size(); }

    static class VH extends RecyclerView.ViewHolder {
        View         headerRow;
        TextView     tvGroupLabel, tvGroupArrow;
        LinearLayout stationsContainer;
        VH(View v) {
            super(v);
            headerRow        = v.findViewById(R.id.tvGroupName);  // the clickable LinearLayout
            tvGroupLabel     = v.findViewById(R.id.tvGroupLabel);
            tvGroupArrow     = v.findViewById(R.id.tvGroupArrow);
            stationsContainer= v.findViewById(R.id.stationsContainer);
        }
    }
}