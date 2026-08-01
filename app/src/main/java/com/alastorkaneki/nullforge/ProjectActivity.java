package com.alastorkaneki.nullforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ProjectActivity extends Activity {
    private String edition;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        edition = getIntent().getStringExtra("edition");
        if (edition == null) {
            edition = "BEDROCK";
        }
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
        scroll.setBackground(Ui.screenBackground());
        scroll.setClipToPadding(false);
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
        titles.addView(Ui.eyebrow(this, edition.equals("JAVA") ? "Java Edition" : "Bedrock Edition"));
        titles.addView(Ui.title(this, "Project Workspace", 27));
        LinearLayout.LayoutParams titleParams = Ui.weight(1);
        titleParams.leftMargin = Ui.dp(this, 12);
        header.addView(titles, titleParams);
        root.addView(header, Ui.matchWrap(this, 18));

        TextView intro = Ui.body(this, edition.equals("JAVA")
                ? "Create local Java resource-pack and data-pack projects with starter metadata ready for cached assets."
                : "Create local Bedrock resource-pack, behavior-pack, and paired add-on projects with fresh manifests.");
        root.addView(Ui.listCard(this, intro));

        root.addView(projectType("Resource Pack", edition.equals("JAVA") ? Project.Kind.JAVA_RESOURCE : Project.Kind.BEDROCK_RESOURCE, true));
        root.addView(projectType(edition.equals("JAVA") ? "Data Pack" : "Behavior Pack", edition.equals("JAVA") ? Project.Kind.JAVA_DATA : Project.Kind.BEDROCK_BEHAVIOR, false));
        if (edition.equals("BEDROCK")) {
            root.addView(projectType("Paired Add-On", Project.Kind.BEDROCK_ADDON, false));
        } else {
            root.addView(projectType("Combined Resource + Data", Project.Kind.JAVA_COMBINED, false));
        }

        return scroll;
    }

    private View projectType(String label, Project.Kind kind, boolean primary) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(Ui.title(this, label, 20), Ui.matchWrap(this, 8));
        content.addView(Ui.body(this, "Creates a project folder in app storage and adds the required starter metadata."), Ui.matchWrap(this, 14));
        Button create = primary ? Ui.primaryButton(this, "Create project") : Ui.button(this, "Create project");
        create.setOnClickListener(view -> prompt(kind, label));
        content.addView(create, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));
        return Ui.card(this, content);
    }

    private void prompt(Project.Kind kind, String label) {
        EditText name = Ui.input(this, label + " name");
        name.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));

        new AlertDialog.Builder(this)
                .setTitle("Create " + label)
                .setView(name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> create(kind, name.getText().toString()))
                .show();
    }

    private void create(Project.Kind kind, String requestedName) {
        String name = requestedName.trim().isEmpty() ? "Untitled Project" : requestedName.trim();
        String folderName = name.replaceAll("[^A-Za-z0-9._ -]", "").trim().replace(' ', '_');
        if (folderName.isEmpty()) {
            folderName = "Untitled_Project";
        }
        File projects = new File(getFilesDir(), "projects");
        File root = new File(projects, UUID.randomUUID() + "-" + folderName);
        root.mkdirs();
        try {
            if (edition.equals("JAVA")) {
                write(new File(root, "pack.mcmeta"), "{\n  \"pack\": {\n    \"pack_format\": 64,\n    \"description\": \"" + escape(name) + "\"\n  }\n}\n");
            } else if (kind == Project.Kind.BEDROCK_ADDON) {
                File resource = new File(root, "resource_pack");
                File behavior = new File(root, "behavior_pack");
                resource.mkdirs();
                behavior.mkdirs();
                writeManifest(new File(resource, "manifest.json"), name + " Resources", "resources");
                writeManifest(new File(behavior, "manifest.json"), name + " Behavior", "data");
            } else {
                writeManifest(new File(root, "manifest.json"), name, kind == Project.Kind.BEDROCK_RESOURCE ? "resources" : "data");
            }
            write(new File(root, "project.json"), new org.json.JSONObject()
                    .put("name", name)
                    .put("edition", edition)
                    .put("kind", kind.name())
                    .put("createdAt", System.currentTimeMillis())
                    .toString(2));
            Toast.makeText(this, "Created " + name, Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            new AlertDialog.Builder(this)
                    .setTitle("Project creation failed")
                    .setMessage(error.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void writeManifest(File file, String name, String type) throws Exception {
        org.json.JSONObject root = new org.json.JSONObject();
        root.put("format_version", 2);
        org.json.JSONObject header = new org.json.JSONObject();
        header.put("name", name);
        header.put("description", "Created with NullForge Studio");
        header.put("uuid", UUID.randomUUID().toString());
        header.put("version", new org.json.JSONArray(new int[]{1, 0, 0}));
        header.put("min_engine_version", new org.json.JSONArray(new int[]{1, 21, 0}));
        root.put("header", header);
        org.json.JSONObject module = new org.json.JSONObject();
        module.put("type", type);
        module.put("uuid", UUID.randomUUID().toString());
        module.put("version", new org.json.JSONArray(new int[]{1, 0, 0}));
        root.put("modules", new org.json.JSONArray().put(module));
        write(file, root.toString(2));
    }

    private void write(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
