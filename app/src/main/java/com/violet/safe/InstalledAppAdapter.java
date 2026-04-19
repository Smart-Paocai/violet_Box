package com.violet.safe;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.violet.safe.model.InstalledAppRow;

import java.util.ArrayList;
import java.util.List;

public final class InstalledAppAdapter extends RecyclerView.Adapter<InstalledAppAdapter.Holder> {

    public interface ActionListener {
        void onOpenActions(@NonNull InstalledAppRow row);
    }

    private final PackageManager pm;
    private final ActionListener actionListener;
    private final List<InstalledAppRow> items = new ArrayList<>();

    public InstalledAppAdapter(PackageManager pm, ActionListener actionListener) {
        this.pm = pm;
        this.actionListener = actionListener;
    }

    public void submitList(List<InstalledAppRow> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_installed_app, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        InstalledAppRow row = items.get(position);
        h.tvLabel.setText(row.label);
        h.tvPackage.setText(row.appInfo.packageName);
        Drawable icon = null;
        try {
            icon = pm.getApplicationIcon(row.appInfo);
        } catch (Exception ignored) {
        }
        if (icon != null) {
            h.ivIcon.setImageDrawable(icon);
        } else {
            h.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        h.btnActions.setOnClickListener(v -> actionListener.onOpenActions(row));
        h.itemView.setOnClickListener(v -> actionListener.onOpenActions(row));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvLabel;
        final TextView tvPackage;
        final ImageButton btnActions;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivAppIcon);
            tvLabel = itemView.findViewById(R.id.tvAppLabel);
            tvPackage = itemView.findViewById(R.id.tvAppPackage);
            btnActions = itemView.findViewById(R.id.btnAppActions);
        }
    }
}
