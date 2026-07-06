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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShortCodeTest {

    private static final String MAINNET_HASH =
            "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e";
    private static final String SOCIAL_HUB_HASH =
            "243d719da7c49d8616723e0cfb698611b0f370f0b34a744b2e0e88b55cd86fa8";
    private static final String ZERO_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    @Test
    void knownVectors() {
        assertEquals("RXYWODQzXG", ShortCode.fromHash(MAINNET_HASH));
        assertEquals("61kgLIX6vE", ShortCode.fromHash(SOCIAL_HUB_HASH));
        assertEquals("0000000000", ShortCode.fromHash(ZERO_HASH));
    }

    @Test
    void hexPrefixDecodesBackToHashPrefix() {
        assertEquals("a591a6d40bf420", ShortCode.hexPrefix("RXYWODQzXG"));
        assertEquals("243d719da7c49d", ShortCode.hexPrefix("61kgLIX6vE"));
        assertTrue(MAINNET_HASH.startsWith(ShortCode.hexPrefix(ShortCode.fromHash(MAINNET_HASH))));
        assertTrue(SOCIAL_HUB_HASH.startsWith(ShortCode.hexPrefix(ShortCode.fromHash(SOCIAL_HUB_HASH))));
    }

    @Test
    void codesAreAlwaysTenCharsOfTheBase62Alphabet() {
        assertTrue(ShortCode.fromHash(MAINNET_HASH).matches("[0-9A-Za-z]{10}"));
        assertTrue(ShortCode.fromHash("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff").matches("[0-9A-Za-z]{10}"));
    }

    @Test
    void isValidAcceptsOnlyTenCharBase62() {
        assertTrue(ShortCode.isValid("RXYWODQzXG"));
        assertFalse(ShortCode.isValid("RXYWODQzX"));
        assertFalse(ShortCode.isValid("RXYWODQzXG1"));
        assertFalse(ShortCode.isValid("RXYWODQzX!"));
        assertFalse(ShortCode.isValid(null));
    }

    @Test
    void uppercaseHexInputProducesTheSameCode() {
        assertEquals(ShortCode.fromHash(MAINNET_HASH), ShortCode.fromHash(MAINNET_HASH.toUpperCase()));
    }
}
