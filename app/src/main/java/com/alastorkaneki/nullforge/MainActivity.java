package com.alastorkaneki.nullforge;

import android.app.Activity;
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
        Ui.immersive(this);
        setContentView(screen());
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
                TweaksActivity.class
        ));

        root.addView(destination(
                "Bedrock Workspace",
                "Create resource packs, behavior packs, add-ons, skin packs, and world templates.",
                "Open Bedrock",
                ProjectActivity.class,
                "edition",
                "BEDROCK"
        ));

        root.addView(destination(
                "Java Workspace",
                "Create Java resource packs, data packs, and combined workspaces.",
                "Open Java",
                ProjectActivity.class,
                "edition",
                "JAVA"
        ));

        root.addView(destination(
                "Official Asset Vault",
                "Fetch and cache Mojang's Bedrock samples or Java asset indexes for project use.",
                "Open vault",
                AssetVaultActivity.class
        ));

        TextView legal = Ui.body(this, "Unofficial creator tool. Minecraft belongs to Mojang Studios and Microsoft. Upstream tweak projects and individual creators retain ownership of their work.");
        legal.setGravity(Gravity.CENTER);
        legal.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);
        root.addView(legal);

        return scroll;
    }

    private View destination(String name, String description, String action, Class<?> activity) {
        return destination(name, description, action, activity, null, null);
    }

    private View destination(String name, String description, String action, Class<?> activity, String extraName, String extraValue) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView heading = Ui.title(this, name, 21);
        content.addView(heading, Ui.matchWrap(this, 6));

        TextView body = Ui.body(this, description);
        content.addView(body, Ui.matchWrap(this, 14));

        Button button = Ui.button(this, action);
        button.setOnClickListener(view -> {
            Intent intent = new Intent(this, activity);
            if (extraName != null) {
                intent.putExtra(extraName, extraValue);
            }
            startActivity(intent);
        });
        content.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));

        return Ui.card(this, content);
    }
}
