/*
 * UVerify Backend
 * Copyright (C) 2025 Fabian Bormann
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package io.uverify.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OgDataFactory {

    public record OgData(String title, String description, String imageUrl) {
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");
    private static final String URL_PARAM_KEY_PREFIX = "uv_url_";

    private OgDataFactory() {
    }

    public static OgData build(Map<String, Object> metadata, Map<String, String> queryParams,
                               String templateId, String frontendUrl) {
        Map<String, String> verifiedParams = verifyParams(metadata, queryParams);
        String title = substituteOrNull(asText(metadata.get("uv_og_title")), verifiedParams);
        String description = substituteOrNull(asText(metadata.get("uv_og_desc")), verifiedParams);
        if (title == null) {
            title = defaultTitle(metadata, templateId, verifiedParams);
        }
        if (description == null) {
            description = defaultDescription(metadata, templateId);
        }
        return new OgData(title, description, imageUrl(metadata, templateId, frontendUrl));
    }

    /**
     * Only values whose SHA-256 matches the on-chain uv_url_* commitment may
     * appear in OG output. Anything else would let arbitrary URLs put false
     * text on a real certificate's preview.
     */
    public static Map<String, String> verifyParams(Map<String, Object> metadata, Map<String, String> queryParams) {
        Map<String, String> verified = new LinkedHashMap<>();
        for (Map.Entry<String, String> param : queryParams.entrySet()) {
            Object commitment = metadata.get(URL_PARAM_KEY_PREFIX + param.getKey());
            if (commitment instanceof String expected && sha256Hex(param.getValue()).equalsIgnoreCase(expected)) {
                verified.put(param.getKey(), cropSalt(param.getValue()));
            }
        }
        return verified;
    }

    public static String cropSalt(String value) {
        int index = value.lastIndexOf('~');
        return index > 0 ? value.substring(0, index) : value;
    }

    private static String substituteOrNull(String template, Map<String, String> verifiedParams) {
        if (template == null || template.isBlank()) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = verifiedParams.get(matcher.group(1));
            if (value == null) {
                return null;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String defaultTitle(Map<String, Object> metadata, String templateId,
                                       Map<String, String> verifiedParams) {
        if ("diploma".equals(templateId)) {
            String certificateTitle = asText(metadata.get("title"));
            String recipientName = verifiedParams.get("name");
            if (certificateTitle != null && recipientName != null) {
                return certificateTitle + " · " + recipientName;
            }
            if (certificateTitle != null) {
                return certificateTitle;
            }
        }
        return "Blockchain-verified certificate";
    }

    private static String defaultDescription(Map<String, Object> metadata, String templateId) {
        if ("diploma".equals(templateId)) {
            String issuer = asText(metadata.get("issuer"));
            if (issuer != null) {
                return "Issued by " + issuer + ". Anchored on the Cardano blockchain.";
            }
        }
        return "Anchored on the Cardano blockchain. Open the link to verify the original document.";
    }

    private static String imageUrl(Map<String, Object> metadata, String templateId, String frontendUrl) {
        String override = asText(metadata.get("uv_og_img"));
        if (override != null && (override.startsWith("https://") || override.startsWith("http://"))) {
            return override;
        }
        // uv_tid is on-chain data: only path-safe ids may be interpolated
        String imageName = templateId != null && templateId.matches("[A-Za-z0-9_-]+")
                ? templateId : "default";
        return frontendUrl + "/og/" + imageName + ".png";
    }

    private static String asText(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
