package dev.vcmi.builds;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class HelperUpdateActivity extends Activity {
    private static final int UNKNOWN_SOURCES = 51, INSTALL = 52;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button check, download, install;
    private HelperUpdate.Release release;
    private boolean busy, waitingForAndroid;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        waitingForAndroid = state != null && state.getBoolean("waitingForAndroid");
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (18 * getResources().getDisplayMetrics().density);
        page.setPadding(padding, padding, padding, padding);
        scroll.addView(page);
        TextView heading = new TextView(this);
        heading.setText("Update VCMI Builds");
        heading.setTextSize(24);
        page.addView(heading);
        TextView source = new TextView(this);
        source.setText("Source: " + HelperUpdate.REPO + " on GitHub");
        page.addView(source);
        check = button(page, "Check for update", this::checkForUpdate);
        download = button(page, "Download update", this::downloadUpdate);
        install = button(page, "Install downloaded update", this::installUpdate);
        status = new TextView(this);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setPadding(0, padding, 0, 0);
        page.addView(status);
        setContentView(scroll);
        status.setText("Check GitHub for a newer version of the downloader.");
        updateButtons();
    }

    private Button button(LinearLayout page, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        page.addView(button);
        return button;
    }

    private void updateButtons() {
        boolean available = !busy && !waitingForAndroid;
        check.setEnabled(available);
        download.setEnabled(available && release != null);
        install.setEnabled(available && HelperUpdate.apk(this).isFile());
    }

    private <T> void runTask(String inProgress, Callable<T> task, Consumer<T> onSuccess) {
        if (busy || waitingForAndroid) return;
        busy = true;
        status.setText(inProgress);
        updateButtons();
        worker.execute(() -> {
            try {
                T result = task.call();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    onSuccess.accept(result);
                    updateButtons();
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    status.setText("Update failed. " + failure.getMessage());
                    updateButtons();
                });
            }
        });
    }

    private void checkForUpdate() {
        release = null;
        runTask("Checking GitHub…", () -> HelperUpdate.latest(HelperUpdate.version(HelperUpdate.installed(this))), latest -> {
            release = latest;
            status.setText(latest == null ? "VCMI Builds is up to date." :
                "Build " + latest.version() + " is available.");
        });
    }

    private void downloadUpdate() {
        HelperUpdate.Release selected = release;
        if (selected == null) return;
        runTask("Downloading and verifying update… Keep this screen open.", () -> {
            HelperUpdate.download(this, selected);
            return null;
        }, ignored -> {
            release = null;
            status.setText("Update verified. Tap Install downloaded update to open Android’s installer.");
        });
    }

    private void installUpdate() {
        runTask("Checking downloaded update…", () -> {
            PackageInfo current = HelperUpdate.installed(this);
            try {
                return HelperUpdate.verify(this, HelperUpdate.apk(this), current);
            } catch (Exception invalid) {
                HelperUpdate.apk(this).delete();
                throw invalid;
            }
        }, update -> openInstaller(update.versionName));
    }

    private void openInstaller(String versionName) {
        try {
            waitingForAndroid = true;
            if (!getPackageManager().canRequestPackageInstalls()) {
                status.setText("Allow installs from VCMI Builds in Android settings, then return.");
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())), UNKNOWN_SOURCES);
                return;
            }
            Uri apk = Uri.parse("content://dev.vcmi.builds.apk/vcmi-builds-update.apk");
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(apk)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("VCMI Builds update", apk));
            status.setText("Confirm the update to " + versionName + " in Android’s installer.");
            startActivityForResult(intent, INSTALL);
        } catch (Exception failure) {
            waitingForAndroid = false;
            status.setText("Could not open Android’s installer. " + failure.getMessage());
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        waitingForAndroid = false;
        if (request == UNKNOWN_SOURCES && getPackageManager().canRequestPackageInstalls()) {
            installUpdate();
            return;
        }
        if (request == UNKNOWN_SOURCES)
            status.setText("Android installation permission was declined. You can retry.");
        else if (request == INSTALL)
            status.setText(result == RESULT_OK ? "Update installed. Reopen VCMI Builds." :
                "Installation cancelled or failed. You can retry.");
        updateButtons();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("waitingForAndroid", waitingForAndroid);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
