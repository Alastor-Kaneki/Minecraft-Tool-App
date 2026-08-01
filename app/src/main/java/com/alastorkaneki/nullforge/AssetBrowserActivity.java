package com.alastorkaneki.nullforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AssetBrowserActivity extends Activity {
    private final Set<String> selections = new LinkedHashSet<>();
    private File root;
    private File current;
    private String edition;
    private String label;
    private LinearLayout list;
    private TextView path;
    private TextView count;
    private EditText search;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String rootPath = getIntent().getStringExtra("root");
        edition = getIntent().getStringExtra("edition");
        label = getIntent().getStringExtra("label");
        if (rootPath == null || edition == null) {
            finish();
            return;
        }
        root = new File(rootPath);
        current = root;
        Ui.immersive(this);
        setContentView(screen());
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    @Override
    public void onBackPressed() {
        if (!current.equals(root)) {
            current = current.getParentFile();
            render();
            return;
        }
        super.onBackPressed();
    }

    private View screen() {
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackground(Ui.screenBackground());
        rootLayout.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 12));

        LinearLayout header = Ui.row(this);
        Button back = Ui.button(this, "‹");
        back.setOnClickListener(view -> onBackPressed());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.eyebrow(this, edition + " Assets"));
        titles.addView(Ui.title(this, label == null ? "Asset Browser" : label, 24));
        path = Ui.body(this, "/");
        titles.addView(path);
        LinearLayout.LayoutParams titleParams = Ui.weight(1);
        titleParams.leftMargin = Ui.dp(this, 12);
        header.addView(titles, titleParams);
        rootLayout.addView(header, Ui.matchWrap(this, 12));

        search = Ui.input(this, "Filter this folder");
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                render();
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        rootLayout.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));

        LinearLayout actions = Ui.row(this);
        Button selectFolder = Ui.primaryButton(this, "Select folder");
        selectFolder.setOnClickListener(view -> toggleSelection(relative(current)));
        actions.addView(selectFolder, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        Button clear = Ui.button(this, "Clear");
        clear.setOnClickListener(view -> {
            selections.clear();
            render();
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        clearParams.leftMargin = Ui.dp(this, 8);
        actions.addView(clear, clearParams);
        rootLayout.addView(actions, Ui.matchWrap(this, 10));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        scroll.addView(list);
        rootLayout.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout footer = Ui.row(this);
        footer.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        footer.setBackground(Ui.outlined(Ui.PANEL, Ui.BORDER, 8, this));
        count = Ui.title(this, "0 selected", 15);
        footer.addView(count, Ui.weight(1));
        Button importButton = Ui.primaryButton(this, "Add to project");
        importButton.setOnClickListener(view -> chooseProject());
        footer.addView(importButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 50)));
        rootLayout.addView(footer);
        return rootLayout;
    }

    private void render() {
        if (list == null || current == null) {
            return;
        }
        path.setText("/" + relative(current));
        count.setText(selections.size() + " selected");
        list.removeAllViews();
        File[] children = current.listFiles();
        if (children == null) {
            list.addView(Ui.body(this, "This folder cannot be read."));
            return;
        }
        Arrays.sort(children, Comparator.comparing(File::isFile).thenComparing(file -> file.getName().toLowerCase(Locale.ROOT)));
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (File child : children) {
            if (!query.isEmpty() && !child.getName().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            list.addView(row(child));
            shown++;
        }
        if (shown == 0) {
            TextView empty = Ui.body(this, query.isEmpty() ? "This folder is empty." : "No matching files in this folder.");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 40), 0, 0);
            list.addView(empty);
        }
    }

    private View row(File file) {
        String relative = relative(file);
        LinearLayout content = Ui.row(this);
        CheckBox check = new CheckBox(this);
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.PURPLE));
        check.setChecked(isSelected(relative));
        check.setOnClickListener(view -> toggleSelection(relative));
        content.addView(check, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(Ui.title(this, (file.isDirectory() ? "▸ " : "") + file.getName(), 16));
        details.addView(Ui.body(this, file.isDirectory() ? childSummary(file) : formatSize(file.length())));
        content.addView(details, Ui.weight(1));

        Button open = Ui.button(this, file.isDirectory() ? "Open" : "View");
        open.setOnClickListener(view -> {
            if (file.isDirectory()) {
                current = file;
                search.setText("");
                render();
            } else {
                preview(file);
            }
        });
        content.addView(open, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 46)));
        return Ui.listCard(this, content);
    }

    private void toggleSelection(String relative) {
        String value = relative.isEmpty() ? "." : relative;
        if (selections.contains(value)) {
            selections.remove(value);
        } else {
            selections.removeIf(existing -> existing.startsWith(value + "/"));
            selections.add(value);
        }
        render();
    }

    private boolean isSelected(String relative) {
        if (selections.contains(relative)) {
            return true;
        }
        for (String selected : selections) {
            if (selected.equals(".") || relative.startsWith(selected + "/")) {
                return true;
            }
        }
        return false;
    }

    private void chooseProject() {
        if (selections.isEmpty()) {
            Toast.makeText(this, "Select at least one file or folder.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<ProjectAssetImporter.ProjectTarget> projects = ProjectAssetImporter.listProjects(getFilesDir(), edition.toUpperCase(Locale.ROOT));
        if (projects.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No matching projects")
                    .setMessage("Create a " + edition + " project first, then return to the asset browser.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        String[] labels = new String[projects.size()];
        for (int index = 0; index < projects.size(); index++) {
            ProjectAssetImporter.ProjectTarget project = projects.get(index);
            labels[index] = project.name + "\n" + project.kind.replace('_', ' ');
        }
        new AlertDialog.Builder(this)
                .setTitle("Add selected assets to")
                .setItems(labels, (dialog, which) -> importInto(projects.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void importInto(ProjectAssetImporter.ProjectTarget project) {
        try {
            Set<String> normalized = new LinkedHashSet<>();
            for (String value : selections) {
                normalized.add(".".equals(value) ? "" : value);
            }
            ProjectAssetImporter.Result result = ProjectAssetImporter.importSelections(root, edition, normalized, project);
            new AlertDialog.Builder(this)
                    .setTitle("Assets added")
                    .setMessage(result.copied + " files copied\n" + result.overwritten + " existing files replaced\n" + result.skipped + " incompatible or missing files skipped")
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception error) {
            new AlertDialog.Builder(this)
                    .setTitle("Import failed")
                    .setMessage(error.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void preview(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
                ImageView image = new ImageView(this);
                image.setAdjustViewBounds(true);
                image.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()));
                image.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
                new AlertDialog.Builder(this).setTitle(file.getName()).setView(image).setPositiveButton("Close", null).show();
                return;
            }
            if (isTextFile(lower) && file.length() <= 1024 * 1024) {
                TextView text = new TextView(this);
                text.setText(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                text.setTextColor(Ui.TEXT);
                text.setTextSize(12);
                text.setTypeface(android.graphics.Typeface.MONOSPACE);
                text.setTextIsSelectable(true);
                text.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
                text.setBackgroundColor(Ui.PANEL);
                ScrollView scroll = new ScrollView(this);
                scroll.addView(text);
                new AlertDialog.Builder(this).setTitle(file.getName()).setView(scroll).setPositiveButton("Close", null).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(file.getName())
                    .setMessage(relative(file) + "\n" + formatSize(file.length()))
                    .setPositiveButton("Close", null)
                    .show();
        } catch (Exception error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String relative(File file) {
        try {
            String rootPath = root.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (rootPath.equals(filePath)) {
                return "";
            }
            return filePath.substring(rootPath.length() + 1).replace(File.separatorChar, '/');
        } catch (Exception error) {
            return file.getName();
        }
    }

    private String childSummary(File directory) {
        File[] children = directory.listFiles();
        return children == null ? "Folder" : children.length + (children.length == 1 ? " item" : " items");
    }

    private boolean isTextFile(String lower) {
        return lower.endsWith(".json") || lower.endsWith(".mcmeta") || lower.endsWith(".txt") || lower.endsWith(".lang")
                || lower.endsWith(".md") || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".mcfunction")
                || lower.endsWith(".fsh") || lower.endsWith(".vsh") || lower.endsWith(".properties") || lower.endsWith(".xml");
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB"};
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return new DecimalFormat("0.##").format(value) + " " + units[unit];
    }
}
