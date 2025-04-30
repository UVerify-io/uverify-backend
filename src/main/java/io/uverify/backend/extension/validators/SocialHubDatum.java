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

package io.uverify.backend.extension.validators;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import lombok.*;

import java.util.Optional;

@Constr
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SocialHubDatum {
    private byte[] owner;
    private byte[] batchId;
    private Optional<byte[]> picture;
    private Optional<byte[]> name;
    private Optional<byte[]> subtitle;
    private Optional<byte[]> x;
    private Optional<byte[]> telegram;
    private Optional<byte[]> instagram;
    private Optional<byte[]> discord;
    private Optional<byte[]> reddit;
    private Optional<byte[]> youtube;
    private Optional<byte[]> linkedin;
    private Optional<byte[]> github;
    private Optional<byte[]> website;
    private Optional<byte[]> adahandle;
    private Optional<byte[]> email;
}
