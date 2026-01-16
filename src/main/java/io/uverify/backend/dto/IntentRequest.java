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

package io.uverify.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.uverify.backend.enums.IntentType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class IntentRequest {
    IntentType type;
    @JsonAlias({"certificateRequest", "certificate_request"})
    BuildTransactionRequest certificateRequest;
    @JsonAlias({"extensionMetadata", "extension_metadata"})
    Map<String, String> extensionMetadata;
    @JsonAlias({"unixTimestamp", "unix_timestamp"})
    long unixTimestamp;
    @JsonAlias({"feePotAddress", "fee_pot_address"})
    String feePotAddress;
    @JsonAlias({"intentSignature", "intent_signature"})
    String intentSignature;
    @JsonAlias({"signerPublicKey", "signer_public_key"})
    String signerPublicKey;
    @JsonAlias({"signerAddress", "signer_address"})
    String signerAddress;
}
