package com.violet.safe.ui.detect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.scottyab.rootbeer.RootBeer;
import com.violet.safe.R;

public class DetectFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detect, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        TextView tvRootStatus = view.findViewById(R.id.tvRootStatus);
        LinearLayout layoutDetectionDetails = view.findViewById(R.id.layoutDetectionDetails);
        
        // 执行Root检测
        RootBeer rootBeer = new RootBeer(requireContext());
        boolean isRooted = rootBeer.isRooted();
        
        if (isRooted) {
            tvRootStatus.setText("⚠️ 检测到 Root 痕迹");
            tvRootStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_semantic_negative));
        } else {
            tvRootStatus.setText("✅ 设备安全（未检测到 Root）");
            tvRootStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_semantic_positive));
        }
        
        // 添加详细检测项
        addDetectionItem(layoutDetectionDetails, "测试签名", rootBeer.detectTestKeys());
        addDetectionItem(layoutDetectionDetails, "Root管理应用", rootBeer.detectRootManagementApps());
        addDetectionItem(layoutDetectionDetails, "Root隐藏应用", rootBeer.detectRootCloakingApps());
        addDetectionItem(layoutDetectionDetails, "危险应用", rootBeer.detectPotentiallyDangerousApps());
        addDetectionItem(layoutDetectionDetails, "SU二进制文件", rootBeer.checkForSuBinary());
        addDetectionItem(layoutDetectionDetails, "Magisk", rootBeer.checkForMagiskBinary());
        addDetectionItem(layoutDetectionDetails, "BusyBox", rootBeer.checkForBusyBoxBinary());
    }
    
    private void addDetectionItem(LinearLayout parent, String label, boolean detected) {
        TextView tv = new TextView(requireContext());
        tv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        tv.setPadding(0, 8, 0, 8);
        tv.setTextSize(14);
        
        if (detected) {
            tv.setText("⚠️ " + label + ": 发现异常");
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_semantic_negative));
        } else {
            tv.setText("✅ " + label + ": 正常");
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.ios_semantic_positive));
        }
        
        parent.addView(tv);
    }
}
