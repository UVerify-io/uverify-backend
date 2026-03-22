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

package io.uverify.backend.extension.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.uverify.backend.extension.dto.fractionized.FractionizedBuildRequest;
import io.uverify.backend.extension.dto.fractionized.FractionizedStatusResponse;
import io.uverify.backend.extension.service.FractionizedCertificateService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@ConditionalOnProperty(value = "extensions.fractionized-certificate.enabled", havingValue = "true")
@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/extension/fractionized-certificate")
@Tag(name = "Fractionized Certificate", description = "Extension endpoints for the fractionized-certificate contract.")
public class FractionizedCertificateController {

    private final FractionizedCertificateService service;

    public FractionizedCertificateController(FractionizedCertificateService service) {
        this.service = service;
    }

    @PostMapping("/build")
    @Operation(
            summary = "Build a fractionized-certificate transaction",
            description = """
                    Builds an unsigned transaction based on the request type:
                    - **CREATE**: checks on-chain state automatically — if no HEAD exists, runs Init;
                      otherwise runs Insert (sorted linked-list insertion + UVerify state update).
                    - **REDEEM**: builds a Claim transaction that mints `amount` fungible tokens
                      from an available node and sends them to the sender's address.

                    Returns HTTP 400 for constraint violations (e.g. EUTXO conflict on first insert,
                    exhausted node, or insufficient amount).
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unsigned transaction built successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "string", description = "Hex-encoded CBOR unsigned transaction"))),
            @ApiResponse(responseCode = "400", description = "Invalid request or constraint violation"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> build(@RequestBody @NotNull FractionizedBuildRequest request) {
        try {
            return ResponseEntity.ok(service.buildTransaction(request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Bad build request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error building transaction: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Transaction building failed: " + e.getMessage());
        }
    }

    @GetMapping("/status/{key}")
    @Operation(
            summary = "Query certificate node status",
            description = "Returns the on-chain status of the fractionized-certificate node identified by its hex key."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FractionizedStatusResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getStatus(
            @Parameter(description = "Hex-encoded node key to look up", required = true)
            @PathVariable String key,
            @Parameter(description = "Tx-hash of the init UTxO used to derive the policy ID", required = true)
            @RequestParam String initUtxoTxHash,
            @Parameter(description = "Output index of the init UTxO", required = true)
            @RequestParam int initUtxoOutputIndex) {
        try {
            return ResponseEntity.ok(service.getCertificateStatus(key, initUtxoTxHash, initUtxoOutputIndex));
        } catch (Exception e) {
            log.error("Error querying certificate status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Status query failed: " + e.getMessage());
        }
    }
}
