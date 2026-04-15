package com.violet.safe.fragment;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.violet.safe.R;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;

public class DeviceFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        TextView tvDeviceName = view.findViewById(R.id.tvDeviceName);
        TextView tvDeviceCode = view.findViewById(R.id.tvDeviceCode);
        TextView tvAndroidVersion = view.findViewById(R.id.tvAndroidVersion);
        TextView tvKernelVersion = view.findViewById(R.id.tvKernelVersion);
        TextView tvSelinuxStatus = view.findViewById(R.id.tvSelinuxStatus);
        TextView tvBootSlot = view.findViewById(R.id.tvBootSlot);

        // 设备名称
        String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
        tvDeviceName.setText("设备名称: " + deviceName);

        // 设备代号
        String deviceCode = Build.DEVICE;
        tvDeviceCode.setText("设备代号: " + deviceCode);

        // 安卓版本
        String androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        tvAndroidVersion.setText("安卓版本: Android " + androidVersion);

        // 内核版本
        String kernelVersion = System.getProperty("os.version");
        if (kernelVersion == null) kernelVersion = "未知";
        tvKernelVersion.setText("内核版本: " + kernelVersion);

        // SELinux 状态
        String selinuxStatus = getSELinuxStatus();
        tvSelinuxStatus.setText("SELinux状态: " + selinuxStatus);

        // 启动槽位
        String bootSlot = getBootSlot();
        tvBootSlot.setText("启动槽位: " + bootSlot);
    }

    private String getSELinuxStatus() {
        try {
            Class<?> selinuxClass = Class.forName("android.os.SELinux");
            Method isSELinuxEnabled = selinuxClass.getMethod("isSELinuxEnabled");
            Method isSELinuxEnforced = selinuxClass.getMethod("isSELinuxEnforced");
            
            Boolean enabled = (Boolean) isSELinuxEnabled.invoke(null);
            if (enabled != null && !enabled) {
                return "关闭状态";
            }
            
            Boolean enforced = (Boolean) isSELinuxEnforced.invoke(null);
            if (enforced != null) {
                return enforced ? "强制模式 (Enforcing)" : "宽容模式 (Permissive)";
            }
        } catch (Exception e) {
            // Fallback
        }
        
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/sys/fs/selinux/enforce"));
            String line = reader.readLine();
            reader.close();
            if ("1".equals(line)) return "强制模式 (Enforcing)";
            if ("0".equals(line)) return "宽容模式 (Permissive)";
        } catch (Exception e) {
            // Ignore
        }
        
        return "未知";
    }

    private String getBootSlot() {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            
            String slotSuffix = (String) get.invoke(null, "ro.boot.slot_suffix", "");
            if (slotSuffix != null && !slotSuffix.isEmpty()) {
                if (slotSuffix.equalsIgnoreCase("_a") || slotSuffix.equalsIgnoreCase("a")) return "A 槽位";
                if (slotSuffix.equalsIgnoreCase("_b") || slotSuffix.equalsIgnoreCase("b")) return "B 槽位";
                return slotSuffix;
            }
            
            String slot = (String) get.invoke(null, "ro.boot.slot", "");
            if (slot != null && !slot.isEmpty()) {
                if (slot.equalsIgnoreCase("a") || slot.equalsIgnoreCase("_a")) return "A 槽位";
                if (slot.equalsIgnoreCase("b") || slot.equalsIgnoreCase("_b")) return "B 槽位";
                return slot;
            }
            
            return "未启用 A/B 分区";
        } catch (Exception e) {
            return "未知";
        }
    }
}
