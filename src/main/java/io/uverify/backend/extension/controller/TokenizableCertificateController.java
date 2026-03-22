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
import io.uverify.backend.extension.dto.tokenizable.CertificateStatusResponse;
import io.uverify.backend.extension.dto.tokenizable.TokenizableBuildRequest;
import io.uverify.backend.extension.service.TokenizableCertificateService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@ConditionalOnProperty(value = "extensions.tokenizable-certificate.enabled", havingValue = "true")
@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/extension/tokenizable-certificate")
@Tag(name = "Tokenizable Certificate", description = "Extension endpoints for the tokenizable-certificate contract.")
public class TokenizableCertificateController {

    private final TokenizableCertificateService service;

    public TokenizableCertificateController(TokenizableCertificateService service) {
        this.service = service;
    }

    @PostMapping("/build")
    @Operation(
            summary = "Build a tokenizable-certificate transaction",
            description = """
                    Builds an unsigned transaction based on the request type:
                    - **CREATE**: checks on-chain state automatically — if no HEAD exists, runs Init;
                      otherwise runs Insert (sorted linked-list insertion + UVerify state update).
                    - **REDEEM**: builds a Redeem transaction that claims the NFT for an available
                      node and sends it to the sender's address.

                    Returns HTTP 400 for constraint violations (e.g. EUTXO conflict on first insert
                    or node already claimed).
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unsigned transaction built successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "string", description = "Hex-encoded CBOR unsigned transaction"))),
            @ApiResponse(responseCode = "400", description = "Invalid request or constraint violation"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> build(@RequestBody @NotNull TokenizableBuildRequest request) {
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
            description = """
                    Returns the on-chain status of the tokenizable-certificate node identified by its hex key.
                    The `initUtxoTxHash` and `initUtxoOutputIndex` parameters identify which linked-list
                    instance to query (they determine the policy ID).
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CertificateStatusResponse.class))),
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
