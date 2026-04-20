package com.violet.safe.ui.plugin;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.violet.safe.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VioletPluginActivity extends AppCompatActivity {

    private static final String TRICKY_STORE_DIR = "/data/adb/tricky_store";
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private View cardTrickyStoreModule;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_violet_plugin);

        Toolbar toolbar = findViewById(R.id.toolbarVioletPlugin);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("紫罗兰插件");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        cardTrickyStoreModule = findViewById(R.id.cardTrickyStoreModule);
        detectTrickyStoreModule();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.tricky_store_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == R.id.action_open_hide_bl_list) {
            startActivity(new Intent(this, TrickyStoreAppListActivity.class));
            return true;
        }
        if (id == R.id.action_set_security_patch) {
            startActivity(new Intent(this, TrickyStoreSecurityPatchActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void detectTrickyStoreModule() {
        ioExecutor.execute(() -> {
            boolean installed = isDirectoryExistsViaSu(TRICKY_STORE_DIR);
            runOnUiThread(() -> updateModuleUi(installed));
        });
    }

    private void updateModuleUi(boolean installed) {
        if (installed) {
            cardTrickyStoreModule.setVisibility(View.VISIBLE);
            cardTrickyStoreModule.setOnClickListener(v ->
                    startActivity(new Intent(this, TrickyStoreAppListActivity.class)));
            return;
        }
        cardTrickyStoreModule.setVisibility(View.GONE);
        cardTrickyStoreModule.setOnClickListener(null);
    }

    private boolean isDirectoryExistsViaSu(String directoryPath) {
        String[] suBins = new String[]{"su", "/system/bin/su", "/system/xbin/su"};
        for (String suBin : suBins) {
            Process process = null;
            try {
                process = new ProcessBuilder(suBin, "-c", "[ -d \"" + directoryPath + "\" ]")
                        .redirectErrorStream(true)
                        .start();
                boolean finished = process.waitFor(1200, TimeUnit.MILLISECONDS);
                if (finished && process.exitValue() == 0) {
                    return true;
                }
            } catch (Exception ignored) {
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
        return false;
    }

}
