package com.alastorkaneki.nullforge;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public final class Ui {
    public static final int BLACK = Color.rgb(5, 5, 8);
    public static final int PANEL = Color.rgb(17, 17, 23);
    public static final int PANEL_LIGHT = Color.rgb(26, 25, 34);
    public static final int PANEL_HOVER = Color.rgb(39, 35, 50);
    public static final int BORDER = Color.rgb(72, 66, 84);
    public static final int TEXT = Color.rgb(247, 246, 250);
    public static final int MUTED = Color.rgb(181, 177, 191);
    public static final int RED = Color.rgb(255, 43, 83);
    public static final int PURPLE = Color.rgb(177, 78, 255);
    public static final int GREEN = Color.rgb(92, 214, 139);

    private Ui() {
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static void immersive(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        try {
            Window window = activity.getWindow();
            window.setStatusBarColor(BLACK);
            window.setNavigationBarColor(BLACK);
            View decor = window.getDecorView();
            decor.post(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 30) {
                        WindowInsetsController controller = window.getInsetsController();
                        if (controller != null) {
                            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                        }
                    } else {
                        decor.setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        );
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public static GradientDrawable screenBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(7, 7, 11), Color.rgb(11, 7, 16), Color.rgb(5, 5, 8)}
        );
        drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return drawable;
    }

    public static TextView title(Context context, String text, int size) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(TEXT);
        view.setTextSize(size);
        view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        view.setLetterSpacing(size >= 24 ? -0.015f : 0f);
        return view;
    }

    public static TextView body(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(MUTED);
        view.setTextSize(14);
        view.setLineSpacing(0f, 1.12f);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return view;
    }

    public static TextView eyebrow(Context context, String text) {
        TextView view = title(context, text.toUpperCase(), 12);
        view.setTextColor(PURPLE);
        view.setLetterSpacing(0.12f);
        return view;
    }

    public static Button button(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(TEXT);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setStateListAnimator(null);
        button.setBackground(buttonBackground(context, PANEL_LIGHT, PANEL_HOVER, BORDER));
        return button;
    }

    public static Button primaryButton(Context context, String text) {
        Button button = button(context, text);
        button.setBackground(buttonBackground(context, Color.rgb(91, 27, 125), Color.rgb(121, 39, 164), PURPLE));
        return button;
    }

    public static Button tabButton(Context context, String text, boolean selected, int accent) {
        Button button = button(context, text);
        button.setTextColor(selected ? TEXT : MUTED);
        int fill = selected ? Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent)) : PANEL;
        int pressed = selected ? Color.argb(205, Color.red(accent), Color.green(accent), Color.blue(accent)) : PANEL_HOVER;
        button.setBackground(buttonBackground(context, fill, pressed, selected ? Color.WHITE : BORDER));
        return button;
    }

    public static EditText input(Context context, String hint) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setHintTextColor(Color.rgb(128, 123, 139));
        input.setTextColor(TEXT);
        input.setTextSize(15);
        input.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        input.setBackground(outlined(PANEL, BORDER, 7, context));
        return input;
    }

    public static ProgressBar progress(Context context) {
        ProgressBar progress = new ProgressBar(context);
        progress.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= 21) {
            progress.setIndeterminateTintList(ColorStateList.valueOf(PURPLE));
        }
        return progress;
    }

    public static GradientDrawable roundRect(int color, int radius, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    public static GradientDrawable outlined(int fill, int strokeColor, int radius, Context context) {
        GradientDrawable drawable = roundRect(fill, radius, context);
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    public static GradientDrawable accentPanel(Context context) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(36, 12, 25), Color.rgb(19, 17, 27), Color.rgb(31, 14, 44)}
        );
        drawable.setCornerRadius(dp(context, 10));
        drawable.setStroke(dp(context, 1), Color.rgb(107, 65, 127));
        return drawable;
    }

    private static StateListDrawable buttonBackground(Context context, int normal, int pressed, int stroke) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, outlined(pressed, Color.WHITE, 7, context));
        states.addState(new int[]{android.R.attr.state_focused}, outlined(pressed, Color.WHITE, 7, context));
        states.addState(new int[]{-android.R.attr.state_enabled}, outlined(Color.rgb(20, 20, 25), Color.rgb(48, 45, 54), 7, context));
        states.addState(new int[]{}, outlined(normal, stroke, 7, context));
        return states;
    }

    public static LinearLayout card(Context context, View content) {
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));
        outer.setBackground(accentPanel(context));

        LinearLayout.LayoutParams outerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        outerParams.setMargins(0, 0, 0, dp(context, 12));
        outer.setLayoutParams(outerParams);

        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 16));
        inner.setBackground(roundRect(PANEL, 9, context));
        inner.addView(content);
        outer.addView(inner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return outer;
    }

    public static LinearLayout listCard(Context context, View content) {
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        outer.setBackground(outlined(PANEL, BORDER, 8, context));
        outer.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(context, 8));
        outer.setLayoutParams(params);
        return outer;
    }

    public static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static LinearLayout.LayoutParams weight(int weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    public static LinearLayout.LayoutParams matchWrap(Context context, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(context, bottomMargin);
        return params;
    }
}
