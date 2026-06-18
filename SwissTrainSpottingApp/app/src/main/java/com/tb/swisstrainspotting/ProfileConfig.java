package com.tb.swisstrainspotting;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads app-side profile configuration from assets.
 *
 * <p>Each specialized profile has a config file at
 * {@code <profileId>_profile_config.json} containing:
 * <ul>
 *   <li>{@code "profile_id"} — machine-readable identifier</li>
 *   <li>{@code "domain_display_name"} — human-readable domain label used in conditional UI messaging</li>
 * </ul>
 */
public final class ProfileConfig {

    private static final String CONFIG_SUFFIX = "_profile_config.json";

    private final String profileId;
    private final String domainDisplayName;

    private ProfileConfig(String profileId, String domainDisplayName) {
        this.profileId = profileId != null ? profileId : "";
        this.domainDisplayName = domainDisplayName != null ? domainDisplayName : "";
    }

    public String getProfileId() {
        return profileId;
    }

    public String getDomainDisplayName() {
        return domainDisplayName;
    }

    /** Build the asset path from a profile ID using the naming convention. */
    public static String buildAssetPath(String profileId) {
        return profileId + CONFIG_SUFFIX;
    }

    /**
     * Load profile config from assets by profile ID.
     *
     * @throws IOException if the file is missing or unreadable
     * @throws JSONException if the JSON schema is invalid
     */
    public static ProfileConfig load(Context context, String profileId) throws IOException, JSONException {
        if (context == null || profileId == null || profileId.isEmpty()) {
            throw new IllegalArgumentException("context and profileId must not be null/empty");
        }

        String assetPath = buildAssetPath(profileId);

        try (InputStream is = context.getAssets().open(assetPath)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONObject json = new JSONObject(sb.toString());
            String id = json.optString("profile_id", "");
            String displayName = json.optString("domain_display_name", "");
            return new ProfileConfig(id, displayName);
        } catch (IOException e) {
            throw new IOException("Failed to load profile config: " + assetPath, e);
        }
    }
}
