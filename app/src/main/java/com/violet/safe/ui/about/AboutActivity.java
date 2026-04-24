package com.violet.safe.ui.about;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.violet.safe.R;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("关于");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        } else {
            setTitle("关于");
        }

        findViewById(R.id.iconAboutLink1).setOnClickListener(v ->
                openUrl("https://space.bilibili.com/601533208", "无法打开哔哩哔哩链接"));
        findViewById(R.id.iconAboutLink2).setOnClickListener(v ->
                openUrl("https://www.coolapk.com/u/33872028", "无法打开酷安链接"));
        findViewById(R.id.iconAboutLink3).setOnClickListener(v ->
                openUrl("https://github.com/Smart-Paocai", "无法打开 GitHub 链接"));
        findViewById(R.id.iconAboutLink4).setOnClickListener(v ->
                openUrl("https://www.douyin.com/user/MS4wLjABAAAAPV71g_7n4JA3-HniyfV2_AjLoK_nwJe_0dtaBA-KZZVJjrkHqId2xmdzhxtfJ3ta", "无法打开抖音链接"));

        TextView tvAboutCurrentVersion = findViewById(R.id.tvAboutCurrentVersion);
        if (tvAboutCurrentVersion != null) {
            tvAboutCurrentVersion.setText(getAppVersionName());
        }

    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void openUrl(String url, String failMessage) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, failMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private String getAppVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "未知版本";
        }
    }

}
