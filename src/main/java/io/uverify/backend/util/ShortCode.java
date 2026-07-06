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

public final class ShortCode {

    private static final char[] BASE62 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    public static final int CODE_LENGTH = 10;

    private ShortCode() {
    }

    public static String fromHash(String hexHash) {
        // Top 59 bits of the hash: 64 bits parsed, 5 shifted out. 59 bits fit
        // 10 base62 chars without modular wrap, so a code decodes back to a
        // hash prefix (see hexPrefix).
        long value = Long.parseUnsignedLong(hexHash.substring(0, 16), 16) >>> 5;
        char[] code = new char[CODE_LENGTH];
        for (int i = CODE_LENGTH - 1; i >= 0; i--) {
            code[i] = BASE62[(int) (value % 62)];
            value /= 62;
        }
        return new String(code);
    }

    public static boolean isValid(String code) {
        return code != null && code.length() == CODE_LENGTH && code.chars().allMatch(ShortCode::isBase62);
    }

    public static String hexPrefix(String code) {
        long value = 0;
        for (char character : code.toCharArray()) {
            value = value * 62 + digitValue(character);
        }
        // 59 decoded bits cover 14 full hex chars (56 bits) plus 3 spare bits.
        return String.format("%014x", value >>> 3);
    }

    private static boolean isBase62(int character) {
        return (character >= '0' && character <= '9')
                || (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z');
    }

    private static long digitValue(char character) {
        if (character <= '9') return character - '0';
        if (character <= 'Z') return character - 'A' + 10;
        return character - 'a' + 36;
    }
}
