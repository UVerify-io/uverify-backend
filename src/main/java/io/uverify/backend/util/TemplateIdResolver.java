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

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;


public final class TemplateIdResolver {

    public static final String TEMPLATE_ID_KEY = "uv_tid";
    public static final String LEGACY_TEMPLATE_ID_KEY = "uverify_template_id";

    private TemplateIdResolver() {
    }

    public static String resolveTemplateId(JsonNode extraRoot) {
        if (extraRoot == null) {
            return null;
        }
        JsonNode shortKey = extraRoot.get(TEMPLATE_ID_KEY);
        if (shortKey != null && shortKey.isTextual()) {
            return shortKey.asText();
        }
        JsonNode legacyKey = extraRoot.get(LEGACY_TEMPLATE_ID_KEY);
        if (legacyKey != null && legacyKey.isTextual()) {
            return legacyKey.asText();
        }
        return null;
    }

    public static String resolveTemplateId(Map<String, Object> extraFields) {
        if (extraFields == null) {
            return null;
        }
        Object shortKey = extraFields.get(TEMPLATE_ID_KEY);
        if (shortKey instanceof String value) {
            return value;
        }
        Object legacyKey = extraFields.get(LEGACY_TEMPLATE_ID_KEY);
        if (legacyKey instanceof String value) {
            return value;
        }
        return null;
    }
}
