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
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FeeProviderRequest {
    @JsonAlias({"adminAddresses", "admin_addresses"})
    List<String> adminAddresses;
    @JsonAlias({"userAddresses", "user_addresses"})
    List<String> userAddresses;
    @JsonAlias({"topUpAmount", "top_up_amount"})
    Long topUpAmount;
    @JsonAlias({"topUpAddress", "top_up_address"})
    String topUpAddress;
}
