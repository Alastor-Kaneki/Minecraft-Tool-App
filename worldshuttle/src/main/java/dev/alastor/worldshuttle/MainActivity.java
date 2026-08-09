package dev.alastor.worldshuttle;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int REQUEST_SHIZUKU = 100;
    private static final int ACTION_NONE = 0;

    private TextView status;
    private Button toEditor;
    private Button toMinecraft;
    private IBinder worldService;
    private boolean binding;
    private int pendingAction = ACTION_NONE;
    private Shizuku.UserServiceArgs serviceArgs;

    private final Shizuku.OnBinderReceivedListener binderReceived = () -> runOnUiThread(() -> {
        status("Shizuku connected. Choose a direction.");
        buttons(true);
    });

    private final Shizuku.OnBinderDeadListener binderDead = () -> runOnUiThread(() -> {
        worldService = null;
        binding = false;
        pendingAction = ACTION_NONE;
        status("Shizuku stopped. Start Shizuku and try again.");
        buttons(true);
    });

    private final Shizuku.OnRequestPermissionResultListener permissionResult = (requestCode, grantResult) -> {
        if (requestCode != REQUEST_SHIZUKU) return;
        runOnUiThread(() -> {
            if (grantResult == PERMISSION_GRANTED) {
                bindAndRun();
            } else {
                pendingAction = ACTION_NONE;
                status("Shizuku permission denied.");
                buttons(true);
            }
        });
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            worldService = service;
            binding = false;
            int action = pendingAction;
            pendingAction = ACTION_NONE;
            if (action != ACTION_NONE) runTransfer(action);
            else buttons(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            worldService = null;
            binding = false;
            status("Privileged transfer service disconnected.");
            buttons(true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();

        serviceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(getPackageName(), WorldService.class.getName()))
                .daemon(false)
                .processNameSuffix("world_service")
                .version(5)
                .tag("world-shuttle");

        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        Shizuku.addRequestPermissionResultListener(permissionResult);

        try {
            status(Shizuku.pingBinder()
                    ? "Shizuku detected. Choose a direction."
                    : "Start Shizuku, then return here.");
        } catch (Throwable ignored) {
            status("Waiting for Shizuku.");
        }
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        Shizuku.removeRequestPermissionResultListener(permissionResult);
        super.onDestroy();
    }

    private void requestTransfer(int action) {
        pendingAction = action;
        buttons(false);
        try {
            if (!Shizuku.pingBinder()) {
                pendingAction = ACTION_NONE;
                status("Shizuku is not running.");
                buttons(true);
                return;
            }
            if (Shizuku.isPreV11()) {
                pendingAction = ACTION_NONE;
                status("Shizuku API 11 or newer is required.");
                buttons(true);
                return;
            }
            if (Shizuku.checkSelfPermission() != PERMISSION_GRANTED) {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    pendingAction = ACTION_NONE;
                    status("Permission denied. Enable World Shuttle in Shizuku.");
                    buttons(true);
                } else {
                    status("Requesting Shizuku permission…");
                    Shizuku.requestPermission(REQUEST_SHIZUKU);
                }
                return;
            }
            bindAndRun();
        } catch (Throwable t) {
            pendingAction = ACTION_NONE;
            status("Shizuku error: " + message(t));
            buttons(true);
        }
    }

    private void bindAndRun() {
        if (worldService != null && worldService.pingBinder()) {
            int action = pendingAction;
            pendingAction = ACTION_NONE;
            runTransfer(action);
            return;
        }
        if (binding) return;
        binding = true;
        status("Starting privileged transfer service…");
        try {
            Shizuku.bindUserService(serviceArgs, serviceConnection);
        } catch (Throwable t) {
            binding = false;
            pendingAction = ACTION_NONE;
            status("Could not start transfer service: " + message(t));
            buttons(true);
        }
    }

    private void runTransfer(int action) {
        if (action == ACTION_NONE) {
            buttons(true);
            return;
        }
        final IBinder binder = worldService;
        if (binder == null || !binder.pingBinder()) {
            status("Transfer service unavailable.");
            buttons(true);
            return;
        }

        status(action == WorldService.TRANSACTION_MOVE_TO_EDITOR
                ? "Moving Minecraft worlds to Inventory Editor…"
                : "Moving Inventory Editor worlds back to Minecraft…");

        new Thread(() -> {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            String result;
            try {
                data.writeInterfaceToken(WorldService.DESCRIPTOR);
                if (!binder.transact(action, data, reply, 0)) {
                    throw new IllegalStateException("Binder transaction was rejected");
                }
                reply.readException();
                result = reply.readString();
                if (TextUtils.isEmpty(result)) result = "Transfer complete.";
            } catch (Throwable t) {
                result = "ERROR: " + message(t);
            } finally {
                reply.recycle();
                data.recycle();
            }
            final String finalResult = result;
            runOnUiThread(() -> {
                status(finalResult);
                buttons(true);
            });
        }, "WorldShuttleTransfer").start();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff080808);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(30), dp(22), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("WORLD SHUTTLE", 28, Color.WHITE);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, margins(-1, -2, 0, 0, 0, 6));

        TextView sub = text("Minecraft ↔ Inventory Editor", 16, 0xffc9c9c9);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(sub, margins(-1, -2, 0, 0, 0, 22));

        status = text("Starting…", 15, 0xffe5d6ff);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackgroundColor(0xff171717);
        root.addView(status, margins(-1, -2, 0, 0, 0, 18));

        root.addView(path("MINECRAFT", WorldService.MINECRAFT_WORLDS));
        root.addView(path("INVENTORY EDITOR", WorldService.EDITOR_WORLDS));

        toEditor = button("MOVE MINECRAFT → INVENTORY EDITOR");
        toEditor.setOnClickListener(v -> requestTransfer(WorldService.TRANSACTION_MOVE_TO_EDITOR));
        root.addView(toEditor, margins(-1, dp(60), 0, 22, 0, 10));

        toMinecraft = button("MOVE INVENTORY EDITOR → MINECRAFT");
        toMinecraft.setOnClickListener(v -> requestTransfer(WorldService.TRANSACTION_MOVE_TO_MINECRAFT));
        root.addView(toMinecraft, margins(-1, dp(60), 0, 0, 0, 18));

        root.addView(text(
                "This is a MOVE, not a copy. Existing same-name destination worlds are backed up temporarily; the source is removed only after the destination verifies. Minecraft is force-stopped before transfer.",
                13, 0xff9b9b9b));

        setContentView(scroll);
    }

    private TextView path(String label, String value) {
        TextView v = text(label + "\n" + value, 13, 0xffbcbcbc);
        v.setPadding(dp(14), dp(12), dp(14), dp(12));
        v.setBackgroundColor(0xff101010);
        return v;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundColor(0xff560000);
        return b;
    }

    private TextView text(String value, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setLineSpacing(0, 1.08f);
        return v;
    }

    private LinearLayout.LayoutParams margins(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private void status(String value) {
        if (status != null) status.setText(value);
    }

    private void buttons(boolean enabled) {
        if (toEditor != null) toEditor.setEnabled(enabled);
        if (toMinecraft != null) toMinecraft.setEnabled(enabled);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String message(Throwable t) {
        String value = t.getMessage();
        return TextUtils.isEmpty(value) ? t.getClass().getSimpleName() : value;
    }
}
