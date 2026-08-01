package com.alastorkaneki.nullforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class TweaksActivity extends Activity {
    private static final int CREATE_PACK = 7001;
    private static final int PAGE_SIZE = 32;

    private final Map<String, LinkedHashMap<String, TweakPack>> selections = new LinkedHashMap<>();
    private final List<TweakPack> catalog = new ArrayList<>();

    private ExecutorService executor;
    private Handler main;
    private LinearLayout providerRow;
    private LinearLayout sectionRow;
    private LinearLayout list;
    private EditText search;
    private TextView status;
    private TextView selectedCount;
    private Button build;
    private ProgressBar progress;
    private TweakProvider provider = TweakProvider.VANILLA;
    private String section = provider.sections[0];
    private String notice = "";
    private File pendingExport;
    private boolean destroyed;
    private int requestToken;
    private int visibleLimit = PAGE_SIZE;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        main = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
        try {
            Ui.immersive(this);
            setContentView(buildScreen());
            renderProviderTabs();
            renderSectionTabs();
            load(false);
        } catch (Throwable error) {
            setContentView(failureScreen(error));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        requestToken++;
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.screenBackground());
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 12));

        LinearLayout header = Ui.row(this);
        Button back = Ui.button(this, "‹");
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.eyebrow(this, "Content Library"));
        titles.addView(Ui.title(this, "Tweaks", 27));
        titles.addView(Ui.body(this, "Vanilla Tweaks, Bedrock Tweaks, and BEComTweaks"));
        LinearLayout.LayoutParams titleParams = Ui.weight(1);
        titleParams.leftMargin = Ui.dp(this, 12);
        header.addView(titles, titleParams);

        Button credits = Ui.button(this, "Credits");
        credits.setOnClickListener(view -> showCredits());
        header.addView(credits, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 48)));
        root.addView(header, Ui.matchWrap(this, 14));

        HorizontalScrollView providerScroll = new HorizontalScrollView(this);
        providerScroll.setHorizontalScrollBarEnabled(false);
        providerScroll.setFillViewport(false);
        providerRow = Ui.row(this);
        providerScroll.addView(providerRow);
        root.addView(providerScroll, Ui.matchWrap(this, 9));

        HorizontalScrollView sectionScroll = new HorizontalScrollView(this);
        sectionScroll.setHorizontalScrollBarEnabled(false);
        sectionScroll.setFillViewport(false);
        sectionRow = Ui.row(this);
        sectionScroll.addView(sectionRow);
        root.addView(sectionScroll, Ui.matchWrap(this, 12));

        LinearLayout controls = Ui.row(this);
        search = Ui.input(this, "Search this catalog");
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                visibleLimit = PAGE_SIZE;
                renderCatalog();
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        controls.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1));

        Button refresh = Ui.button(this, "Refresh");
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 50));
        refreshParams.leftMargin = Ui.dp(this, 8);
        controls.addView(refresh, refreshParams);
        refresh.setOnClickListener(view -> load(true));
        root.addView(controls, Ui.matchWrap(this, 10));

        LinearLayout statusPanel = Ui.row(this);
        statusPanel.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        statusPanel.setBackground(Ui.outlined(Ui.PANEL, Ui.BORDER, 7, this));
        status = Ui.body(this, "Loading catalog");
        statusRowColor();
        statusPanel.addView(status, Ui.weight(1));
        progress = Ui.progress(this);
        statusPanel.addView(progress, new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));
        root.addView(statusPanel, Ui.matchWrap(this, 9));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout footer = Ui.row(this);
        footer.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        footer.setBackground(Ui.outlined(Ui.PANEL, Ui.BORDER, 8, this));
        selectedCount = Ui.title(this, "0 selected", 15);
        footer.addView(selectedCount, Ui.weight(1));
        build = Ui.primaryButton(this, "Build pack");
        build.setEnabled(false);
        build.setOnClickListener(view -> buildPack());
        footer.addView(build, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 50)));
        root.addView(footer);

        return root;
    }

    private View failureScreen(Throwable error) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
        root.setBackground(Ui.screenBackground());
        TextView eyebrow = Ui.eyebrow(this, "Safe Mode");
        eyebrow.setGravity(Gravity.CENTER);
        root.addView(eyebrow, Ui.matchWrap(this, 8));
        TextView title = Ui.title(this, "Tweaks could not open", 25);
        title.setGravity(Gravity.CENTER);
        root.addView(title, Ui.matchWrap(this, 10));
        TextView body = Ui.body(this, message(error));
        body.setGravity(Gravity.CENTER);
        root.addView(body, Ui.matchWrap(this, 18));
        Button retry = Ui.primaryButton(this, "Retry");
        retry.setOnClickListener(view -> recreate());
        root.addView(retry, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));
        Button close = Ui.button(this, "Back");
        close.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50));
        closeParams.topMargin = Ui.dp(this, 8);
        root.addView(close, closeParams);
        return root;
    }

    private void renderProviderTabs() {
        if (providerRow == null) {
            return;
        }
        providerRow.removeAllViews();
        for (TweakProvider value : TweakProvider.values()) {
            Button button = Ui.tabButton(this, value.label, value == provider, Ui.PURPLE);
            button.setOnClickListener(view -> {
                provider = value;
                section = provider.sections[0];
                visibleLimit = PAGE_SIZE;
                catalog.clear();
                notice = "";
                renderProviderTabs();
                renderSectionTabs();
                load(false);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 46));
            params.rightMargin = Ui.dp(this, 8);
            providerRow.addView(button, params);
        }
    }

    private void renderSectionTabs() {
        if (sectionRow == null) {
            return;
        }
        sectionRow.removeAllViews();
        for (String value : provider.sections) {
            Button button = Ui.tabButton(this, value, value.equals(section), Ui.RED);
            button.setOnClickListener(view -> {
                section = value;
                visibleLimit = PAGE_SIZE;
                catalog.clear();
                notice = "";
                renderSectionTabs();
                load(false);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 43));
            params.rightMargin = Ui.dp(this, 8);
            sectionRow.addView(button, params);
        }
        updateSelectionCount();
    }

    private void load(boolean force) {
        if (destroyed || executor == null || executor.isShutdown()) {
            return;
        }
        visibleLimit = PAGE_SIZE;
        int token = ++requestToken;
        progress.setVisibility(View.VISIBLE);
        status.setText("Loading " + provider.label + " • " + section);
        list.removeAllViews();
        TweakProvider requestedProvider = provider;
        String requestedSection = section;
        try {
            executor.execute(() -> {
                try {
                    TweaksRepository.Catalog result = TweaksRepository.load(this, requestedProvider, requestedSection, force);
                    safePost(() -> {
                        if (token != requestToken || provider != requestedProvider || !section.equals(requestedSection)) {
                            return;
                        }
                        catalog.clear();
                        catalog.addAll(result.packs());
                        notice = result.notice();
                        progress.setVisibility(View.GONE);
                        status.setText(catalog.size() + " packs available");
                        renderCatalog();
                    });
                } catch (Throwable error) {
                    safePost(() -> {
                        if (token != requestToken || provider != requestedProvider || !section.equals(requestedSection)) {
                            return;
                        }
                        progress.setVisibility(View.GONE);
                        status.setText("Catalog unavailable");
                        notice = message(error);
                        renderCatalog();
                    });
                }
            });
        } catch (RejectedExecutionException error) {
            progress.setVisibility(View.GONE);
            status.setText("Catalog stopped");
        }
    }

    private void renderCatalog() {
        if (list == null || destroyed) {
            return;
        }
        try {
            list.removeAllViews();
            if (notice != null && !notice.trim().isEmpty()) {
                TextView noticeView = Ui.body(this, notice);
                noticeView.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
                list.addView(Ui.listCard(this, noticeView));
            }

            String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
            String currentCategory = null;
            int matched = 0;
            int shown = 0;
            for (TweakPack pack : catalog) {
                String haystack = (pack.name + " " + pack.description + " " + pack.category).toLowerCase(Locale.ROOT);
                if (!query.isEmpty() && !haystack.contains(query)) {
                    continue;
                }
                matched++;
                if (shown >= visibleLimit) {
                    continue;
                }
                if (!pack.category.equals(currentCategory)) {
                    currentCategory = pack.category;
                    TextView heading = Ui.eyebrow(this, currentCategory);
                    heading.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), 0, Ui.dp(this, 8));
                    list.addView(heading);
                }
                list.addView(packCard(pack));
                shown++;
            }

            if (matched == 0) {
                TextView empty = Ui.body(this, query.isEmpty() ? "No public catalog is available for this section." : "No packs match this search.");
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(Ui.dp(this, 20), Ui.dp(this, 42), Ui.dp(this, 20), Ui.dp(this, 42));
                list.addView(empty);
            } else if (shown < matched) {
                LinearLayout morePanel = new LinearLayout(this);
                morePanel.setOrientation(LinearLayout.VERTICAL);
                TextView summary = Ui.body(this, "Showing " + shown + " of " + matched + " packs");
                summary.setGravity(Gravity.CENTER);
                morePanel.addView(summary, Ui.matchWrap(this, 10));
                Button more = Ui.button(this, "Load " + Math.min(PAGE_SIZE, matched - shown) + " more");
                more.setOnClickListener(view -> {
                    visibleLimit += PAGE_SIZE;
                    renderCatalog();
                });
                morePanel.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
                list.addView(Ui.listCard(this, morePanel));
            }
            updateSelectionCount();
        } catch (Throwable error) {
            list.removeAllViews();
            TextView failure = Ui.body(this, "The catalog could not be displayed safely.\n" + message(error));
            failure.setGravity(Gravity.CENTER);
            failure.setPadding(Ui.dp(this, 16), Ui.dp(this, 40), Ui.dp(this, 16), Ui.dp(this, 40));
            list.addView(failure);
            status.setText("Display failed safely");
        }
    }

    private View packCard(TweakPack pack) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        CheckBox check = new CheckBox(this);
        check.setText(pack.name);
        check.setTextColor(Ui.TEXT);
        check.setTextSize(16);
        check.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        check.setButtonTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Ui.PURPLE, Color.rgb(118, 112, 128)}
        ));
        check.setChecked(currentSelection().containsKey(pack.key()));
        bindToggle(check, pack);
        content.addView(check);

        TextView description = Ui.body(this, pack.description.trim().isEmpty() ? "No description supplied upstream." : pack.description);
        LinearLayout.LayoutParams descriptionParams = Ui.matchWrap(this, 0);
        descriptionParams.leftMargin = Ui.dp(this, 34);
        content.addView(description, descriptionParams);

        if (!pack.conflicts.isEmpty()) {
            TextView conflicts = Ui.body(this, "Conflicts: " + String.join(", ", pack.conflicts));
            conflicts.setTextColor(Color.rgb(255, 137, 156));
            LinearLayout.LayoutParams params = Ui.matchWrap(this, 0);
            params.leftMargin = Ui.dp(this, 34);
            params.topMargin = Ui.dp(this, 6);
            content.addView(conflicts, params);
        }

        if (pack.sourceOnly) {
            TextView source = Ui.body(this, "Source export • upstream build required");
            source.setTextColor(Color.rgb(208, 158, 255));
            LinearLayout.LayoutParams params = Ui.matchWrap(this, 0);
            params.leftMargin = Ui.dp(this, 34);
            params.topMargin = Ui.dp(this, 6);
            content.addView(source, params);
        }

        return Ui.listCard(this, content);
    }

    private void bindToggle(CheckBox check, TweakPack pack) {
        check.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                currentSelection().remove(pack.key());
                updateSelectionCount();
                return;
            }
            List<TweakPack> conflicts = activeConflicts(pack);
            if (conflicts.isEmpty()) {
                currentSelection().put(pack.key(), pack);
                updateSelectionCount();
                return;
            }
            check.setOnCheckedChangeListener(null);
            check.setChecked(false);
            bindToggle(check, pack);
            new AlertDialog.Builder(this)
                    .setTitle("Pack conflict")
                    .setMessage(pack.name + " conflicts with " + names(conflicts) + ". Add it anyway?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Add anyway", (dialog, which) -> {
                        currentSelection().put(pack.key(), pack);
                        check.setOnCheckedChangeListener(null);
                        check.setChecked(true);
                        bindToggle(check, pack);
                        updateSelectionCount();
                    })
                    .show();
        });
    }

    private List<TweakPack> activeConflicts(TweakPack pack) {
        List<TweakPack> found = new ArrayList<>();
        for (TweakPack selected : currentSelection().values()) {
            String selectedId = selected.normalizedId();
            for (String conflict : pack.conflicts) {
                if (TweakPack.normalize(conflict).equals(selectedId)) {
                    found.add(selected);
                    break;
                }
            }
            for (String conflict : selected.conflicts) {
                if (TweakPack.normalize(conflict).equals(pack.normalizedId()) && !found.contains(selected)) {
                    found.add(selected);
                    break;
                }
            }
        }
        return found;
    }

    private String names(List<TweakPack> packs) {
        List<String> values = new ArrayList<>();
        for (TweakPack pack : packs) {
            values.add(pack.name);
        }
        return String.join(", ", values);
    }

    private LinkedHashMap<String, TweakPack> currentSelection() {
        return selections.computeIfAbsent(provider.name() + ":" + section, ignored -> new LinkedHashMap<>());
    }

    private void updateSelectionCount() {
        if (selectedCount == null || build == null) {
            return;
        }
        int count = currentSelection().size();
        selectedCount.setText(count + " selected");
        build.setEnabled(count > 0);
        build.setAlpha(count > 0 ? 1f : 0.45f);
    }

    private void buildPack() {
        List<TweakPack> selected = new ArrayList<>(currentSelection().values());
        if (selected.isEmpty() || executor == null || executor.isShutdown()) {
            return;
        }
        build.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        TweakProvider requestedProvider = provider;
        String requestedSection = section;
        try {
            executor.execute(() -> {
                try {
                    PackAssembler.BuildResult result = PackAssembler.build(
                            this,
                            requestedProvider,
                            requestedSection,
                            selected,
                            (message, current, total) -> safePost(() -> status.setText(message + " • " + current + "/" + total))
                    );
                    safePost(() -> {
                        progress.setVisibility(View.GONE);
                        build.setEnabled(true);
                        pendingExport = result.file();
                        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        save.addCategory(Intent.CATEGORY_OPENABLE);
                        save.setType(result.mimeType());
                        save.putExtra(Intent.EXTRA_TITLE, result.fileName());
                        startActivityForResult(save, CREATE_PACK);
                        String message = result.included() + " packs assembled";
                        if (!result.missing().isEmpty()) {
                            message += " • missing " + String.join(", ", result.missing());
                        }
                        status.setText(message);
                    });
                } catch (Throwable error) {
                    safePost(() -> {
                        progress.setVisibility(View.GONE);
                        build.setEnabled(true);
                        status.setText("Build failed");
                        new AlertDialog.Builder(this)
                                .setTitle("Could not build pack")
                                .setMessage(message(error))
                                .setPositiveButton("OK", null)
                                .show();
                    });
                }
            });
        } catch (RejectedExecutionException error) {
            build.setEnabled(true);
            progress.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CREATE_PACK && resultCode == RESULT_OK && data != null && data.getData() != null && pendingExport != null && executor != null && !executor.isShutdown()) {
            Uri destination = data.getData();
            File source = pendingExport;
            try {
                executor.execute(() -> {
                    try (FileInputStream input = new FileInputStream(source); OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                        if (output == null) {
                            throw new IllegalStateException("Could not open destination");
                        }
                        byte[] buffer = new byte[32768];
                        int read;
                        while ((read = input.read(buffer)) >= 0) {
                            output.write(buffer, 0, read);
                        }
                        output.flush();
                        source.delete();
                        safePost(() -> {
                            status.setText("Pack saved");
                            Toast.makeText(this, "Pack exported", Toast.LENGTH_LONG).show();
                        });
                    } catch (Throwable error) {
                        safePost(() -> new AlertDialog.Builder(this)
                                .setTitle("Export failed")
                                .setMessage(message(error))
                                .setPositiveButton("OK", null)
                                .show());
                    }
                });
            } catch (RejectedExecutionException ignored) {
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showCredits() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), 0);

        TextView subtitle = Ui.eyebrow(this, provider.subtitle);
        content.addView(subtitle, Ui.matchWrap(this, 12));

        TextView text = Ui.body(this, provider.credits + "\n\nIndividual pack credits and licenses remain attached to the upstream projects.");
        content.addView(text, Ui.matchWrap(this, 16));

        Button site = Ui.primaryButton(this, "Open " + provider.label);
        site.setOnClickListener(view -> open(provider.website));
        content.addView(site, Ui.matchWrap(this, 8));

        Button repo = Ui.button(this, "Open GitHub");
        repo.setOnClickListener(view -> open(provider.repository));
        content.addView(repo);

        new AlertDialog.Builder(this)
                .setTitle(provider.label + " credits")
                .setView(content)
                .setNegativeButton("Close", null)
                .show();
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable error) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_LONG).show();
        }
    }

    private void safePost(Runnable action) {
        if (main == null || destroyed) {
            return;
        }
        main.post(() -> {
            if (destroyed || isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                return;
            }
            try {
                action.run();
            } catch (Throwable error) {
                if (status != null) {
                    status.setText("Recovered from a display error");
                }
            }
        });
    }

    private void statusRowColor() {
        if (status != null) {
            status.setTextColor(Ui.MUTED);
        }
    }

    private String message(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        String value = error.getMessage();
        return value == null || value.trim().isEmpty() ? error.getClass().getSimpleName() : value;
    }
}
