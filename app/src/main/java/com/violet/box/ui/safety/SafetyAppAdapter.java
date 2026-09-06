package com.violet.box.ui.safety;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.violet.box.ui.widget.KsuSwitchView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.violet.box.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 安全页应用列表适配器：应用 + 保护开关 + 最近一次操作状态。 */
public class SafetyAppAdapter extends RecyclerView.Adapter<SafetyAppAdapter.Holder> {

    public interface OnToggleListener {
        void onToggle(SafetyPageController.SafetyAppItem item, boolean enable);
    }

    private final List<SafetyPageController.SafetyAppItem> all = new ArrayList<>();
    private final List<SafetyPageController.SafetyAppItem> shown = new ArrayList<>();
    private final OnToggleListener listener;
    private String query = "";

    public SafetyAppAdapter(OnToggleListener listener) {
        this.listener = listener;
    }

    public void submit(List<SafetyPageController.SafetyAppItem> items) {
        List<SafetyPageController.SafetyAppItem> nextShown = filter(items);
        DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new ItemDiff(shown, nextShown));
        all.clear();
        all.addAll(items);
        shown.clear();
        shown.addAll(nextShown);
        changes.dispatchUpdatesTo(this);
    }

    public void setQuery(String q) {
        query = q == null ? "" : q.trim().toLowerCase(Locale.US);
        List<SafetyPageController.SafetyAppItem> nextShown = filter(all);
        DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new ItemDiff(shown, nextShown));
        shown.clear();
        shown.addAll(nextShown);
        changes.dispatchUpdatesTo(this);
    }

    /** Reverts only the row tapped while another command is still in flight. */
    public void restore(SafetyPageController.SafetyAppItem item) {
        for (int i = 0; i < shown.size(); i++) {
            if (sameIdentity(shown.get(i), item)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    private List<SafetyPageController.SafetyAppItem> filter(
            List<SafetyPageController.SafetyAppItem> source) {
        List<SafetyPageController.SafetyAppItem> result = new ArrayList<>();
        for (SafetyPageController.SafetyAppItem it : source) {
            if (query.isEmpty()
                    || it.label.toLowerCase(Locale.US).contains(query)
                    || it.pkg.toLowerCase(Locale.US).contains(query)) {
                result.add(it);
            }
        }
        return result;
    }

    private static boolean sameIdentity(SafetyPageController.SafetyAppItem oldItem,
                                        SafetyPageController.SafetyAppItem newItem) {
        return oldItem.uid == newItem.uid && oldItem.pkg.equals(newItem.pkg);
    }

    private static boolean sameContent(SafetyPageController.SafetyAppItem oldItem,
                                       SafetyPageController.SafetyAppItem newItem) {
        return oldItem.label.equals(newItem.label)
                && oldItem.sharedSummary.equals(newItem.sharedSummary)
                && oldItem.stateText.equals(newItem.stateText)
                && oldItem.icon == newItem.icon
                && oldItem.stateColor == newItem.stateColor
                && oldItem.protectionRequested == newItem.protectionRequested
                && oldItem.canToggle == newItem.canToggle;
    }

    private static final class ItemDiff extends DiffUtil.Callback {
        private final List<SafetyPageController.SafetyAppItem> oldItems;
        private final List<SafetyPageController.SafetyAppItem> newItems;

        ItemDiff(List<SafetyPageController.SafetyAppItem> oldItems,
                 List<SafetyPageController.SafetyAppItem> newItems) {
            this.oldItems = oldItems;
            this.newItems = newItems;
        }

        @Override public int getOldListSize() { return oldItems.size(); }
        @Override public int getNewListSize() { return newItems.size(); }

        @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
            return sameIdentity(oldItems.get(oldPosition), newItems.get(newPosition));
        }

        @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
            return sameContent(oldItems.get(oldPosition), newItems.get(newPosition));
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_safety_app, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        final SafetyPageController.SafetyAppItem item = shown.get(position);
        h.icon.setImageDrawable(item.icon);
        h.name.setText(item.label);
        h.pkg.setText(item.sharedSummary.isEmpty() ? item.pkg : item.sharedSummary + "\n" + item.pkg);
        h.pkg.setContentDescription(item.pkg + " " + item.sharedSummary);

        h.state.setVisibility(item.stateText.isEmpty() ? View.GONE : View.VISIBLE);
        h.state.setText(item.stateText);
        h.state.setTextColor(item.stateColor);
        // KernelSU StatusTag 风格：文字色低透明度作为 tonal 底色
        h.state.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(item.stateColor, 0x24)));

        h.switcher.setChecked(item.protectionRequested);
        h.switcher.setEnabled(item.canToggle);
        h.switcher.setOnCheckedChange(isChecked -> {
            if (listener != null) {
                listener.onToggle(item, isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView pkg;
        final TextView state;
        final KsuSwitchView switcher;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.ivSafetyAppIcon);
            name = itemView.findViewById(R.id.tvSafetyAppName);
            pkg = itemView.findViewById(R.id.tvSafetyAppPkg);
            state = itemView.findViewById(R.id.tvSafetyAppState);
            switcher = itemView.findViewById(R.id.swSafetyProtect);
        }
    }
}
