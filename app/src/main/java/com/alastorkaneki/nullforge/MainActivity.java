package com.alastorkaneki.nullforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            View content = screen();
            setContentView(content);
            content.post(() -> Ui.immersive(this));
        } catch (Throwable error) {
            setContentView(recovery(error));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    private View screen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 34), Ui.dp(this, 18), Ui.dp(this, 28));
        scroll.addView(root);

        TextView eyebrow = Ui.body(this, "MINECRAFT CREATOR WORKSPACE");
        eyebrow.setTextColor(Ui.PURPLE);
        eyebrow.setGravity(Gravity.CENTER);
        root.addView(eyebrow, Ui.matchWrap(this, 6));

        TextView title = Ui.title(this, "NullForge Studio", 34);
        title.setGravity(Gravity.CENTER);
        root.addView(title, Ui.matchWrap(this, 8));

        TextView subtitle = Ui.body(this, "Build packs, browse official assets, and assemble tweak collections without leaving the app.");
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        root.addView(subtitle, Ui.matchWrap(this, 26));

        root.addView(destination(
                "Tweaks Library",
                "Native selectors for Vanilla Tweaks, Bedrock Tweaks, and BEComTweaks with independent credits and on-device exports.",
                "Open selectors",
                "com.alastorkaneki.nullforge.TweaksActivity",
                null,
                null
        ));

        root.addView(destination(
                "Bedrock Workspace",
                "Create resource packs, behavior packs, add-ons, skin packs, and world templates.",
                "Open Bedrock",
                "com.alastorkaneki.nullforge.ProjectActivity",
                "edition",
                "BEDROCK"
        ));

        root.addView(destination(
                "Java Workspace",
                "Create Java resource packs, data packs, and combined workspaces.",
                "Open Java",
                "com.alastorkaneki.nullforge.ProjectActivity",
                "edition",
                "JAVA"
        ));

        root.addView(destination(
                "Official Asset Vault",
                "Fetch and cache Mojang's Bedrock samples or Java asset indexes for project use.",
                "Open vault",
                "com.alastorkaneki.nullforge.AssetVaultActivity",
                null,
                null
        ));

        TextView legal = Ui.body(this, "Unofficial creator tool. Minecraft belongs to Mojang Studios and Microsoft. Upstream tweak projects and individual creators retain ownership of their work.");
        legal.setGravity(Gravity.CENTER);
        legal.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);
        root.addView(legal);

        return scroll;
    }

    private View destination(String name, String description, String action, String activityClass, String extraName, String extraValue) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView heading = Ui.title(this, name, 21);
        content.addView(heading, Ui.matchWrap(this, 6));

        TextView body = Ui.body(this, description);
        content.addView(body, Ui.matchWrap(this, 14));

        Button button = Ui.button(this, action);
        button.setOnClickListener(view -> open(activityClass, extraName, extraValue));
        content.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));

        return Ui.card(this, content);
    }

    private void open(String activityClass, String extraName, String extraValue) {
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), activityClass);
            if (extraName != null) {
                intent.putExtra(extraName, extraValue);
            }
            startActivity(intent);
        } catch (Throwable error) {
            new AlertDialog.Builder(this)
                    .setTitle("Screen could not open")
                    .setMessage(error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()))
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private View recovery(Throwable error) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(android.graphics.Color.rgb(3, 3, 5));

        TextView title = new TextView(this);
        title.setText("NullForge started in recovery mode");
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(this);
        message.setText(error.getClass().getName() + "\n\n" + String.valueOf(error.getMessage()));
        message.setTextColor(android.graphics.Color.LTGRAY);
        message.setTextSize(14);
        message.setGravity(Gravity.CENTER);
        message.setTextIsSelectable(true);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = 24;
        root.addView(message, messageParams);

        Button retry = new Button(this);
        retry.setText("Retry launcher");
        retry.setOnClickListener(view -> recreate());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryParams.topMargin = 28;
        root.addView(retry, retryParams);
        return root;
    }
}
