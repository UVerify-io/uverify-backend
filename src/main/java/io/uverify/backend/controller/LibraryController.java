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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.uverify.backend.dto.BuildTransactionResponse;
import io.uverify.backend.dto.LibraryDeploymentResponse;
import io.uverify.backend.enums.BuildStatusCode;
import io.uverify.backend.service.LibraryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/library")
@Tag(name = "Library", description = "Endpoints for managing on-chain Aiken/Plutus script deployments.")
public class LibraryController {

    @Autowired
    LibraryService libraryService;

    @GetMapping("/deployments")
    @Operation(
            summary = "List script deployments",
            description = "Returns the current on-chain deployment state of the UVerify Plutus/Aiken library scripts."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deployments retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LibraryDeploymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Failed to retrieve library deployments"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getLibraryDeployments() {
        LibraryDeploymentResponse libraryDeploymentResponse = libraryService.getDeployments();

        if (libraryDeploymentResponse == null) {
            return ResponseEntity.badRequest().body("Failed to retrieve library deployments.");
        }

        return ResponseEntity.ok(libraryDeploymentResponse);
    }

    @PostMapping("/deploy/proxy")
    @Operation(
            summary = "Deploy proxy contract",
            description = "Builds an unsigned Cardano transaction that deploys the UVerify proxy script on-chain. "
                    + "The returned transaction must be signed by the service wallet before submission."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unsigned deployment transaction built successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuildTransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Transaction build failed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuildTransactionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> deployContracts() {
        BuildTransactionResponse buildTransactionResponse = libraryService.buildDeployTransaction();
        if (buildTransactionResponse.getStatus().getCode().equals(BuildStatusCode.SUCCESS)) {
            return ResponseEntity.ok(buildTransactionResponse);
        } else {
            return ResponseEntity.badRequest().body(buildTransactionResponse);
        }
    }

    @PostMapping("/upgrade/proxy")
    @Operation(
            summary = "Upgrade proxy contract",
            description = "Builds an unsigned Cardano transaction that upgrades the existing proxy script to a new version. "
                    + "The request body should contain the new script reference transaction hash."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unsigned upgrade transaction built successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuildTransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Transaction build failed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuildTransactionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> upgradeStateContract(@RequestBody String request) {
        BuildTransactionResponse buildTransactionResponse = libraryService.buildDeployTransaction(request);
        if (buildTransactionResponse.getStatus().getCode().equals(BuildStatusCode.SUCCESS)) {
            return ResponseEntity.ok(buildTransactionResponse);
        } else {
            return ResponseEntity.badRequest().body(buildTransactionResponse);
        }
    }

    @GetMapping("/undeploy/unused")
    @Operation(
            summary = "Undeploy unused contracts",
            description = "Builds an unsigned Cardano transaction that reclaims all on-chain script UTxOs that are no longer referenced by active certificates."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unsigned undeployment transaction built successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuildTransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Transaction build failed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuildTransactionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> undeployUnusedContracts() {
        BuildTransactionResponse undeployUnusedContractsResponse = libraryService.undeployUnusedContracts();

        if (undeployUnusedContractsResponse.getStatus().getCode().equals(BuildStatusCode.ERROR)) {
            return ResponseEntity.badRequest().body(undeployUnusedContractsResponse);
        }

        return ResponseEntity.ok(undeployUnusedContractsResponse);
    }

    @GetMapping("/undeploy/{transactionHash}/{outputIndex}")
    @Operation(
            summary = "Undeploy a specific contract",
            description = "Builds an unsigned Cardano transaction that removes the script UTxO identified by the given transaction hash and output index."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unsigned undeployment transaction built successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuildTransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Transaction build failed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuildTransactionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> undeployContract(
            @Parameter(description = "Cardano transaction hash of the UTxO containing the deployed script", required = true)
            @PathVariable String transactionHash,
            @Parameter(description = "Output index of the UTxO within the transaction", required = true)
            @PathVariable Integer outputIndex) {
        BuildTransactionResponse undeployContractResponse = libraryService.undeployContract(transactionHash, outputIndex);

        if (undeployContractResponse.getStatus().getCode().equals(BuildStatusCode.ERROR)) {
            return ResponseEntity.badRequest().body(undeployContractResponse);
        }

        return ResponseEntity.ok(undeployContractResponse);
    }
}
