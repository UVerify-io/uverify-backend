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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.uverify.backend.enums.UserAction;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecuteUserActionRequest {
    private String address;
    private UserAction action;

    @JsonProperty("state_id")
    private String stateId;

    private String message;
    private String signature;

    @JsonProperty("user_signature")
    private String userSignature;

    @JsonProperty("user_public_key")
    private String userPublicKey;
    private long timestamp;
}
