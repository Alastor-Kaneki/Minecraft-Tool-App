package com.alastorkaneki.nullforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AssetVaultActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private AssetCacheManager manager;
    private LinearLayout snapshots;
    private TextView status;
    private ProgressBar progress;
    private EditText javaVersion;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        manager = new AssetCacheManager(this);
        Ui.immersive(this);
        setContentView(screen());
        renderSnapshots();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        renderSnapshots();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View screen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 28), Ui.dp(this, 18), Ui.dp(this, 28));
        scroll.addView(root);

        LinearLayout header = Ui.row(this);
        Button back = Ui.button(this, "‹");
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.title(this, "Official Asset Vault", 26));
        titles.addView(Ui.body(this, "Download once, browse and reuse offline"));
        LinearLayout.LayoutParams titleParams = Ui.weight(1);
        titleParams.leftMargin = Ui.dp(this, 12);
        header.addView(titles, titleParams);
        root.addView(header, Ui.matchWrap(this, 18));

        root.addView(bedrockCard());
        root.addView(javaCard());

        LinearLayout statusRow = Ui.row(this);
        status = Ui.body(this, "Ready");
        statusRow.addView(status, Ui.weight(1));
        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        statusRow.addView(progress, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
        root.addView(statusRow, Ui.matchWrap(this, 14));

        TextView cachedTitle = Ui.title(this, "Cached snapshots", 21);
        root.addView(cachedTitle, Ui.matchWrap(this, 10));
        snapshots = new LinearLayout(this);
        snapshots.setOrientation(LinearLayout.VERTICAL);
        root.addView(snapshots);

        TextView legal = Ui.body(this, "Assets are fetched directly from Mojang services and stay in this app's private storage. Minecraft content remains subject to Mojang and Microsoft terms.");
        legal.setGravity(Gravity.CENTER);
        legal.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);
        root.addView(legal);
        return scroll;
    }

    private View bedrockCard() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(Ui.title(this, "Bedrock Edition", 21), Ui.matchWrap(this, 5));
        TextView source = Ui.body(this, "Mojang/bedrock-samples full release archives");
        source.setTextColor(Ui.RED);
        content.addView(source, Ui.matchWrap(this, 8));
        content.addView(Ui.body(this, "Caches the complete stable or preview archive, including resource-pack and behavior-pack files, textures, models, sounds, definitions, documentation, and metadata."), Ui.matchWrap(this, 14));
        LinearLayout row = Ui.row(this);
        Button stable = Ui.button(this, "Cache latest stable");
        stable.setOnClickListener(view -> cacheBedrock(false));
        row.addView(stable, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
        Button preview = Ui.button(this, "Cache latest preview");
        preview.setOnClickListener(view -> cacheBedrock(true));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1);
        previewParams.leftMargin = Ui.dp(this, 8);
        row.addView(preview, previewParams);
        content.addView(row);
        return Ui.card(this, content);
    }

    private View javaCard() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(Ui.title(this, "Java Edition", 21), Ui.matchWrap(this, 5));
        TextView source = Ui.body(this, "Mojang version manifest, client JAR, asset index, and object service");
        source.setTextColor(Ui.PURPLE);
        content.addView(source, Ui.matchWrap(this, 8));
        content.addView(Ui.body(this, "Enter any version ID or fill the latest release or snapshot automatically. The vault extracts assets and data, then downloads every indexed asset object into its original path."), Ui.matchWrap(this, 12));
        javaVersion = new EditText(this);
        javaVersion.setSingleLine(true);
        javaVersion.setHint("Version ID, for example 1.21.8");
        javaVersion.setTextColor(Ui.TEXT);
        javaVersion.setHintTextColor(Ui.MUTED);
        javaVersion.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        javaVersion.setBackground(Ui.outlined(Ui.PANEL, Ui.PURPLE, 14, this));
        content.addView(javaVersion, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));
        LinearLayout fillRow = Ui.row(this);
        Button latestRelease = Ui.button(this, "Use latest release");
        latestRelease.setOnClickListener(view -> fillLatestJava(false));
        fillRow.addView(latestRelease, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        Button latestSnapshot = Ui.button(this, "Use latest snapshot");
        latestSnapshot.setOnClickListener(view -> fillLatestJava(true));
        LinearLayout.LayoutParams snapshotParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        snapshotParams.leftMargin = Ui.dp(this, 8);
        fillRow.addView(latestSnapshot, snapshotParams);
        LinearLayout.LayoutParams fillParams = Ui.matchWrap(this, 8);
        fillParams.topMargin = Ui.dp(this, 8);
        content.addView(fillRow, fillParams);
        Button cache = Ui.button(this, "Cache Java version");
        cache.setOnClickListener(view -> cacheJava());
        content.addView(cache, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        return Ui.card(this, content);
    }

    private void fillLatestJava(boolean snapshot) {
        runTask(() -> {
            String version = manager.latestJavaVersion(snapshot);
            main.post(() -> javaVersion.setText(version));
            setStatus("Selected Java " + version, false);
        });
    }

    private void cacheBedrock(boolean preview) {
        runTask(() -> {
            AssetCacheManager.Snapshot snapshot = manager.cacheBedrock(preview, listener());
            main.post(() -> {
                renderSnapshots();
                open(snapshot);
            });
        });
    }

    private void cacheJava() {
        String version = javaVersion.getText().toString().trim();
        runTask(() -> {
            AssetCacheManager.Snapshot snapshot = manager.cacheJava(version, listener());
            main.post(() -> {
                javaVersion.setText(snapshot.version);
                renderSnapshots();
                open(snapshot);
            });
        });
    }

    private AssetCacheManager.Listener listener() {
        return new AssetCacheManager.Listener() {
            @Override
            public void onStatus(String text) {
                setStatus(text, true);
            }

            @Override
            public void onProgress(int completed, int total) {
                if (total <= 0) {
                    return;
                }
                int percent = (int) Math.min(100, Math.round(completed * 100f / total));
                setStatus("Downloading assets: " + percent + "%", true);
            }
        };
    }

    private void runTask(Task task) {
        setStatus("Starting", true);
        executor.submit(() -> {
            try {
                task.run();
                setStatus("Ready", false);
            } catch (Exception error) {
                setStatus("Failed", false);
                main.post(() -> new AlertDialog.Builder(this)
                        .setTitle("Asset vault error")
                        .setMessage(error.getMessage())
                        .setPositiveButton("OK", null)
                        .show());
            }
        });
    }

    private void setStatus(String text, boolean busy) {
        main.post(() -> {
            if (status != null) {
                status.setText(text);
            }
            if (progress != null) {
                progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void renderSnapshots() {
        if (snapshots == null) {
            return;
        }
        snapshots.removeAllViews();
        List<AssetCacheManager.Snapshot> values = manager.listSnapshots();
        if (values.isEmpty()) {
            TextView empty = Ui.body(this, "No assets are cached yet.");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 22));
            snapshots.addView(empty);
            return;
        }
        for (AssetCacheManager.Snapshot snapshot : values) {
            snapshots.addView(snapshotCard(snapshot));
        }
    }

    private View snapshotCard(AssetCacheManager.Snapshot snapshot) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(Ui.title(this, snapshot.label, 18), Ui.matchWrap(this, 4));
        content.addView(Ui.body(this, snapshot.content.getAbsolutePath()), Ui.matchWrap(this, 10));
        LinearLayout row = Ui.row(this);
        Button browse = Ui.button(this, "Browse everything");
        browse.setOnClickListener(view -> open(snapshot));
        row.addView(browse, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        Button delete = Ui.button(this, "Delete cache");
        delete.setOnClickListener(view -> confirmDelete(snapshot));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        deleteParams.leftMargin = Ui.dp(this, 8);
        row.addView(delete, deleteParams);
        content.addView(row);
        return Ui.card(this, content);
    }

    private void open(AssetCacheManager.Snapshot snapshot) {
        Intent intent = new Intent(this, AssetBrowserActivity.class);
        intent.putExtra("root", snapshot.content.getAbsolutePath());
        intent.putExtra("edition", snapshot.edition);
        intent.putExtra("label", snapshot.label);
        startActivity(intent);
    }

    private void confirmDelete(AssetCacheManager.Snapshot snapshot) {
        new AlertDialog.Builder(this)
                .setTitle("Delete cached assets?")
                .setMessage(snapshot.label + " will need to be downloaded again before it can be browsed offline.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> runTask(() -> {
                    manager.deleteSnapshot(snapshot);
                    main.post(() -> {
                        renderSnapshots();
                        Toast.makeText(this, "Cache deleted", Toast.LENGTH_SHORT).show();
                    });
                }))
                .show();
    }

    private interface Task {
        void run() throws Exception;
    }
}
