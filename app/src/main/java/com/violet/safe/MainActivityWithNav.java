package com.violet.safe;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.violet.safe.fragment.AboutFragment;
import com.violet.safe.fragment.DetectFragment;
import com.violet.safe.fragment.DeviceFragment;

public class MainActivityWithNav extends AppCompatActivity {

    private LinearLayout navHome, navDevice, navExplore, navSettings;
    private ImageView iconHome, iconDevice, iconExplore, iconSettings;
    private TextView textHome, textDevice, textExplore, textSettings;
    
    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_with_nav);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 初始化导航栏
        navHome = findViewById(R.id.nav_home);
        navDevice = findViewById(R.id.nav_device);
        navExplore = findViewById(R.id.nav_explore);
        navSettings = findViewById(R.id.nav_settings);
        
        iconHome = findViewById(R.id.nav_home_icon);
        iconDevice = findViewById(R.id.nav_device_icon);
        iconExplore = findViewById(R.id.nav_explore_icon);
        iconSettings = findViewById(R.id.nav_settings_icon);
        
        textHome = findViewById(R.id.nav_home_text);
        textDevice = findViewById(R.id.nav_device_text);
        textExplore = findViewById(R.id.nav_explore_text);
        textSettings = findViewById(R.id.nav_settings_text);

        // 设置点击事件
        navHome.setOnClickListener(v -> selectTab(0));
        navDevice.setOnClickListener(v -> selectTab(1));
        navExplore.setOnClickListener(v -> selectTab(2));
        navSettings.setOnClickListener(v -> selectTab(3));

        // 默认显示首页页面
        selectTab(0);
    }

    private void selectTab(int tab) {
        if (currentTab == tab) return;
        currentTab = tab;

        // 重置所有标签
        resetAllTabs();

        // 设置选中的标签
        Fragment fragment = null;
        String title = "";
        
        switch (tab) {
            case 0:
                iconHome.setColorFilter(ContextCompat.getColor(this, R.color.purple_700));
                textHome.setTextColor(ContextCompat.getColor(this, R.color.purple_700));
                navHome.setBackgroundResource(R.drawable.nav_item_selected);
                fragment = new DetectFragment(); // reusing DetectFragment as Home for now
                title = "首页";
                break;
            case 1:
                iconDevice.setColorFilter(ContextCompat.getColor(this, R.color.purple_700));
                textDevice.setTextColor(ContextCompat.getColor(this, R.color.purple_700));
                navDevice.setBackgroundResource(R.drawable.nav_item_selected);
                fragment = new DeviceFragment();
                title = "设备信息";
                break;
            case 2:
                iconExplore.setColorFilter(ContextCompat.getColor(this, R.color.purple_700));
                textExplore.setTextColor(ContextCompat.getColor(this, R.color.purple_700));
                navExplore.setBackgroundResource(R.drawable.nav_item_selected);
                fragment = new Fragment(); // Create an empty fragment for now if ExploreFragment doesn't exist
                title = "玩机";
                break;
            case 3:
                iconSettings.setColorFilter(ContextCompat.getColor(this, R.color.purple_700));
                textSettings.setTextColor(ContextCompat.getColor(this, R.color.purple_700));
                navSettings.setBackgroundResource(R.drawable.nav_item_selected);
                fragment = new AboutFragment(); // reusing AboutFragment for Settings for now
                title = "设置";
                break;
        }

        // 更新标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }

        // 切换Fragment
        if (fragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            );
            transaction.replace(R.id.fragment_container, fragment);
            transaction.commit();
        }
    }

    private void resetAllTabs() {
        int grayColor = ContextCompat.getColor(this, R.color.ios_text_secondary);
        
        iconHome.setColorFilter(grayColor);
        iconDevice.setColorFilter(grayColor);
        iconExplore.setColorFilter(grayColor);
        iconSettings.setColorFilter(grayColor);
        
        textHome.setTextColor(grayColor);
        textDevice.setTextColor(grayColor);
        textExplore.setTextColor(grayColor);
        textSettings.setTextColor(grayColor);
        
        navHome.setBackgroundResource(android.R.color.transparent);
        navDevice.setBackgroundResource(android.R.color.transparent);
        navExplore.setBackgroundResource(android.R.color.transparent);
        navSettings.setBackgroundResource(android.R.color.transparent);
    }
}
