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

import io.uverify.backend.util.OgDataFactory.OgData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OgDataFactoryTest {

    private static final String FRONTEND = "https://app.uverify.io";

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void saltIsCroppedAtTheLastTilde() {
        assertEquals("Jane Doe", OgDataFactory.cropSalt("Jane Doe~x7Rk2p"));
        assertEquals("Jane~Doe", OgDataFactory.cropSalt("Jane~Doe~x7Rk2p"));
        assertEquals("Jane Doe", OgDataFactory.cropSalt("Jane Doe"));
        assertEquals("~leading", OgDataFactory.cropSalt("~leading"));
    }

    @Test
    void verifiedPlaceholderIsSubstitutedCropped() throws Exception {
        Map<String, Object> metadata = Map.of(
                "uv_og_title", "Certified Cardano Developer",
                "uv_og_desc", "Awarded to {name} by Cardano Academy",
                "uv_url_name", sha256("Jane Doe~x7Rk2p"));
        OgData og = OgDataFactory.build(metadata, Map.of("name", "Jane Doe~x7Rk2p"), "diploma", FRONTEND);
        assertEquals("Certified Cardano Developer", og.title());
        assertEquals("Awarded to Jane Doe by Cardano Academy", og.description());
    }

    @Test
    void unverifiableParamNeverReachesTheOutput() throws Exception {
        Map<String, Object> metadata = Map.of(
                "uv_og_desc", "Awarded to {name}",
                "uv_url_name", sha256("Jane Doe~x7Rk2p"));
        OgData og = OgDataFactory.build(metadata, Map.of("name", "Someone Else"), "diploma", FRONTEND);
        assertFalse(og.description().contains("Someone Else"));
    }

    @Test
    void unresolvedPlaceholderFallsBackToDefaultForThatFieldOnly() throws Exception {
        Map<String, Object> metadata = Map.of(
                "uv_og_title", "Static Title",
                "uv_og_desc", "Awarded to {name}",
                "title", "Certified Cardano Developer");
        OgData og = OgDataFactory.build(metadata, Map.of(), "diploma", FRONTEND);
        assertEquals("Static Title", og.title());
        assertFalse(og.description().contains("{name}"));
    }

    @Test
    void diplomaDefaultsUseTitleAndVerifiedName() throws Exception {
        Map<String, Object> metadata = Map.of(
                "title", "Certified Cardano Developer",
                "uv_url_name", sha256("Jane Doe~x7Rk2p"));
        OgData og = OgDataFactory.build(metadata, Map.of("name", "Jane Doe~x7Rk2p"), "diploma", FRONTEND);
        assertEquals("Certified Cardano Developer · Jane Doe", og.title());
    }

    @Test
    void genericDefaultsWhenNothingIsAvailable() {
        OgData og = OgDataFactory.build(Map.of(), Map.of(), null, FRONTEND);
        assertEquals("Blockchain-verified certificate", og.title());
        assertEquals(FRONTEND + "/og/default.png", og.imageUrl());
    }

    @Test
    void templateImageIsDerivedFromTemplateId() {
        OgData og = OgDataFactory.build(Map.of(), Map.of(), "diploma", FRONTEND);
        assertEquals(FRONTEND + "/og/diploma.png", og.imageUrl());
    }

    @Test
    void uvOgImgOverridesTemplateImageButOnlyWithHttpScheme() {
        OgData withOverride = OgDataFactory.build(
                Map.of("uv_og_img", "https://academy.example/card.png"), Map.of(), "diploma", FRONTEND);
        assertEquals("https://academy.example/card.png", withOverride.imageUrl());

        OgData badScheme = OgDataFactory.build(
                Map.of("uv_og_img", "javascript:alert(1)"), Map.of(), "diploma", FRONTEND);
        assertEquals(FRONTEND + "/og/diploma.png", badScheme.imageUrl());
    }

    @Test
    void pathUnsafeTemplateIdsFallBackToTheDefaultImage() {
        // uv_tid is on-chain data and must never be path-interpolated unchecked
        assertEquals(FRONTEND + "/og/default.png",
                OgDataFactory.build(Map.of(), Map.of(), "../evil", FRONTEND).imageUrl());
        assertEquals(FRONTEND + "/og/default.png",
                OgDataFactory.build(Map.of(), Map.of(), "a/b", FRONTEND).imageUrl());
        assertEquals(FRONTEND + "/og/IdentityAuth.png",
                OgDataFactory.build(Map.of(), Map.of(), "IdentityAuth", FRONTEND).imageUrl());
    }
}
