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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import is.dyino.R;
import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;

/**
 * Flat-list adapter: TYPE_HEADER + TYPE_STATION.
 * Drag-to-reorder is DISABLED here — it lives only in the category manager dialog.
 * Duplicate URLs within the same group are automatically removed.
 */
public class RadioGroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface StationClickListener { void onStation(RadioStation s); }
    public interface FavouriteListener    { void onFavouriteToggled(RadioStation s, boolean isFav); }
    public interface SwipeActionListener  {
        void onArchive(RadioStation s);
        void onUnarchive(String key);
    }

    private static final int TYPE_HEADER  = 0;
    private static final int TYPE_STATION = 1;

    // ── Flat item ─────────────────────────────────────────────────
    private static final class FlatItem {
        final int          type;
        final RadioGroup   group;
        final RadioStation station;
        final boolean      isArch;
        final String       archKey;

        static FlatItem header(RadioGroup g) {
            return new FlatItem(TYPE_HEADER, g, null, false, null);
        }
        static FlatItem station(RadioStation s, boolean isArch, String archKey) {
            return new FlatItem(TYPE_STATION, null, s, isArch, archKey);
        }
        private FlatItem(int t, RadioGroup g, RadioStation s, boolean arch, String aKey) {
            type = t; group = g; station = s; isArch = arch; archKey = aKey;
        }
    }

    // ── State ─────────────────────────────────────────────────────
    private final List<RadioGroup>     groups;
    private final List<FlatItem>       flat       = new ArrayList<>();
    private final Map<String, Boolean> expandedMap = new HashMap<>();
    private final Map<String, Long>    lastTapTime = new HashMap<>();
    private static final long DOUBLE_TAP_MS = 350;

    private final StationClickListener clickL;
    private final FavouriteListener    favL;
    private final AppPrefs             prefs;
    private final ColorConfig          colors;
    private final SwipeActionListener  swipeL;
    private RadioStation               activeStation;

    public RadioGroupAdapter(List<RadioGroup> groups,
                             StationClickListener click, FavouriteListener fav,
                             AppPrefs prefs, ColorConfig colors, SwipeActionListener swipe) {
        this.groups = groups; this.clickL = click; this.favL = fav;
        this.prefs = prefs; this.colors = colors; this.swipeL = swipe;
        rebuildFlat();
    }

    // ── Flat list builder (deduplicates URLs per group) ───────────
    private void rebuildFlat() {
        flat.clear();
        for (RadioGroup g : groups) {
            flat.add(FlatItem.header(g));
            if (!Boolean.TRUE.equals(expandedMap.get(g.getName()))) continue;

            boolean isArch = "__ARCHIVED__".equals(g.getName());
            java.util.LinkedHashSet<String> seenUrls = new java.util.LinkedHashSet<>();

            for (RadioStation s : g.getStations()) {
                String url = s.getUrl().trim().toLowerCase();
                if (!seenUrls.add(url)) continue; // skip duplicate URL

                if (!isArch && prefs.isArchived(
                        AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup()))) continue;

                String aKey = isArch
                        ? AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup()) : null;
                flat.add(FlatItem.station(s, isArch, aKey));
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────
    public void setActiveStation(RadioStation s) { activeStation = s; notifyDataSetChanged(); }

    /** No drag helper — drag lives only in the category manager dialog. */
    public void attachToRecyclerView(RecyclerView rv) {
        applySavedOrder();
        rebuildFlat();
        notifyDataSetChanged();
    }

    private void applySavedOrder() {
        List<String> saved = prefs.getGroupOrder();
        if (saved.isEmpty()) return;
        List<RadioGroup> ordered = new ArrayList<>();
        for (String name : saved)
            for (RadioGroup g : groups)
                if (g.getName().equals(name)) { ordered.add(g); break; }
        for (RadioGroup g : groups)
            if (!saved.contains(g.getName())) ordered.add(g);
        groups.clear();
        groups.addAll(ordered);
    }

    // ── RecyclerView ──────────────────────────────────────────────
    @Override public int getItemCount()           { return flat.size(); }
    @Override public int getItemViewType(int pos) { return flat.get(pos).type; }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View v = inf.inflate(R.layout.item_radio_group_header, parent, false);
            return new HeaderVH(v);
        } else {
            View v = inf.inflate(R.layout.item_radio_station, parent, false);
            return new StationVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int pos) {
        FlatItem item = flat.get(pos);
        if (item.type == TYPE_HEADER) bindHeader((HeaderVH) vh, item);
        else                          bindStation((StationVH) vh, item);
    }

    // ── Header ────────────────────────────────────────────────────
    private void bindHeader(HeaderVH h, FlatItem item) {
        RadioGroup g     = item.group;
        String     name  = g.getName();
        boolean isArch   = "__ARCHIVED__".equals(name);
        boolean expanded = Boolean.TRUE.equals(expandedMap.get(name));

        // Count visible stations for the badge
        int stationCount = 0;
        if (!isArch) {
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            for (RadioStation s : g.getStations()) {
                String url = s.getUrl().trim().toLowerCase();
                if (seen.add(url) && !prefs.isArchived(
                        AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup())))
                    stationCount++;
            }
        } else {
            stationCount = g.getStations().size();
        }

        String displayName = isArch ? "Archived" : name;
        h.tvLabel.setText(displayName);
        h.tvLabel.setTextColor(expanded ? colors.radioGroupNameText()
                                        : colors.radioGroupCollapsed());

        if (h.tvCount != null) {
            h.tvCount.setText(String.valueOf(stationCount));
            h.tvCount.setVisibility(stationCount > 0 ? View.VISIBLE : View.GONE);
            // badge bg: accent dim when expanded, muted when collapsed
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            float dp = h.itemView.getContext().getResources().getDisplayMetrics().density;
            badgeBg.setCornerRadius(10 * dp);
            badgeBg.setColor(expanded ? colors.accentDim() : 0x22FFFFFF);
            h.tvCount.setBackground(badgeBg);
            h.tvCount.setTextColor(expanded ? colors.accent() : colors.radioGroupCollapsed());
        }

        if (h.tvArrow != null) {
            h.tvArrow.setRotation(expanded ? 90f : 0f);
            h.tvArrow.setTextColor(expanded ? colors.radioGroupNameText()
                                            : colors.radioGroupCollapsed());
        }

        h.itemView.setOnClickListener(v -> {
            haptic(v);
            boolean now = !Boolean.TRUE.equals(expandedMap.get(name));
            expandedMap.put(name, now);
            if (h.tvArrow != null)
                h.tvArrow.animate().rotation(now ? 90f : 0f).setDuration(200).start();
            rebuildFlat();
            notifyDataSetChanged();
        });
    }

    // ── Station ───────────────────────────────────────────────────
    @SuppressWarnings("ClickableViewAccessibility")
    private void bindStation(StationVH h, FlatItem item) {
        RadioStation station = item.station;
        boolean isArch = item.isArch;

        h.tvName.setText(station.getName());

        // Cancel old EQ animators from recycled view
        if (h.eq1Anim != null) { h.eq1Anim.cancel(); h.eq1Anim = null; }
        if (h.eq2Anim != null) { h.eq2Anim.cancel(); h.eq2Anim = null; }
        if (h.eq3Anim != null) { h.eq3Anim.cancel(); h.eq3Anim = null; }

        boolean active = !isArch && activeStation != null
                && activeStation.getUrl().equals(station.getUrl());

        if (active) {
            applyActive(h.root, h.tvName);
            h.eqView.setVisibility(View.VISIBLE);
            h.eq1Anim = animEq(h.eq1, 400, 4, 14);
            h.eq2Anim = animEq(h.eq2, 600, 8, 18);
            h.eq3Anim = animEq(h.eq3, 500, 3, 12);
        } else {
            applyInactive(h.root, h.tvName);
            h.eqView.setVisibility(View.GONE);
        }

        if (isArch) {
            h.tvName.setTextColor(colors.textSecondary());
            h.root.setOnClickListener(null);
            attachSwipe(h.root, station, true, item.archKey);
        } else {
            h.root.setOnClickListener(v -> {
                String url = station.getUrl();
                long now   = System.currentTimeMillis();
                Long lst   = lastTapTime.get(url);
                if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                    // Double tap = favourite toggle
                    lastTapTime.remove(url);
                    haptic(v);
                    String key = AppPrefs.stationKey(
                            station.getName(), url, station.getGroup());
                    boolean fav = prefs.isFavourite(key);
                    if (fav) prefs.removeFavourite(key); else prefs.addFavourite(key);
                    if (favL != null) favL.onFavouriteToggled(station, !fav);
                    notifyDataSetChanged();
                } else {
                    lastTapTime.put(url, now);
                    applyClickGlow(h.root, h.tvName);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        activeStation = station; notifyDataSetChanged();
                    }, 80);
                    if (clickL != null) clickL.onStation(station);
                }
            });
            attachSwipe(h.root, station, false, null);
        }
    }

    @SuppressWarnings("ClickableViewAccessibility")
    private void attachSwipe(View sv, RadioStation station, boolean isArch, String archKey) {
        final float[]   sx     = {0};
        final boolean[] swiped = {false};
        sv.setOnTouchListener((v, ev) -> {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sx[0] = ev.getX(); swiped[0] = false; break;
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
                             rebuildFlat(); notifyDataSetChanged();
                         }).start();
                    }
                    break;
            }
            return false;
        });
    }

    // ── Style helpers ─────────────────────────────────────────────
    private void applyActive(View root, TextView tv) {
        float dp = dp(root);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadius(12 * dp);
        gd.setColor(colors.stationActiveBg()); gd.setStroke((int)(1.5f * dp), colors.stationActiveBorder());
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
        gd.setColor(colors.stationActiveBg()); gd.setStroke((int)(2f * dp), colors.stationClickGlow());
        root.setBackground(gd); tv.setTextColor(colors.stationTextActive());
    }

    private ValueAnimator animEq(View bar, long dur, int minDp, int maxDp) {
        if (bar == null) return null;
        float d = bar.getContext().getResources().getDisplayMetrics().density;
        ValueAnimator a = ValueAnimator.ofInt((int)(minDp * d), (int)(maxDp * d));
        a.setDuration(dur); a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.addUpdateListener(anim -> {
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.height = (int) anim.getAnimatedValue(); bar.setLayoutParams(lp);
        });
        a.start(); return a;
    }

    private void haptic(View v) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) v.getContext()
                        .getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(50, 255));
                    return;
                }
            }
            @SuppressWarnings("deprecation")
            Vibrator vib = (Vibrator) v.getContext()
                    .getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vib.vibrate(VibrationEffect.createOneShot(50, 255));
            else vib.vibrate(50);
        } catch (Exception ignored) {}
    }

    private float dp(View v) {
        return v.getContext().getResources().getDisplayMetrics().density;
    }

    // ── ViewHolders ───────────────────────────────────────────────
    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvLabel, tvArrow, tvCount;
        HeaderVH(View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvGroupLabel);
            tvArrow = v.findViewById(R.id.tvGroupArrow);
            tvCount = v.findViewById(R.id.tvGroupCount);
        }
    }

    static class StationVH extends RecyclerView.ViewHolder {
        final View         root, eqView, eq1, eq2, eq3;
        final TextView     tvName;
        ValueAnimator      eq1Anim, eq2Anim, eq3Anim;
        StationVH(View v) {
            super(v);
            root   = v.findViewById(R.id.stationRoot);
            tvName = v.findViewById(R.id.tvStationName);
            eqView = v.findViewById(R.id.equalizerView);
            eq1    = v.findViewById(R.id.eq1);
            eq2    = v.findViewById(R.id.eq2);
            eq3    = v.findViewById(R.id.eq3);
        }
    }
}
