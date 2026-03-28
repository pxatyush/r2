package is.dyino.ui.radio;

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

public class RadioGroupAdapter extends RecyclerView.Adapter<RadioGroupAdapter.VH> {

    public interface StationClickListener {
        void onStationClicked(RadioStation station);
    }

    private final List<RadioGroup> groups;
    private final StationClickListener listener;
    private RadioStation activeStation;

    public RadioGroupAdapter(List<RadioGroup> groups, StationClickListener listener) {
        this.groups = groups;
        this.listener = listener;
    }

    public void setActiveStation(RadioStation station) {
        this.activeStation = station;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_radio_group, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        RadioGroup group = groups.get(position);
        holder.tvGroupName.setText(group.getName());
        holder.stationsContainer.removeAllViews();

        for (RadioStation station : group.getStations()) {
            View stView = LayoutInflater.from(holder.itemView.getContext())
                    .inflate(R.layout.item_radio_station, holder.stationsContainer, false);

            TextView tvName = stView.findViewById(R.id.tvStationName);
            View eqView = stView.findViewById(R.id.equalizerView);
            View root = stView.findViewById(R.id.stationRoot);

            tvName.setText(station.getName());

            boolean isActive = activeStation != null
                    && activeStation.getUrl().equals(station.getUrl());

            root.setBackgroundResource(isActive
                    ? R.drawable.bg_radio_item_active
                    : R.drawable.bg_radio_item);
            eqView.setVisibility(isActive ? View.VISIBLE : View.GONE);

            if (isActive) {
                tvName.setTextColor(0xFF6C63FF);
                startEqualizerAnim(stView);
            } else {
                tvName.setTextColor(0xFFFFFFFF);
            }

            stView.setOnClickListener(v -> {
                activeStation = station;
                notifyDataSetChanged();
                listener.onStationClicked(station);
            });

            holder.stationsContainer.addView(stView);
        }
    }

    private void startEqualizerAnim(View parent) {
        View eq1 = parent.findViewById(R.id.eq1);
        View eq2 = parent.findViewById(R.id.eq2);
        View eq3 = parent.findViewById(R.id.eq3);
        if (eq1 == null) return;

        animEqBar(eq1, 400, 6, 16);
        animEqBar(eq2, 600, 10, 20);
        animEqBar(eq3, 500, 4, 14);
    }

    private void animEqBar(View bar, long duration, int minDp, int maxDp) {
        float density = bar.getContext().getResources().getDisplayMetrics().density;
        int minPx = (int) (minDp * density);
        int maxPx = (int) (maxDp * density);

        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(minPx, maxPx);
        anim.setDuration(duration);
        anim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        anim.addUpdateListener(a -> {
            int h = (int) a.getAnimatedValue();
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.height = h;
            bar.setLayoutParams(lp);
        });
        anim.start();
    }

    @Override
    public int getItemCount() { return groups.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvGroupName;
        LinearLayout stationsContainer;

        VH(View itemView) {
            super(itemView);
            tvGroupName = itemView.findViewById(R.id.tvGroupName);
            stationsContainer = itemView.findViewById(R.id.stationsContainer);
        }
    }
}
