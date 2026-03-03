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
import io.uverify.backend.dto.FaucetChallengeRequest;
import io.uverify.backend.dto.FaucetChallengeResponse;
import io.uverify.backend.dto.FaucetClaimRequest;
import io.uverify.backend.dto.FaucetClaimResponse;
import io.uverify.backend.service.FaucetService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/faucet")
@ConditionalOnProperty(name = "faucet.enabled", havingValue = "true")
@Tag(name = "Dev Faucet", description = "Optional testnet faucet — enabled only when FAUCET_ENABLED=true. " +
        "Sends tADA to a requesting address after ownership is verified via a challenge-sign flow.")
public class FaucetController {

    @Autowired
    private FaucetService faucetService;

    @PostMapping("/request")
    @Operation(
            summary = "Request a faucet challenge",
            description = """
                    Step 1 of 2 in the faucet flow. Provide your Cardano address to receive a
                    server-signed challenge message. Sign the returned `message` with your wallet
                    (CIP-30 `signData`) and use all returned fields in the `/api/v1/faucet/claim` step.

                    The endpoint returns HTTP 429 if the address is still within its cooldown period."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Challenge created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FaucetChallengeResponse.class))),
            @ApiResponse(responseCode = "429", description = "Address is in cooldown — too many recent requests",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FaucetChallengeResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<FaucetChallengeResponse> requestChallenge(
            @RequestBody @NotNull FaucetChallengeRequest request) {
        FaucetChallengeResponse response = faucetService.requestChallenge(request);
        if (response.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/claim")
    @Operation(
            summary = "Claim testnet ADA from the faucet",
            description = """
                    Step 2 of 2 in the faucet flow. Submit the challenge fields from `/api/v1/faucet/request`
                    together with your CIP-30 wallet signature (`userSignature`, `userPublicKey`).

                    On success, the backend signs and submits a transaction sending multiple UTXOs of tADA
                    from the faucet wallet to your address. Returns the Cardano transaction hash.

                    The address enters a cooldown period after a successful claim to prevent abuse."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Funds sent successfully — txHash is the Cardano transaction hash",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FaucetClaimResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or expired signatures",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FaucetClaimResponse.class))),
            @ApiResponse(responseCode = "429", description = "Address is in cooldown",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FaucetClaimResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<FaucetClaimResponse> claimFunds(
            @RequestBody @NotNull FaucetClaimRequest request) {
        FaucetClaimResponse response = faucetService.claimFunds(request);
        return switch (response.getStatus()) {
            case BAD_REQUEST -> ResponseEntity.badRequest().body(response);
            case TOO_MANY_REQUESTS -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
            case INTERNAL_SERVER_ERROR -> ResponseEntity.internalServerError().body(response);
            default -> ResponseEntity.ok(response);
        };
    }
}
