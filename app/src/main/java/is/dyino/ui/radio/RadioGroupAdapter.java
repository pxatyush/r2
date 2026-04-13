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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import is.dyino.R;
import is.dyino.model.RadioGroup;
import is.dyino.model.RadioStation;
import is.dyino.util.AppPrefs;
import is.dyino.util.ColorConfig;

/**
 * Flat-list adapter with two view types: TYPE_HEADER and TYPE_STATION.
 *
 * Header rows show a card-style pill with the category name, station count
 * badge, and expand arrow.  When expanded, station rows sit inside a visually
 * distinct card background making the section feel "3D / raised".
 *
 * Drag-to-reorder is NOT attached here — it lives only in the category dialog.
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
    private static final class Item {
        final int          type;
        final RadioGroup   group;
        final RadioStation station;
        final boolean      isArch;
        final String       archKey;
        final boolean      isFirst; // first station in group (top-rounded corners)
        final boolean      isLast;  // last station in group (bottom-rounded corners)

        static Item header(RadioGroup g) {
            return new Item(TYPE_HEADER, g, null, false, null, false, false);
        }
        static Item station(RadioStation s, boolean isArch, String archKey,
                            boolean first, boolean last) {
            return new Item(TYPE_STATION, null, s, isArch, archKey, first, last);
        }
        private Item(int t, RadioGroup g, RadioStation s, boolean arch, String aKey,
                     boolean first, boolean last) {
            type = t; group = g; station = s; isArch = arch; archKey = aKey;
            isFirst = first; isLast = last;
        }
    }

    // ── State ─────────────────────────────────────────────────────
    private final List<RadioGroup>     groups;
    private final List<Item>           flat       = new ArrayList<>();
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

    // ── Flat list ─────────────────────────────────────────────────
    private void rebuildFlat() {
        flat.clear();
        for (RadioGroup g : groups) {
            flat.add(Item.header(g));
            if (!Boolean.TRUE.equals(expandedMap.get(g.getName()))) continue;

            boolean isArch = "__ARCHIVED__".equals(g.getName());
            LinkedHashSet<String> seenUrls = new LinkedHashSet<>();
            List<RadioStation> visible = new ArrayList<>();

            for (RadioStation s : g.getStations()) {
                String url = s.getUrl().trim().toLowerCase();
                if (!seenUrls.add(url)) continue;
                if (!isArch && prefs.isArchived(
                        AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup()))) continue;
                visible.add(s);
            }

            for (int i = 0; i < visible.size(); i++) {
                RadioStation s = visible.get(i);
                String aKey = isArch
                        ? AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup()) : null;
                flat.add(Item.station(s, isArch, aKey, i == 0, i == visible.size() - 1));
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────
    public void setActiveStation(RadioStation s) { activeStation = s; notifyDataSetChanged(); }

    public void attachToRecyclerView(RecyclerView rv) {
        applySavedOrder();
        rebuildFlat();
        notifyDataSetChanged();
        // No ItemTouchHelper — drag only in category manager dialog
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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (vt == TYPE_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.item_radio_group_header, parent, false));
        } else {
            return new StationVH(inf.inflate(R.layout.item_radio_station, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int pos) {
        Item item = flat.get(pos);
        if (item.type == TYPE_HEADER) bindHeader((HeaderVH) vh, item);
        else                          bindStation((StationVH) vh, item);
    }

    // ── Header binding ────────────────────────────────────────────
    private void bindHeader(HeaderVH h, Item item) {
        RadioGroup g     = item.group;
        String     name  = g.getName();
        boolean isArch   = "__ARCHIVED__".equals(name);
        boolean expanded = Boolean.TRUE.equals(expandedMap.get(name));

        // Count visible stations
        int count = 0;
        if (!isArch) {
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (RadioStation s : g.getStations()) {
                String url = s.getUrl().trim().toLowerCase();
                if (seen.add(url) && !prefs.isArchived(
                        AppPrefs.stationKey(s.getName(), s.getUrl(), s.getGroup())))
                    count++;
            }
        } else {
            count = g.getStations().size();
        }

        h.tvLabel.setText(isArch ? "Archived" : name);
        h.tvLabel.setTextColor(expanded ? colors.radioGroupNameText()
                                        : colors.radioGroupCollapsed());

        // Badge
        if (h.tvCount != null && count > 0) {
            h.tvCount.setText(String.valueOf(count));
            h.tvCount.setVisibility(View.VISIBLE);
            float dp = density(h.itemView);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            badgeBg.setCornerRadius(10 * dp);
            badgeBg.setColor(expanded ? colors.radioGroupBadgeBg() : 0x18FFFFFF);
            h.tvCount.setBackground(badgeBg);
            h.tvCount.setTextColor(expanded ? colors.radioGroupBadgeText()
                                            : colors.radioGroupCollapsed());
        } else if (h.tvCount != null) {
            h.tvCount.setVisibility(View.GONE);
        }

        // Arrow
        if (h.tvArrow != null) {
            h.tvArrow.setRotation(expanded ? 90f : 0f);
            h.tvArrow.setTextColor(expanded ? colors.radioGroupNameText()
                                            : colors.radioGroupCollapsed());
        }

        // Header card background
        float dp = density(h.itemView);
        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setShape(GradientDrawable.RECTANGLE);
        headerBg.setCornerRadius(expanded ? 0f : 10 * dp);
        if (expanded) {
            // When expanded, top corners rounded, bottom square (stations sit below)
            headerBg.setCornerRadii(new float[]{10*dp,10*dp, 10*dp,10*dp, 0,0, 0,0});
            headerBg.setColor(colors.radioGroupHeaderBg());
            headerBg.setStroke((int)(1f*dp), colors.radioGroupHeaderBorder());
        } else {
            headerBg.setCornerRadius(10 * dp);
            headerBg.setColor(colors.bgCard2());
        }
        h.itemView.setBackground(headerBg);

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

    // ── Station binding ───────────────────────────────────────────
    @SuppressWarnings("ClickableViewAccessibility")
    private void bindStation(StationVH h, Item item) {
        RadioStation station = item.station;
        boolean isArch = item.isArch;

        h.tvName.setText(station.getName());

        // Cancel recycled EQ animators
        if (h.eq1Anim != null) { h.eq1Anim.cancel(); h.eq1Anim = null; }
        if (h.eq2Anim != null) { h.eq2Anim.cancel(); h.eq2Anim = null; }
        if (h.eq3Anim != null) { h.eq3Anim.cancel(); h.eq3Anim = null; }

        boolean active = !isArch && activeStation != null
                && activeStation.getUrl().equals(station.getUrl());

        // Card-style background — rounded only on first/last station in group
        float dp = density(h.root);
        float top  = item.isFirst ? 0f : 0f; // group card handles top radius on header
        float bot  = item.isLast  ? 10 * dp : 0f;
        GradientDrawable stationBg = new GradientDrawable();
        stationBg.setShape(GradientDrawable.RECTANGLE);
        stationBg.setCornerRadii(new float[]{0,0, 0,0, bot,bot, bot,bot});

        if (active) {
            stationBg.setColor(colors.stationActiveBg());
            stationBg.setStroke((int)(1.5f * dp), colors.stationActiveBorder());
            h.root.setBackground(stationBg);
            h.tvName.setTextColor(colors.stationTextActive());
            h.eqView.setVisibility(View.VISIBLE);
            h.eq1Anim = animEq(h.eq1, 400, 4, 14);
            h.eq2Anim = animEq(h.eq2, 600, 8, 18);
            h.eq3Anim = animEq(h.eq3, 500, 3, 12);
        } else {
            stationBg.setColor(isArch ? colors.bgCard2() : colors.radioCardBg());
            if (!isArch) stationBg.setStroke(1, colors.radioCardBorder());
            h.root.setBackground(stationBg);
            h.tvName.setTextColor(isArch ? colors.textSecondary() : colors.stationText());
            h.eqView.setVisibility(View.GONE);
        }

        if (isArch) {
            h.root.setOnClickListener(null);
            attachSwipe(h.root, station, true, item.archKey);
        } else {
            h.root.setOnClickListener(v -> {
                String url = station.getUrl();
                long now   = System.currentTimeMillis();
                Long lst   = lastTapTime.get(url);
                if (lst != null && (now - lst) < DOUBLE_TAP_MS) {
                    lastTapTime.remove(url);
                    haptic(v);
                    String key = AppPrefs.stationKey(station.getName(), url, station.getGroup());
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
                         .setDuration(380).setInterpolator(new DecelerateInterpolator(2f))
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

    private void applyClickGlow(View root, TextView tv) {
        float dp = density(root);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(colors.stationActiveBg());
        gd.setStroke((int)(2f * dp), colors.stationClickGlow());
        root.setBackground(gd);
        tv.setTextColor(colors.stationTextActive());
    }

    private ValueAnimator animEq(View bar, long dur, int minDp, int maxDp) {
        if (bar == null) return null;
        float d = bar.getContext().getResources().getDisplayMetrics().density;
        ValueAnimator a = ValueAnimator.ofInt((int)(minDp * d), (int)(maxDp * d));
        a.setDuration(dur);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.addUpdateListener(anim -> {
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.height = (int) anim.getAnimatedValue();
            bar.setLayoutParams(lp);
        });
        a.start();
        return a;
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

    private float density(View v) {
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