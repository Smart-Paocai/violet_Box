package com.violet.box.ui.plugin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.violet.box.R;

public class SusfsConfigActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_susfs_config);

        Toolbar toolbar = findViewById(R.id.toolbarSusfsConfig);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("SuSFS配置");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        View cardKernelDisguise = findViewById(R.id.cardKernelDisguise);
        if (cardKernelDisguise != null) {
            cardKernelDisguise.setOnClickListener(v ->
                    startActivity(new Intent(this, KernelDisguiseActivity.class)));
        }
    }
}
