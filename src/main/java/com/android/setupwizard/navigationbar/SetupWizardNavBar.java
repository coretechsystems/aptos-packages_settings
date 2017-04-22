package com.android.setupwizard.navigationbar;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.Button;

public class SetupWizardNavBar extends Fragment implements OnClickListener, OnPreDrawListener {
    private Button mBackButton;
    private NavigationBarListener mCallback;
    private ViewGroup mNavigationBarView;
    private Button mNextButton;
    private int mSystemUiFlags = 4610;

    public interface NavigationBarListener {
        void onNavigateBack();

        void onNavigateNext();

        void onNavigationBarCreated(SetupWizardNavBar setupWizardNavBar);
    }

    public static class NavButton extends Button {
        public NavButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
        }

        public NavButton(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        public NavButton(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public NavButton(Context context) {
            super(context);
        }

        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setAlpha(enabled ? 1.0f : 0.23f);
        }
    }

    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.mCallback = (NavigationBarListener) activity;
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.mNavigationBarView = (ViewGroup) LayoutInflater.from(new ContextThemeWrapper(getActivity(), getNavbarTheme())).inflate(R.layout.setup_wizard_navbar_layout, container, false);
        this.mNextButton = (Button) this.mNavigationBarView.findViewById(R.id.setup_wizard_navbar_next);
        this.mBackButton = (Button) this.mNavigationBarView.findViewById(R.id.setup_wizard_navbar_back);
        this.mNextButton.setOnClickListener(this);
        this.mBackButton.setOnClickListener(this);
        return this.mNavigationBarView;
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.mCallback.onNavigationBarCreated(this);
        this.mNavigationBarView.setSystemUiVisibility(this.mSystemUiFlags);
        this.mNavigationBarView.getViewTreeObserver().addOnPreDrawListener(this);
    }

    public boolean onPreDraw() {
        this.mNavigationBarView.setSystemUiVisibility(this.mSystemUiFlags);
        return true;
    }

    public void setUseImmersiveMode(boolean useImmersiveMode) {
        setUseImmersiveMode(useImmersiveMode, useImmersiveMode);
    }

    public void setUseImmersiveMode(boolean useImmersiveMode, boolean layoutHideNavigation) {
        if (useImmersiveMode) {
            this.mSystemUiFlags |= 4098;
            if (layoutHideNavigation) {
                this.mSystemUiFlags |= 512;
            }
        } else {
            this.mSystemUiFlags &= -4611;
        }
        this.mNavigationBarView.setSystemUiVisibility(this.mSystemUiFlags);
    }

    private int getNavbarTheme() {
        boolean isDarkBg = true;
        TypedArray attributes = getActivity().obtainStyledAttributes(new int[]{R.attr.setup_wizard_navbar_theme, 16842800, 16842801});
        int theme = attributes.getResourceId(0, 0);
        if (theme == 0) {
            float[] foregroundHsv = new float[3];
            float[] backgroundHsv = new float[3];
            Color.colorToHSV(attributes.getColor(1, 0), foregroundHsv);
            Color.colorToHSV(attributes.getColor(2, 0), backgroundHsv);
            if (foregroundHsv[2] <= backgroundHsv[2]) {
                isDarkBg = false;
            }
            theme = isDarkBg ? R.style.setup_wizard_navbar_theme_dark : R.style.setup_wizard_navbar_theme_light;
        }
        attributes.recycle();
        return theme;
    }

    public void onClick(View v) {
        if (v == this.mBackButton) {
            this.mCallback.onNavigateBack();
        } else if (v == this.mNextButton) {
            this.mCallback.onNavigateNext();
        }
    }

    public Button getNextButton() {
        return this.mNextButton;
    }
}
