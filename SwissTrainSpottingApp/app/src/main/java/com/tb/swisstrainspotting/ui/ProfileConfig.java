package com.tb.swisstrainspotting.ui;

import android.content.Context;

import com.tb.swisstrainspotting.onnx.ModelProfile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Immutable, app-side presentation config for a specialized classifier profile.
 *
 * <p>This class is <em>not</em> Python metadata. It carries purely Android-facing text
 * — domain display name and out-of-scope prefix strings — that control how conditional
 * (non-direct) classification messages are composed in the UI. Written to assets as
 * {@code <profileId>_profile_config.json} by hand or automation between training profiles.
 *
 * <p>Use alongside {@link ModelProfile}, which describes the model artifacts themselves.
 * Together they answer two questions: <em>"which model does inference?"</em> (ModelProfile)
 * and <em>"how do we phrase the result when the generic classifier says this image is out of scope?"</em>
 * (ProfileConfig).
 */
public final class ProfileConfig {

    private static final String CONFIG_SUFFIX = "_profile_config.json";

    private final String profileId;
    private final String domainDisplayName;
    private final String outOfScopePrefix;

    private ProfileConfig(String profileId, String domainDisplayName, String outOfScopePrefix) {
        this.profileId = profileId != null ? profileId : "";
        this.domainDisplayName = domainDisplayName != null ? domainDisplayName : "";
        this.outOfScopePrefix = outOfScopePrefix != null ? outOfScopePrefix : "";
    }

    public String getProfileId() {
        return profileId;
    }

    public String getDomainDisplayName() {
        return domainDisplayName;
    }

    public String getOutOfScopePrefix() {
        return outOfScopePrefix;
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
            String outOfScopePrefix = json.optString("out_of_scope_prefix", "");
            return new ProfileConfig(id, displayName, outOfScopePrefix);
        } catch (IOException e) {
            throw new IOException("Failed to load profile config: " + assetPath, e);
        }
    }
}
