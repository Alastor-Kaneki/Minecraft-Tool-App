package com.alastorkaneki.nullforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
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

public final class TweaksActivity extends Activity {
    private static final int CREATE_PACK = 7001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, LinkedHashMap<String, TweakPack>> selections = new LinkedHashMap<>();
    private final List<TweakPack> catalog = new ArrayList<>();

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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.immersive(this);
        setContentView(buildScreen());
        renderProviderTabs();
        renderSectionTabs();
        load(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BLACK);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 12));

        LinearLayout header = Ui.row(this);
        Button back = Ui.button(this, "‹");
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.title(this, "Tweaks Library", 26);
        TextView subtitle = Ui.body(this, "Native selectors for Vanilla Tweaks, Bedrock Tweaks, and BEComTweaks");
        titles.addView(title);
        titles.addView(subtitle);
        LinearLayout.LayoutParams titleParams = Ui.weight(1);
        titleParams.leftMargin = Ui.dp(this, 12);
        header.addView(titles, titleParams);

        Button credits = Ui.button(this, "Credits");
        credits.setOnClickListener(view -> showCredits());
        header.addView(credits, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 48)));
        root.addView(header, Ui.matchWrap(this, 14));

        HorizontalScrollView providerScroll = new HorizontalScrollView(this);
        providerScroll.setHorizontalScrollBarEnabled(false);
        providerRow = Ui.row(this);
        providerScroll.addView(providerRow);
        root.addView(providerScroll, Ui.matchWrap(this, 10));

        HorizontalScrollView sectionScroll = new HorizontalScrollView(this);
        sectionScroll.setHorizontalScrollBarEnabled(false);
        sectionRow = Ui.row(this);
        sectionScroll.addView(sectionRow);
        root.addView(sectionScroll, Ui.matchWrap(this, 10));

        LinearLayout controls = Ui.row(this);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search packs");
        search.setHintTextColor(Color.rgb(120, 116, 132));
        search.setTextColor(Ui.TEXT);
        search.setTextSize(15);
        search.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        search.setBackground(Ui.outlined(Ui.PANEL, Color.rgb(58, 52, 72), 14, this));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                renderCatalog();
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        controls.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(this, 50), 1));

        Button refresh = Ui.button(this, "↻");
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 50));
        refreshParams.leftMargin = Ui.dp(this, 8);
        controls.addView(refresh, refreshParams);
        refresh.setOnClickListener(view -> load(true));
        root.addView(controls, Ui.matchWrap(this, 10));

        LinearLayout statusRow = Ui.row(this);
        status = Ui.body(this, "Loading catalog");
        statusRow.addView(status, Ui.weight(1));
        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        statusRow.addView(progress, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        root.addView(statusRow, Ui.matchWrap(this, 8));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout footer = Ui.row(this);
        selectedCount = Ui.title(this, "0 selected", 15);
        footer.addView(selectedCount, Ui.weight(1));
        build = Ui.button(this, "Build pack");
        build.setEnabled(false);
        build.setOnClickListener(view -> buildPack());
        footer.addView(build, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 52)));
        root.addView(footer);

        return root;
    }

    private void renderProviderTabs() {
        providerRow.removeAllViews();
        for (TweakProvider value : TweakProvider.values()) {
            Button button = Ui.button(this, value.label);
            boolean active = value == provider;
            button.setTextColor(active ? Ui.TEXT : Ui.MUTED);
            button.setBackground(Ui.outlined(active ? Color.rgb(45, 14, 52) : Ui.PANEL, active ? Ui.PURPLE : Color.rgb(52, 48, 62), 14, this));
            button.setOnClickListener(view -> {
                provider = value;
                section = provider.sections[0];
                catalog.clear();
                notice = "";
                renderProviderTabs();
                renderSectionTabs();
                load(false);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 48));
            params.rightMargin = Ui.dp(this, 8);
            providerRow.addView(button, params);
        }
    }

    private void renderSectionTabs() {
        sectionRow.removeAllViews();
        for (String value : provider.sections) {
            Button button = Ui.button(this, value);
            boolean active = value.equals(section);
            button.setTextColor(active ? Ui.TEXT : Ui.MUTED);
            button.setBackground(Ui.outlined(active ? Color.rgb(47, 16, 28) : Ui.PANEL, active ? Ui.RED : Color.rgb(52, 48, 62), 14, this));
            button.setOnClickListener(view -> {
                section = value;
                catalog.clear();
                notice = "";
                renderSectionTabs();
                load(false);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44));
            params.rightMargin = Ui.dp(this, 8);
            sectionRow.addView(button, params);
        }
        updateSelectionCount();
    }

    private void load(boolean force) {
        progress.setVisibility(View.VISIBLE);
        status.setText("Loading " + provider.label + " " + section.toLowerCase(Locale.ROOT));
        list.removeAllViews();
        TweakProvider requestedProvider = provider;
        String requestedSection = section;
        executor.execute(() -> {
            try {
                TweaksRepository.Catalog result = TweaksRepository.load(this, requestedProvider, requestedSection, force);
                main.post(() -> {
                    if (provider != requestedProvider || !section.equals(requestedSection)) {
                        return;
                    }
                    catalog.clear();
                    catalog.addAll(result.packs());
                    notice = result.notice();
                    progress.setVisibility(View.GONE);
                    status.setText(catalog.size() + " packs");
                    renderCatalog();
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (provider != requestedProvider || !section.equals(requestedSection)) {
                        return;
                    }
                    progress.setVisibility(View.GONE);
                    status.setText("Catalog failed");
                    notice = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    renderCatalog();
                });
            }
        });
    }

    private void renderCatalog() {
        if (list == null) {
            return;
        }
        list.removeAllViews();
        if (!notice.isBlank()) {
            TextView noticeView = Ui.body(this, notice);
            noticeView.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            noticeView.setBackground(Ui.outlined(Color.rgb(22, 17, 29), Color.rgb(76, 56, 94), 14, this));
            list.addView(noticeView, Ui.matchWrap(this, 12));
        }
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        String currentCategory = null;
        int visible = 0;
        for (TweakPack pack : catalog) {
            String haystack = (pack.name + " " + pack.description + " " + pack.category).toLowerCase(Locale.ROOT);
            if (!query.isBlank() && !haystack.contains(query)) {
                continue;
            }
            if (!pack.category.equals(currentCategory)) {
                currentCategory = pack.category;
                TextView heading = Ui.title(this, currentCategory, 18);
                heading.setPadding(Ui.dp(this, 2), Ui.dp(this, 10), 0, Ui.dp(this, 8));
                list.addView(heading);
            }
            list.addView(packCard(pack));
            visible++;
        }
        if (visible == 0) {
            TextView empty = Ui.body(this, query.isBlank() ? "No public GitHub catalog is available for this section." : "No packs match the search.");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(this, 20), Ui.dp(this, 50), Ui.dp(this, 20), Ui.dp(this, 50));
            list.addView(empty);
        }
        updateSelectionCount();
    }

    private View packCard(TweakPack pack) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        CheckBox check = new CheckBox(this);
        check.setText(pack.name);
        check.setTextColor(Ui.TEXT);
        check.setTextSize(16);
        check.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        check.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Ui.PURPLE, Color.rgb(108, 102, 118)}
        ));
        check.setChecked(currentSelection().containsKey(pack.key()));
        content.addView(check);

        TextView description = Ui.body(this, pack.description.isBlank() ? "No description supplied upstream." : pack.description);
        LinearLayout.LayoutParams descriptionParams = Ui.matchWrap(this, 0);
        descriptionParams.leftMargin = Ui.dp(this, 34);
        content.addView(description, descriptionParams);

        if (!pack.conflicts.isEmpty()) {
            TextView conflicts = Ui.body(this, "Conflicts: " + String.join(", ", pack.conflicts));
            conflicts.setTextColor(Color.rgb(255, 132, 153));
            LinearLayout.LayoutParams conflictParams = Ui.matchWrap(this, 0);
            conflictParams.leftMargin = Ui.dp(this, 34);
            conflictParams.topMargin = Ui.dp(this, 6);
            content.addView(conflicts, conflictParams);
        }

        if (pack.sourceOnly) {
            TextView source = Ui.body(this, "Source export • upstream build required");
            source.setTextColor(Color.rgb(201, 148, 255));
            LinearLayout.LayoutParams sourceParams = Ui.matchWrap(this, 0);
            sourceParams.leftMargin = Ui.dp(this, 34);
            sourceParams.topMargin = Ui.dp(this, 6);
            content.addView(source, sourceParams);
        }

        check.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                List<TweakPack> conflicts = activeConflicts(pack);
                if (!conflicts.isEmpty()) {
                    button.setChecked(false);
                    new AlertDialog.Builder(this)
                            .setTitle("Pack conflict")
                            .setMessage(pack.name + " conflicts with " + names(conflicts) + ". Add it anyway?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Add anyway", (dialog, which) -> {
                                currentSelection().put(pack.key(), pack);
                                check.setOnCheckedChangeListener(null);
                                check.setChecked(true);
                                check.setOnCheckedChangeListener((innerButton, innerChecked) -> toggle(pack, innerChecked));
                                updateSelectionCount();
                            })
                            .show();
                } else {
                    currentSelection().put(pack.key(), pack);
                    updateSelectionCount();
                }
            } else {
                currentSelection().remove(pack.key());
                updateSelectionCount();
            }
        });

        return Ui.card(this, content);
    }

    private void toggle(TweakPack pack, boolean checked) {
        if (checked) {
            currentSelection().put(pack.key(), pack);
        } else {
            currentSelection().remove(pack.key());
        }
        updateSelectionCount();
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
        List<String> names = new ArrayList<>();
        for (TweakPack pack : packs) {
            names.add(pack.name);
        }
        return String.join(", ", names);
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
        if (selected.isEmpty()) {
            return;
        }
        build.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        TweakProvider requestedProvider = provider;
        String requestedSection = section;
        executor.execute(() -> {
            try {
                PackAssembler.BuildResult result = PackAssembler.build(
                        this,
                        requestedProvider,
                        requestedSection,
                        selected,
                        (message, current, total) -> main.post(() -> status.setText(message + " • " + current + "/" + total))
                );
                main.post(() -> {
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
                        message += ". Source not found for: " + String.join(", ", result.missing());
                    }
                    status.setText(message);
                });
            } catch (Exception error) {
                main.post(() -> {
                    progress.setVisibility(View.GONE);
                    build.setEnabled(true);
                    String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    status.setText("Build failed");
                    new AlertDialog.Builder(this)
                            .setTitle("Could not build pack")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CREATE_PACK && resultCode == RESULT_OK && data != null && data.getData() != null && pendingExport != null) {
            Uri destination = data.getData();
            File source = pendingExport;
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
                    main.post(() -> {
                        status.setText("Pack saved");
                        Toast.makeText(this, "Pack exported", Toast.LENGTH_LONG).show();
                    });
                } catch (Exception error) {
                    main.post(() -> new AlertDialog.Builder(this)
                            .setTitle("Export failed")
                            .setMessage(error.getMessage())
                            .setPositiveButton("OK", null)
                            .show());
                }
            });
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showCredits() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), 0);

        TextView subtitle = Ui.body(this, provider.subtitle);
        subtitle.setTextColor(Ui.PURPLE);
        content.addView(subtitle, Ui.matchWrap(this, 12));

        TextView text = Ui.body(this, provider.credits + "\n\nIndividual pack credits and license terms are read from the upstream projects. NullForge Studio does not remove or replace upstream attribution.");
        content.addView(text, Ui.matchWrap(this, 16));

        Button site = Ui.button(this, "Open " + provider.label);
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
        } catch (Exception error) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_LONG).show();
        }
    }
}
