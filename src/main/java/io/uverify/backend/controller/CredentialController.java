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
import io.uverify.backend.dto.CredentialResponse;
import io.uverify.backend.service.CredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@SuppressWarnings("unused")
@RequiredArgsConstructor
@RequestMapping("/api/v1/credential")
@Tag(name = "Credential Verification", description = "Endpoints for resolving KERI-backed identity and certification credentials linked to Cardano wallets.")
public class CredentialController {

    private final CredentialService credentialService;

    @GetMapping("/{paymentCredential}")
    @Operation(
            summary = "Resolve credentials for a wallet",
            description = "Returns all active (non-revoked) credentials for the given Cardano payment credential. "
                    + "When the optional `type` query parameter is supplied, only the matching credential type is returned."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credential(s) found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CredentialResponse.class))),
            @ApiResponse(responseCode = "404", description = "No active credential found")
    })
    public ResponseEntity<?> getCredentials(
            @PathVariable String paymentCredential,
            @RequestParam(required = false) String type) {

        if (type != null && !type.isBlank()) {
            return credentialService.resolveCredential(paymentCredential, type)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        List<CredentialResponse> results = credentialService.resolveCredentials(paymentCredential);
        if (results.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(results);
    }

    @GetMapping("/by-hash/{authHash}")
    @Operation(
            summary = "Resolve a credential by its AUTH cert hash",
            description = "Looks up a credential (active or revoked) by the on-chain hash of the AUTH certificate that registered it."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credential found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CredentialResponse.class))),
            @ApiResponse(responseCode = "404", description = "Credential not found")
    })
    public ResponseEntity<CredentialResponse> getCredentialByHash(@PathVariable String authHash) {
        return credentialService.resolveCredentialByHash(authHash)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
