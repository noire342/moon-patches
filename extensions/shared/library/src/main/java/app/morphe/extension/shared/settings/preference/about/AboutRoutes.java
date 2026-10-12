// SPDX-License-Identifier: GPL-3.0-only
// Modified 2026-10-12 by Moon Patches contributors: project links and version checking.
// Based on Morphe Patches; see NOTICE for retained Section 7 terms.
package app.morphe.extension.shared.settings.preference.about;

import androidx.annotation.Nullable;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.util.List;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.requests.Route;

class AboutRoutes {
    private static final String PROJECT = "https://github.com/noire342/moon-patches";
    private static final List<MorpheAboutPreference.WebLink> LINKS = List.of(
            new MorpheAboutPreference.WebLink("source", null, PROJECT),
            new MorpheAboutPreference.WebLink("releases", null, PROJECT + "/releases"),
            new MorpheAboutPreference.WebLink("issues", null, PROJECT + "/issues"),
            new MorpheAboutPreference.WebLink("privacy", null, PROJECT + "/blob/main/PRIVACY.md"),
            MorpheAboutPreference.CREDITS_LINK,
            MorpheCreditsDialog.ABOUT_LICENSE
    );
    private static final Route.CompiledRoute VERSION_ROUTE = new Route(Route.Method.GET,
            "/noire342/moon-patches/refs/heads/main/patches-bundle.json").compile();
    @Nullable
    private static volatile String latestPatchesVersion;
    private static volatile long lastCheckedTime;

    static boolean hasFetchedPatchersVersion() {
        return lastCheckedTime != 0 && System.currentTimeMillis() - lastCheckedTime < 5 * 60 * 1000;
    }

    @Nullable
    static String getLatestPatchesVersion() {
        if (hasFetchedPatchersVersion()) return latestPatchesVersion;
        if (!Utils.isNetworkConnected()) return null;
        HttpURLConnection connection = null;
        try {
            connection = Requester.getConnectionFromCompiledRoute("https://raw.githubusercontent.com", VERSION_ROUTE);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            if (connection.getResponseCode() != 200) return null;
            JSONObject json = Requester.parseJSONObjectAndDisconnect(connection);
            String version = json.getString("version");
            if (!version.matches("v?[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?")) return null;
            latestPatchesVersion = version.startsWith("v") ? version.substring(1) : version;
            return latestPatchesVersion;
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not check Moon patches version", ex);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
            lastCheckedTime = System.currentTimeMillis();
        }
    }

    static List<MorpheAboutPreference.WebLink> fetchAboutLinks() {
        return LINKS;
    }
}
