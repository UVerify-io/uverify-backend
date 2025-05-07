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
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
public class CertificateResponse {
    private String hash;
    private String address;
    @JsonAlias({"blockHash", "block_hash"})
    private String blockHash;
    @JsonAlias({"blockNumber", "block_number"})
    private Long blockNumber;
    @JsonAlias({"transactionHash", "transaction_hash"})
    private String transactionHash;
    private Long slot;
    @JsonAlias({"creationTime", "creation_time"})
    private Long creationTime;
    private String metadata;
    private String issuer;
}
