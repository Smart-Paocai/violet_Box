package com.violet.safe.ui.plugin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.violet.safe.R;

public class TrickyStoreConfigActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tricky_store_config);

        Toolbar toolbar = findViewById(R.id.toolbarTrickyStoreConfig);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Tricky Store");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        bindCard(R.id.cardTrickyStoreHideBl, TrickyStoreAppListActivity.class);
        bindCard(R.id.cardTrickyStoreSecurityPatch, TrickyStoreSecurityPatchActivity.class);
        bindCard(R.id.cardTrickyStoreHash, TrickyStoreHashActivity.class);
    }

    private void bindCard(int viewId, Class<?> targetActivity) {
        View card = findViewById(viewId);
        if (card != null) {
            card.setOnClickListener(v -> startActivity(new Intent(this, targetActivity)));
        }
    }
}
