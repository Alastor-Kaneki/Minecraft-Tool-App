package com.alastorkaneki.nullforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class AssetVaultActivity extends Activity {
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
        scroll.setBackgroundColor(Ui.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 28), Ui.dp(this, 18), Ui.dp(this, 28));
        scroll.addView(root);

        LinearLayout header = Ui.row(this);
        Button back = Ui.button(this, "‹");
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));
        TextView title = Ui.title(this, "Official Asset Vault", 26);
        LinearLayout.LayoutParams titleParams = Ui.weight(1);
        titleParams.leftMargin = Ui.dp(this, 12);
        header.addView(title, titleParams);
        root.addView(header, Ui.matchWrap(this, 18));

        root.addView(sourceCard(
                "Bedrock samples",
                "Mojang/bedrock-samples",
                "Browse the official Bedrock sample repository and releases.",
                "https://github.com/Mojang/bedrock-samples"
        ));

        root.addView(sourceCard(
                "Java version manifest",
                "Mojang piston metadata",
                "Open the official version manifest used to resolve Java client and asset indexes.",
                "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        ));

        TextView notice = Ui.body(this, "The source hydration bundle referenced by the original repository workflow was never committed. The Tweaks merge restores a compiling native app shell while preserving the asset-source destinations. Full offline asset extraction remains a separate module.");
        notice.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        notice.setGravity(Gravity.CENTER);
        notice.setBackground(Ui.outlined(Ui.PANEL, Ui.PURPLE, 14, this));
        root.addView(notice);

        return scroll;
    }

    private View sourceCard(String name, String source, String description, String url) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView heading = Ui.title(this, name, 20);
        content.addView(heading, Ui.matchWrap(this, 4));
        TextView sourceView = Ui.body(this, source);
        sourceView.setTextColor(Ui.PURPLE);
        content.addView(sourceView, Ui.matchWrap(this, 8));
        content.addView(Ui.body(this, description), Ui.matchWrap(this, 14));
        Button open = Ui.button(this, "Open source");
        open.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception error) {
                new AlertDialog.Builder(this).setMessage("No app can open this link.").setPositiveButton("OK", null).show();
            }
        });
        content.addView(open, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));
        return Ui.card(this, content);
    }
}
