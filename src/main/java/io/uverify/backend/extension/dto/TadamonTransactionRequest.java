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

package io.uverify.backend.extension.dto;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

@Data
public class TadamonTransactionRequest {
    private String transaction;
    @JsonAlias({"witnessSet", "witness_set"})
    private String witnessSet;

    @JsonProperty("cso")
    private TadamonCso cso;

    @JsonAlias({"tadamonId", "tadamon_id"})
    private String tadamonId;

    @JsonAlias({"veridianAid", "veridian_aid"})
    private String veridianAid;

    @JsonAlias({"undpSigningDate", "undp_signing_date"})
    private LocalDateTime undpSigningDate;

    @JsonAlias({"beneficiarySigningDate", "beneficiary_signing_date"})
    private LocalDateTime beneficiarySigningDate;
}
