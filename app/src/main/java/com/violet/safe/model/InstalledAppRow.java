package com.violet.safe.model;

import android.content.pm.ApplicationInfo;

/** 应用管理列表一行：展示名、包名、图标由 {@link ApplicationInfo} 现场加载。 */
public final class InstalledAppRow {

    public final ApplicationInfo appInfo;
    public final String label;
    public final boolean system;

    public InstalledAppRow(ApplicationInfo appInfo, String label, boolean system) {
        this.appInfo = appInfo;
        this.label = label;
        this.system = system;
    }
}
