package dev.vcmi.builds;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

final class HelperUpdateTests {
    static void run() throws Exception {
        String digest = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        JSONObject apk = new JSONObject().put("name", "vcmi-builds.apk").put("size", 1234)
            .put("state", "uploaded").put("digest", "sha256:" + digest);
        JSONObject release = new JSONObject().put("tag_name", "android-12")
            .put("assets", new JSONArray().put(apk));
        HelperUpdate.Release update = HelperUpdate.release(release, 9);
        check(update.version() == 12 && update.size() == 1234 && update.digest().equals(digest));
        check(update.url().equals("https://github.com/CapnChaosDK/vcmi-builds/releases/download/android-12/vcmi-builds.apk"));
        check(HelperUpdate.release(release, 12) == null);
        check(HelperUpdate.release(release, 13) == null);
        reject(release.put("prerelease", true));
        reject(release.put("prerelease", false).put("tag_name", "android-12/other"));
        release.put("tag_name", "android-12");
        apk.put("digest", "sha256:invalid");
        reject(release);
        apk.put("digest", "sha256:" + digest).put("size", 1L << 32);
        reject(release);
        apk.put("size", 1234).put("state", "new");
        reject(release);
        apk.put("name", "thor-candidate-arm64.apk");
        reject(release);
    }

    private static void reject(JSONObject release) throws Exception {
        try {
            HelperUpdate.release(release, 9);
            throw new AssertionError("Invalid update metadata was accepted");
        } catch (IOException expected) {
            // Expected rejection.
        }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected update metadata");
    }
}
