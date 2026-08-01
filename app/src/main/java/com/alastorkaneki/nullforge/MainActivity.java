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
        scroll.setBackground(Ui.screenBackground());
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 34), Ui.dp(this, 18), Ui.dp(this, 28));
        scroll.addView(root);

        TextView eyebrow = Ui.eyebrow(this, "Minecraft Creator Workspace");
        eyebrow.setGravity(Gravity.CENTER);
        root.addView(eyebrow, Ui.matchWrap(this, 7));

        TextView title = Ui.title(this, "NullForge Studio", 34);
        title.setGravity(Gravity.CENTER);
        root.addView(title, Ui.matchWrap(this, 8));

        TextView subtitle = Ui.body(this, "Build packs, browse official assets, and assemble tweak collections without leaving the app.");
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        root.addView(subtitle, Ui.matchWrap(this, 26));

        root.addView(destination(
                "Tweaks Library",
                "Browse Vanilla Tweaks, Bedrock Tweaks, and BEComTweaks with native filters, credits, conflict checks, and local exports.",
                "Open library",
                "com.alastorkaneki.nullforge.TweaksActivity",
                null,
                null,
                true
        ));

        root.addView(destination(
                "Bedrock Workspace",
                "Create resource packs, behavior packs, paired add-ons, skin packs, and world templates.",
                "Open Bedrock",
                "com.alastorkaneki.nullforge.ProjectActivity",
                "edition",
                "BEDROCK",
                false
        ));

        root.addView(destination(
                "Java Workspace",
                "Create Java resource packs, data packs, and combined workspaces.",
                "Open Java",
                "com.alastorkaneki.nullforge.ProjectActivity",
                "edition",
                "JAVA",
                false
        ));

        root.addView(destination(
                "Official Asset Vault",
                "Fetch Mojang assets, cache complete versions offline, and import individual files or folders into projects.",
                "Open vault",
                "com.alastorkaneki.nullforge.AssetVaultActivity",
                null,
                null,
                false
        ));

        TextView legal = Ui.body(this, "Unofficial creator tool. Minecraft belongs to Mojang Studios and Microsoft. Upstream projects and individual creators retain ownership of their work.");
        legal.setGravity(Gravity.CENTER);
        legal.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);
        root.addView(legal);

        return scroll;
    }

    private View destination(String name, String description, String action, String activityClass, String extraName, String extraValue, boolean primary) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView heading = Ui.title(this, name, 21);
        content.addView(heading, Ui.matchWrap(this, 6));
        content.addView(Ui.body(this, description), Ui.matchWrap(this, 14));

        Button button = primary ? Ui.primaryButton(this, action) : Ui.button(this, action);
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
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
        root.setBackground(Ui.screenBackground());

        TextView eyebrow = Ui.eyebrow(this, "Recovery Mode");
        eyebrow.setGravity(Gravity.CENTER);
        root.addView(eyebrow, Ui.matchWrap(this, 8));

        TextView title = Ui.title(this, "NullForge could not draw the launcher", 24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, Ui.matchWrap(this, 12));

        TextView message = Ui.body(this, error.getClass().getName() + "\n\n" + String.valueOf(error.getMessage()));
        message.setGravity(Gravity.CENTER);
        message.setTextIsSelectable(true);
        root.addView(message, Ui.matchWrap(this, 20));

        Button retry = Ui.primaryButton(this, "Retry launcher");
        retry.setOnClickListener(view -> recreate());
        root.addView(retry, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));
        return root;
    }
}
