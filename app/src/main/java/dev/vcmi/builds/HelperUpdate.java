package dev.vcmi.builds;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;

/** Public releases of the standalone downloader use an independent, fixed source. */
final class HelperUpdate {
    static final String REPO = "CapnChaosDK/vcmi-builds";
    private static final String TAG_PREFIX = "android-";
    private static final String ASSET = "vcmi-builds.apk";
    private static final long MAX_BYTES = 32L * 1024 * 1024;

    record Release(long version, String digest, long size) {
        String url() {
            return "https://github.com/" + REPO + "/releases/download/" + TAG_PREFIX + version + "/" + ASSET;
        }
    }

    static File apk(Context context) {
        return new File(context.getFilesDir(), "vcmi-builds-update.apk");
    }

    static long version(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) return info.getLongVersionCode();
        return info.versionCode;
    }

    private static int signingFlags() {
        return Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    }

    static PackageInfo installed(Context context) throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), signingFlags());
    }

    private static Signature[] signers(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28)
            return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        return info.signatures;
    }

    static PackageInfo verify(Context context, File file, PackageInfo current) throws Exception {
        PackageInfo update = context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), signingFlags());
        ValidationPlan.require(update != null && current.packageName.equals(update.packageName),
            "The APK does not belong to VCMI Builds.");
        Signature[] expected = signers(current);
        Signature[] actual = signers(update);
        ValidationPlan.require(expected != null && expected.length > 0 && actual != null &&
            new HashSet<>(Arrays.asList(expected)).equals(new HashSet<>(Arrays.asList(actual))),
            "The APK signing certificate differs from this installation.");
        ValidationPlan.require(version(update) > version(current), "This update is already installed or is older.");
        return update;
    }

    static Release release(JSONObject data, long installedVersion) throws Exception {
        String tag = data.getString("tag_name");
        ValidationPlan.require(tag.matches(TAG_PREFIX + "[1-9][0-9]{0,9}") &&
            !data.optBoolean("draft") && !data.optBoolean("prerelease"), "Unexpected VCMI Builds release.");
        long version = Long.parseLong(tag.substring(TAG_PREFIX.length()));
        if (version <= installedVersion) return null;
        JSONArray assets = data.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (!ASSET.equals(asset.optString("name"))) continue;
            String digest = asset.optString("digest");
            long size = asset.getLong("size");
            ValidationPlan.require("uploaded".equals(asset.optString("state")) &&
                digest.matches("sha256:[a-f0-9]{64}") && size > 0 && size <= MAX_BYTES,
                "The release APK is missing valid integrity metadata. Try again later.");
            return new Release(version, digest.substring(7), size);
        }
        throw new java.io.IOException("The latest release has no VCMI Builds APK.");
    }

    static Release latest(long installedVersion) throws Exception {
        JSONObject release = GitHubClient.json("https://api.github.com/repos/" + REPO + "/releases/latest", "");
        return release(release, installedVersion);
    }

    static void download(Context context, Release release) throws Exception {
        File target = apk(context);
        File staged = File.createTempFile("helper-update-", ".apk", context.getFilesDir());
        try {
            HttpURLConnection connection = GitHubClient.connect(release.url(), "", "application/octet-stream");
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(staged)) {
                byte[] buffer = new byte[65536];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    ValidationPlan.require(!Thread.currentThread().isInterrupted(), "Download interrupted.");
                    total += count;
                    ValidationPlan.require(total <= release.size(), "The APK exceeds the expected size.");
                    output.write(buffer, 0, count);
                }
                ValidationPlan.require(total == release.size(), "The APK download is incomplete. Retry the download.");
            } finally {
                connection.disconnect();
            }
            ValidationPlan.require(release.digest().equals(CandidateStore.digest(staged)), "The APK checksum does not match GitHub.");
            PackageInfo update = verify(context, staged, installed(context));
            ValidationPlan.require(version(update) == release.version(), "The APK version does not match the release.");
            ValidationPlan.require(!Thread.currentThread().isInterrupted(), "Download interrupted.");
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            staged.delete();
        }
    }
}
