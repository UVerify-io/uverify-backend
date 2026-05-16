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

package io.uverify.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.uverify.backend.dto.BuildTransactionResponse;
import io.uverify.backend.dto.PrepareCollateralRequest;
import io.uverify.backend.enums.BuildStatusCode;
import io.uverify.backend.service.PrepareCollateralService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/transaction")
@Slf4j
@Tag(name = "Transaction Management")
public class PrepareCollateralController {

    @Autowired
    private PrepareCollateralService prepareCollateralService;

    @PostMapping("/prepare-collateral")
    @Operation(
            summary = "Prepare a collateral UTxO",
            description = """
                    Checks whether the given address already has a suitable collateral UTxO (≥5 ADA in a dedicated UTxO with at least one other UTxO available for fees).
                    If collateral is already available, returns COLLATERAL_ALREADY_AVAILABLE with no transaction to sign.
                    Otherwise builds an ADA-only split transaction that creates a dedicated 5 ADA UTxO for use as collateral.
                    The caller must sign and submit the returned transaction, then wait for confirmation before retrying the original certificate transaction."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Collateral already available or split transaction built",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuildTransactionResponse.class))),
            @ApiResponse(responseCode = "409", description = "All UTxOs are locked by in-flight transactions"),
            @ApiResponse(responseCode = "400", description = "Insufficient funds or unknown error")
    })
    public ResponseEntity<?> prepareCollateral(@RequestBody PrepareCollateralRequest request) {
        BuildTransactionResponse response = prepareCollateralService.prepareCollateral(request.getSenderAddress());
        return switch (response.getStatus().getCode()) {
            case SUCCESS, COLLATERAL_ALREADY_AVAILABLE -> ResponseEntity.ok(response);
            case PENDING_TRANSACTION -> ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            default -> ResponseEntity.badRequest().body(response);
        };
    }
}
