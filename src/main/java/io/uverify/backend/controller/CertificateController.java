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
import io.uverify.backend.dto.CertificateResponse;
import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.mapper.CertificateMapper;
import io.uverify.backend.service.UVerifyCertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/verify")
@Tag(name = "Certificate Verification", description = "Endpoints for retrieving and verifying certificates stored on the Cardano blockchain.")
public class CertificateController {

    private final UVerifyCertificateService UVerifyCertificateService;

    private final CardanoNetwork network;

    @Autowired
    public CertificateController(@Value("${cardano.network}") String network, UVerifyCertificateService UVerifyCertificateService) {
        this.network = CardanoNetwork.valueOf(network);
        this.UVerifyCertificateService = UVerifyCertificateService;
    }

    @GetMapping("/{hash}")
    @Operation(
            summary = "Retrieve certificates by data hash",
            description = "Retrieves a list of certificates associated with the provided data hash. "
                    + "The `hash` is the data hash (e.g., SHA-256 or SHA-512) of the file or text that was certified."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Certificates retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CertificateResponse.class))),
            @ApiResponse(responseCode = "404", description = "No certificates found for the provided hash"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<CertificateResponse>> getCertificateByHash(
            @PathVariable String hash) {
        List<UVerifyCertificateEntity> UVerifyCertificateEntities = UVerifyCertificateService.getCertificatesByHash(hash);

        if (UVerifyCertificateEntities.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(UVerifyCertificateEntities.stream().map(entity -> CertificateMapper.fromCertificate(entity, network)).toList());
    }

    @GetMapping("/by-transaction-hash/{transactionHash}/{dataHash}")
    @Operation(
            summary = "Retrieve a certificate by transaction hash and data hash",
            description = """
                    Retrieves a certificate associated with the provided Cardano blockchain transaction hash and data hash.
                    The `transactionHash` is the hash of the Cardano blockchain transaction where the certificate data has been stored,
                    and the `dataHash` is the hash of the certified file or text."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Certificate retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CertificateResponse.class))),
            @ApiResponse(responseCode = "404", description = "No certificate found for the provided transaction hash and data hash"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getCertificateByTransactionHash(
            @PathVariable String transactionHash, @PathVariable String dataHash) {
        UVerifyCertificateEntity UVerifyCertificateEntity = UVerifyCertificateService.getCertificateByTransactionHash(transactionHash, dataHash);

        if (UVerifyCertificateEntity == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(UVerifyCertificateEntity);
    }
}
