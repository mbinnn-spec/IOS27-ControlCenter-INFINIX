package com.ios27.controlcenter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.ios27.controlcenter.prefs.ModulePrefs;

import java.io.DataOutputStream;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences mPrefs;
    private boolean mWorldReadable = false;

    private MaterialSwitch swEnabled;
    private RadioGroup rgLayoutMode;
    private RadioButton rbHybrid, rbPills, rbCircles;
    private SeekBar sbGlassOpacity, sbRimIntensity, sbBgDim;
    private TextView tvGlassOpacityVal, tvRimIntensityVal, tvBgDimVal;
    private MaterialSwitch swAmbientGlow, swIosHeader, swIosSliders, swIosMedia, swSubtitles, swHideIot, swIosAnimations;

    /**
     * Hooked by HookEntry when this module is active in LSPosed.
     */
    public static boolean isModuleActive() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = openModulePrefs();
        bindViews();
        loadStateToUi();
        attachListeners();
    }

    @SuppressLint("WorldReadableFiles")
    @SuppressWarnings("deprecation")
    private SharedPreferences openModulePrefs() {
        try {
            SharedPreferences prefs = getSharedPreferences(ModulePrefs.PREFS_NAME, Context.MODE_WORLD_READABLE);
            mWorldReadable = true;
            return prefs;
        } catch (SecurityException ignored) {
            mWorldReadable = false;
            return getSharedPreferences(ModulePrefs.PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    private void bindViews() {
        TextView tvStatus = findViewById(R.id.tv_module_status);
        if (isModuleActive()) {
            tvStatus.setText("LSPosed: Active on XOS 15");
            tvStatus.setTextColor(getColor(R.color.accent_green));
            tvStatus.setBackgroundResource(R.drawable.bg_status_active);
        } else {
            tvStatus.setText("LSPosed: Not Active — Enable in LSPosed Manager");
            tvStatus.setTextColor(getColor(R.color.accent_amber));
            tvStatus.setBackgroundResource(R.drawable.bg_status_inactive);
        }

        swEnabled = findViewById(R.id.sw_enabled);
        rgLayoutMode = findViewById(R.id.rg_layout_mode);
        rbHybrid = findViewById(R.id.rb_layout_hybrid);
        rbPills = findViewById(R.id.rb_layout_pills);
        rbCircles = findViewById(R.id.rb_layout_circles);

        sbGlassOpacity = findViewById(R.id.sb_glass_opacity);
        sbRimIntensity = findViewById(R.id.sb_rim_intensity);
        sbBgDim = findViewById(R.id.sb_bg_dim);

        tvGlassOpacityVal = findViewById(R.id.tv_glass_opacity_val);
        tvRimIntensityVal = findViewById(R.id.tv_rim_intensity_val);
        tvBgDimVal = findViewById(R.id.tv_bg_dim_val);

        swAmbientGlow = findViewById(R.id.sw_ambient_glow);
        swIosHeader = findViewById(R.id.sw_ios_header);
        swIosSliders = findViewById(R.id.sw_ios_sliders);
        swIosMedia = findViewById(R.id.sw_ios_media);
        swSubtitles = findViewById(R.id.sw_subtitles);
        swHideIot = findViewById(R.id.sw_hide_iot);
        swIosAnimations = findViewById(R.id.sw_ios_animations);
    }

    private void loadStateToUi() {
        swEnabled.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_ENABLED, true));

        int mode = mPrefs.getInt(ModulePrefs.KEY_LAYOUT_MODE, ModulePrefs.LAYOUT_HYBRID);
        if (mode == ModulePrefs.LAYOUT_PILLS) {
            rbPills.setChecked(true);
        } else if (mode == ModulePrefs.LAYOUT_CIRCLES) {
            rbCircles.setChecked(true);
        } else {
            rbHybrid.setChecked(true);
        }

        int opacity = mPrefs.getInt(ModulePrefs.KEY_GLASS_OPACITY, 46);
        sbGlassOpacity.setProgress(Math.max(0, opacity - 10));
        tvGlassOpacityVal.setText(opacity + "%");

        int rim = mPrefs.getInt(ModulePrefs.KEY_RIM_INTENSITY, 85);
        sbRimIntensity.setProgress(rim);
        tvRimIntensityVal.setText(rim + "%");

        int dim = mPrefs.getInt(ModulePrefs.KEY_BG_DIM_ALPHA, 18);
        sbBgDim.setProgress(dim);
        tvBgDimVal.setText(dim + "%");

        swAmbientGlow.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_AMBIENT_GLOW, true));
        swIosHeader.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_IOS_HEADER, true));
        swIosSliders.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_IOS_SLIDERS, true));
        swIosMedia.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_IOS_MEDIA, true));
        swSubtitles.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_SHOW_STATUS_SUBTITLE, true));
        swHideIot.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_HIDE_IOT_CARD, true));
        swIosAnimations.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_IOS_ANIMATIONS, true));
    }

    private void attachListeners() {
        swEnabled.setOnCheckedChangeListener((b, checked) ->
                mPrefs.edit().putBoolean(ModulePrefs.KEY_ENABLED, checked).apply());

        rgLayoutMode.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = ModulePrefs.LAYOUT_HYBRID;
            if (checkedId == R.id.rb_layout_pills) {
                mode = ModulePrefs.LAYOUT_PILLS;
            } else if (checkedId == R.id.rb_layout_circles) {
                mode = ModulePrefs.LAYOUT_CIRCLES;
            }
            mPrefs.edit().putInt(ModulePrefs.KEY_LAYOUT_MODE, mode).apply();
        });

        sbGlassOpacity.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress + 10;
                tvGlassOpacityVal.setText(val + "%");
                if (fromUser) {
                    mPrefs.edit().putInt(ModulePrefs.KEY_GLASS_OPACITY, val).apply();
                }
            }
        });

        sbRimIntensity.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvRimIntensityVal.setText(progress + "%");
                if (fromUser) {
                    mPrefs.edit().putInt(ModulePrefs.KEY_RIM_INTENSITY, progress).apply();
                }
            }
        });

        sbBgDim.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvBgDimVal.setText(progress + "%");
                if (fromUser) {
                    mPrefs.edit().putInt(ModulePrefs.KEY_BG_DIM_ALPHA, progress).apply();
                }
            }
        });

        swAmbientGlow.setOnCheckedChangeListener((b, c) ->
                mPrefs.edit().putBoolean(ModulePrefs.KEY_AMBIENT_GLOW, c).apply());
        swIosHeader.setOnCheckedChangeListener((b, c) ->
                mPrefs.edit().putBoolean(ModulePrefs.KEY_IOS_HEADER, c).apply());
        swIosSliders.setOnCheckedChangeListener((b, c) ->
                mPrefs.edit().putBoolean(ModulePrefs.KEY_IOS_SLIDERS, c).apply());
        swIosMedia.setOnCheckedChangeListener((b, c) ->
                mPrefs.edit().putBoolean(ModulePrefs.KEY_IOS_MEDIA, c).apply());
        swSubtitles.setOnCheckedChangeListener((b, c) ->
                mPrefs.edit().putBoolean(ModulePrefs.KEY_SHOW_STATUS_SUBTITLE, c).apply());
        swHideIot.setOnCheckedChangeListener((b, c) ->
                mPrefs.edit().putBoolean(ModulePrefs.KEY_HIDE_IOT_CARD, c).apply());
        swIosAnimations.setOnCheckedChangeListener((b, c) ->
                mPrefs.edit().putBoolean(ModulePrefs.KEY_IOS_ANIMATIONS, c).apply());

        Button btnRestart = findViewById(R.id.btn_restart_systemui);
        btnRestart.setOnClickListener(v -> restartSystemUi());

        Button btnReset = findViewById(R.id.btn_reset_defaults);
        btnReset.setOnClickListener(v -> {
            mPrefs.edit()
                    .putBoolean(ModulePrefs.KEY_ENABLED, true)
                    .putInt(ModulePrefs.KEY_LAYOUT_MODE, ModulePrefs.LAYOUT_HYBRID)
                    .putInt(ModulePrefs.KEY_GLASS_OPACITY, 46)
                    .putInt(ModulePrefs.KEY_RIM_INTENSITY, 85)
                    .putInt(ModulePrefs.KEY_BG_DIM_ALPHA, 18)
                    .putBoolean(ModulePrefs.KEY_AMBIENT_GLOW, true)
                    .putBoolean(ModulePrefs.KEY_IOS_HEADER, true)
                    .putBoolean(ModulePrefs.KEY_IOS_SLIDERS, true)
                    .putBoolean(ModulePrefs.KEY_IOS_MEDIA, true)
                    .putBoolean(ModulePrefs.KEY_SHOW_STATUS_SUBTITLE, true)
                    .putBoolean(ModulePrefs.KEY_HIDE_IOT_CARD, true)
                    .putBoolean(ModulePrefs.KEY_IOS_ANIMATIONS, true)
                    .apply();
            loadStateToUi();
            Toast.makeText(this, "Defaults restored", Toast.LENGTH_SHORT).show();
        });

        if (!mWorldReadable && isModuleActive()) {
            Toast.makeText(this, "Reboot once if settings do not sync to SystemUI", Toast.LENGTH_LONG).show();
        }
    }

    private void restartSystemUi() {
        new Thread(() -> {
            boolean ok = false;
            try {
                Process p = Runtime.getRuntime().exec("su");
                try (DataOutputStream os = new DataOutputStream(p.getOutputStream())) {
                    os.writeBytes("pkill -f com.android.systemui\n");
                    os.writeBytes("exit\n");
                    os.flush();
                }
                ok = (p.waitFor() == 0);
            } catch (Throwable ignored) {
            }
            boolean finalOk = ok;
            runOnUiThread(() -> Toast.makeText(
                    MainActivity.this,
                    finalOk ? "Restarting SystemUI..." : "Root permission denied",
                    Toast.LENGTH_SHORT
            ).show());
        }).start();
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}

