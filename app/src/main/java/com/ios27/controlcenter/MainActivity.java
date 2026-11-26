package com.ios27.controlcenter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ios27.controlcenter.prefs.ModulePrefs;
import com.ios27.controlcenter.ui.NbBrutalDrawable;
import com.ios27.controlcenter.ui.NbSeekBar;
import com.ios27.controlcenter.ui.NbToggleView;

import java.io.DataOutputStream;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences mPrefs;
    private XposedService mService;
    private boolean mSyncingUi = false;
    private int mSelectedLayoutMode = ModulePrefs.LAYOUT_HYBRID;

    private ScrollView scrollView;
    private View sectionAppearance, sectionModules;
    private View layoutStatusPill;
    private NbBrutalDrawable statusPillBg;
    private ImageView ivStatusDot;
    private TextView tvStatus;

    private NbToggleView swEnabled;
    private View optHybrid, optPills, optCircles;
    private NbBrutalDrawable optHybridBg, optPillsBg, optCirclesBg;
    private ImageView ivRadioHybrid, ivRadioPills, ivRadioCircles;
    private TextView tvRadioHybrid, tvRadioPills, tvRadioCircles;

    private NbSeekBar sbGlassOpacity, sbRimIntensity, sbBgDim;
    private TextView tvGlassOpacityVal, tvRimIntensityVal, tvBgDimVal;
    private NbToggleView swAmbientGlow, swIosHeader, swIosSliders, swIosMedia, swSubtitles, swHideIot, swIosAnimations;

    private Typeface tfBold;
    private Typeface tfMedium;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tfBold = ResourcesCompat.getFont(this, R.font.jakarta_bold);
        tfMedium = ResourcesCompat.getFont(this, R.font.jakarta_medium);

        mPrefs = getSharedPreferences(ModulePrefs.PREFS_NAME, Context.MODE_PRIVATE);
        bindViews();
        applySystemBarInsets();
        applyNeoBrutalistStyling();
        loadStateToUi();
        attachListeners();
        registerXposedService();
    }

    private void applySystemBarInsets() {
        final View contentContainer = findViewById(R.id.main_content_container);
        final float d = getResources().getDisplayMetrics().density;
        final int padH = Math.round(18f * d);
        final int padTop = Math.round(10f * d);
        final int padBottom = Math.round(24f * d);

        ViewCompat.setOnApplyWindowInsetsListener(contentContainer, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(
                    padH + bars.left,
                    padTop + bars.top,
                    padH + bars.right,
                    padBottom + bars.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(contentContainer);
    }

    private void registerXposedService() {
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(@NonNull XposedService service) {
                mService = service;
                try {
                    SharedPreferences remote = service.getRemotePreferences(ModulePrefs.PREFS_NAME);
                    if (remote != null) {
                        mPrefs = remote;
                    }
                } catch (Throwable ignored) {
                }
                runOnUiThread(() -> {
                    String fw = service.getFrameworkName() + " " + service.getFrameworkVersion();
                    tvStatus.setText("LSPosed: Active • " + fw);
                    ivStatusDot.setImageResource(R.drawable.ic_nb_status_dot);
                    statusPillBg.setFillColor(ContextCompat.getColor(MainActivity.this, R.color.nb_green_soft));
                    loadStateToUi();
                });
            }

            @Override
            public void onServiceDied(@NonNull XposedService service) {
                mService = null;
                runOnUiThread(() -> {
                    tvStatus.setText("LSPosed: Service Disconnected");
                    ivStatusDot.setImageResource(R.drawable.ic_nb_status_dot_inactive);
                    statusPillBg.setFillColor(ContextCompat.getColor(MainActivity.this, R.color.nb_amber_soft));
                });
            }
        });
    }

    private void bindViews() {
        scrollView = findViewById(R.id.main_scroll_view);
        sectionAppearance = findViewById(R.id.section_appearance);
        sectionModules = findViewById(R.id.section_modules);

        layoutStatusPill = findViewById(R.id.layout_status_pill);
        ivStatusDot = findViewById(R.id.iv_status_dot);
        tvStatus = findViewById(R.id.tv_module_status);

        swEnabled = findViewById(R.id.sw_enabled);

        optHybrid = findViewById(R.id.opt_layout_hybrid);
        optPills = findViewById(R.id.opt_layout_pills);
        optCircles = findViewById(R.id.opt_layout_circles);
        ivRadioHybrid = findViewById(R.id.iv_radio_hybrid);
        ivRadioPills = findViewById(R.id.iv_radio_pills);
        ivRadioCircles = findViewById(R.id.iv_radio_circles);
        tvRadioHybrid = findViewById(R.id.tv_radio_hybrid);
        tvRadioPills = findViewById(R.id.tv_radio_pills);
        tvRadioCircles = findViewById(R.id.tv_radio_circles);

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

    private void applyNeoBrutalistStyling() {
        int white = ContextCompat.getColor(this, R.color.nb_surface);
        int yellow = ContextCompat.getColor(this, R.color.nb_yellow);
        int butter = ContextCompat.getColor(this, R.color.nb_butter);
        int purple = ContextCompat.getColor(this, R.color.nb_purple);
        int green = ContextCompat.getColor(this, R.color.nb_green);
        int pink = ContextCompat.getColor(this, R.color.nb_pink);
        int amberSoft = ContextCompat.getColor(this, R.color.nb_amber_soft);

        // Top utility icon buttons
        NbBrutalDrawable.apply(findViewById(R.id.btn_top_sliders), white, 10f, 2f, 2f, true);
        NbBrutalDrawable.apply(findViewById(R.id.btn_top_layers), white, 10f, 2f, 2f, true);
        NbBrutalDrawable.apply(findViewById(R.id.btn_top_refresh), white, 10f, 2f, 2f, true);

        // Status pill
        statusPillBg = NbBrutalDrawable.applyPill(layoutStatusPill, amberSoft, 2f, 2f, false);

        // Section cards (with 4dp hard drop shadow)
        NbBrutalDrawable.apply(findViewById(R.id.card_master), white, 16f, 2f, 4f, false);
        NbBrutalDrawable.apply(findViewById(R.id.card_grid_style), white, 16f, 2f, 4f, false);
        NbBrutalDrawable.apply(findViewById(R.id.card_appearance), white, 16f, 2f, 4f, false);
        NbBrutalDrawable.apply(findViewById(R.id.card_modules), white, 16f, 2f, 4f, false);

        // Section header icon badges
        NbBrutalDrawable.apply(findViewById(R.id.badge_master), yellow, 10f, 2f, 0f, false);
        NbBrutalDrawable.apply(findViewById(R.id.badge_grid), purple, 8f, 1.5f, 0f, false);
        NbBrutalDrawable.apply(findViewById(R.id.badge_sparkles), green, 8f, 1.5f, 0f, false);
        NbBrutalDrawable.apply(findViewById(R.id.badge_layers), pink, 8f, 1.5f, 0f, false);

        // Radio options (reserve 2dp shadow box consistently so selection never shifts layout)
        optHybridBg = NbBrutalDrawable.apply(optHybrid, white, 10f, 2f, 0f, 2f, false);
        optPillsBg = NbBrutalDrawable.apply(optPills, white, 10f, 2f, 0f, 2f, false);
        optCirclesBg = NbBrutalDrawable.apply(optCircles, white, 10f, 2f, 0f, 2f, false);

        // Value badges & slider fills
        NbBrutalDrawable.apply(tvGlassOpacityVal, butter, 6f, 1.5f, 0f, false);
        NbBrutalDrawable.apply(tvRimIntensityVal, butter, 6f, 1.5f, 0f, false);
        NbBrutalDrawable.apply(tvBgDimVal, butter, 6f, 1.5f, 0f, false);

        sbGlassOpacity.setRange(10, 95);
        sbGlassOpacity.setFillColor(green);

        sbRimIntensity.setRange(0, 100);
        sbRimIntensity.setFillColor(yellow);

        sbBgDim.setRange(0, 80);
        sbBgDim.setFillColor(purple);

        // Module toggle rows
        int[] rowIds = {
                R.id.row_ambient_glow,
                R.id.row_ios_header,
                R.id.row_ios_sliders,
                R.id.row_ios_media,
                R.id.row_subtitles,
                R.id.row_hide_iot,
                R.id.row_ios_animations
        };
        for (int id : rowIds) {
            NbBrutalDrawable.apply(findViewById(id), white, 10f, 1.5f, 0f, false);
        }

        // Bottom CTA buttons
        NbBrutalDrawable.apply(findViewById(R.id.btn_restart_systemui), yellow, 10f, 2f, 4f, true);
        NbBrutalDrawable.apply(findViewById(R.id.btn_reset_defaults), white, 10f, 2f, 4f, true);
    }

    private void loadStateToUi() {
        mSyncingUi = true;
        try {
            swEnabled.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_ENABLED, true));

            int mode = mPrefs.getInt(ModulePrefs.KEY_LAYOUT_MODE, ModulePrefs.LAYOUT_HYBRID);
            updateRadioSelection(mode);

            int opacity = mPrefs.getInt(ModulePrefs.KEY_GLASS_OPACITY, 46);
            sbGlassOpacity.setValue(opacity, false);
            tvGlassOpacityVal.setText(opacity + "%");

            int rim = mPrefs.getInt(ModulePrefs.KEY_RIM_INTENSITY, 85);
            sbRimIntensity.setValue(rim, false);
            tvRimIntensityVal.setText(rim + "%");

            int dim = mPrefs.getInt(ModulePrefs.KEY_BG_DIM_ALPHA, 18);
            sbBgDim.setValue(dim, false);
            tvBgDimVal.setText(dim + "%");

            swAmbientGlow.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_AMBIENT_GLOW, true));
            swIosHeader.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_IOS_HEADER, true));
            swIosSliders.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_IOS_SLIDERS, true));
            swIosMedia.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_IOS_MEDIA, true));
            swSubtitles.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_SHOW_STATUS_SUBTITLE, true));
            swHideIot.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_HIDE_IOT_CARD, true));
            swIosAnimations.setChecked(mPrefs.getBoolean(ModulePrefs.KEY_IOS_ANIMATIONS, true));
        } finally {
            mSyncingUi = false;
        }
    }

    private void updateRadioSelection(int mode) {
        mSelectedLayoutMode = mode;
        applyRadioVisual(optHybridBg, ivRadioHybrid, tvRadioHybrid, mode == ModulePrefs.LAYOUT_HYBRID);
        applyRadioVisual(optPillsBg, ivRadioPills, tvRadioPills, mode == ModulePrefs.LAYOUT_PILLS);
        applyRadioVisual(optCirclesBg, ivRadioCircles, tvRadioCircles, mode == ModulePrefs.LAYOUT_CIRCLES);
    }

    private void applyRadioVisual(NbBrutalDrawable bg, ImageView icon, TextView label, boolean selected) {
        int selectedFill = ContextCompat.getColor(this, R.color.nb_purple_soft);
        int normalFill = ContextCompat.getColor(this, R.color.nb_surface);
        bg.setFillColor(selected ? selectedFill : normalFill);
        bg.setShadowDp(selected ? 2f : 0f);
        icon.setImageResource(selected ? R.drawable.ic_nb_radio_selected : R.drawable.ic_nb_radio_unselected);
        label.setTypeface(selected ? tfBold : tfMedium);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, selected ? 13.5f : 12.5f);
    }

    private void selectLayoutMode(int mode) {
        updateRadioSelection(mode);
        if (!mSyncingUi) {
            mPrefs.edit().putInt(ModulePrefs.KEY_LAYOUT_MODE, mode).apply();
        }
    }

    private void attachListeners() {
        findViewById(R.id.btn_top_sliders).setOnClickListener(v ->
                scrollView.smoothScrollTo(0, sectionAppearance.getTop()));
        findViewById(R.id.btn_top_layers).setOnClickListener(v ->
                scrollView.smoothScrollTo(0, sectionModules.getTop()));
        findViewById(R.id.btn_top_refresh).setOnClickListener(v -> {
            loadStateToUi();
            Toast.makeText(this, "Preferences synced", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.card_master).setOnClickListener(v -> swEnabled.toggle());
        swEnabled.setOnCheckedChangeListener((b, checked) -> {
            if (!mSyncingUi) mPrefs.edit().putBoolean(ModulePrefs.KEY_ENABLED, checked).apply();
        });

        optHybrid.setOnClickListener(v -> selectLayoutMode(ModulePrefs.LAYOUT_HYBRID));
        optPills.setOnClickListener(v -> selectLayoutMode(ModulePrefs.LAYOUT_PILLS));
        optCircles.setOnClickListener(v -> selectLayoutMode(ModulePrefs.LAYOUT_CIRCLES));

        sbGlassOpacity.setOnValueChangeListener((view, value, fromUser) -> {
            tvGlassOpacityVal.setText(value + "%");
            if (fromUser && !mSyncingUi) {
                mPrefs.edit().putInt(ModulePrefs.KEY_GLASS_OPACITY, value).apply();
            }
        });

        sbRimIntensity.setOnValueChangeListener((view, value, fromUser) -> {
            tvRimIntensityVal.setText(value + "%");
            if (fromUser && !mSyncingUi) {
                mPrefs.edit().putInt(ModulePrefs.KEY_RIM_INTENSITY, value).apply();
            }
        });

        sbBgDim.setOnValueChangeListener((view, value, fromUser) -> {
            tvBgDimVal.setText(value + "%");
            if (fromUser && !mSyncingUi) {
                mPrefs.edit().putInt(ModulePrefs.KEY_BG_DIM_ALPHA, value).apply();
            }
        });

        bindModuleRow(R.id.row_ambient_glow, swAmbientGlow, ModulePrefs.KEY_AMBIENT_GLOW);
        bindModuleRow(R.id.row_ios_header, swIosHeader, ModulePrefs.KEY_IOS_HEADER);
        bindModuleRow(R.id.row_ios_sliders, swIosSliders, ModulePrefs.KEY_IOS_SLIDERS);
        bindModuleRow(R.id.row_ios_media, swIosMedia, ModulePrefs.KEY_IOS_MEDIA);
        bindModuleRow(R.id.row_subtitles, swSubtitles, ModulePrefs.KEY_SHOW_STATUS_SUBTITLE);
        bindModuleRow(R.id.row_hide_iot, swHideIot, ModulePrefs.KEY_HIDE_IOT_CARD);
        bindModuleRow(R.id.row_ios_animations, swIosAnimations, ModulePrefs.KEY_IOS_ANIMATIONS);

        findViewById(R.id.btn_restart_systemui).setOnClickListener(v -> restartSystemUi());

        findViewById(R.id.btn_reset_defaults).setOnClickListener(v -> {
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
    }

    private void bindModuleRow(int rowId, NbToggleView toggle, String prefKey) {
        findViewById(rowId).setOnClickListener(v -> toggle.toggle());
        toggle.setOnCheckedChangeListener((view, isChecked) -> {
            if (!mSyncingUi) {
                mPrefs.edit().putBoolean(prefKey, isChecked).apply();
            }
        });
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
}
