package com.violet.safe;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class VioletApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
