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
import io.uverify.backend.dto.BuildTransactionRequest;
import io.uverify.backend.dto.BuildTransactionResponse;
import io.uverify.backend.dto.SubmitTransactionRequest;
import io.uverify.backend.enums.BuildStatusCode;
import io.uverify.backend.enums.TransactionType;
import io.uverify.backend.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/transaction")
@Tag(name = "Transaction Management", description = "Endpoints for building and submitting UVerify certificate transactions to the Cardano blockchain.")
public class TransactionController {
    @Autowired
    private TransactionService transactionService;

    @PostMapping("/build")
    @Operation(
            summary = "Build a transaction",
            description = """
                    Builds a transaction for the Cardano blockchain based on the provided request. Supports the following transaction types:
                    - **DEFAULT**: Submits UVerify certificates to the blockchain using the cheapest options. If no state is initialized, it forks a new state from the bootstrap datum with the best service fee conditions. If a user state exists with a valid transaction countdown and no service fee is required, it will be reused.
                    - **BOOTSTRAP**: Initializes a new bootstrap token and datum for forking states. Requires a whitelisted credential to sign the transaction.
                    - **CUSTOM**: Allows the user to specify a bootstrap datum to fork or consume a state related to a specific bootstrap datum. This is useful for use cases requiring a 'partner datum' and may result in a different certificate UI on the client side."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction built successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuildTransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid transaction type or request data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> buildTransaction(@RequestBody BuildTransactionRequest request) {
        try {
            if (request.getType().equals(TransactionType.DEFAULT)) {
                BuildTransactionResponse buildTransactionResponse = transactionService.buildUVerifyTransaction(request.getCertificates(), request.getAddress());
                if (buildTransactionResponse.getStatus().getCode().equals(BuildStatusCode.SUCCESS)) {
                    return ResponseEntity.ok(buildTransactionResponse);
                } else {
                    return ResponseEntity.badRequest().body(buildTransactionResponse);
                }
            } else if (request.getType().equals(TransactionType.BOOTSTRAP)) {
                BuildTransactionResponse buildTransactionResponse = transactionService.buildBootstrapDatum(request.getBootstrapDatum());
                if (buildTransactionResponse.getStatus().getCode().equals(BuildStatusCode.SUCCESS)) {
                    return ResponseEntity.ok(buildTransactionResponse);
                } else {
                    return ResponseEntity.badRequest().body(buildTransactionResponse);
                }
            } else if (request.getType().equals(TransactionType.CUSTOM)) {
                BuildTransactionResponse buildTransactionResponse = transactionService.buildCustomTransaction(request.getCertificates(), request.getAddress(), request.getBootstrapDatum().getName());
                if (buildTransactionResponse.getStatus().getCode().equals(BuildStatusCode.SUCCESS)) {
                    return ResponseEntity.ok(buildTransactionResponse);
                } else {
                    return ResponseEntity.badRequest().body(buildTransactionResponse);
                }
            } else {
                return ResponseEntity.badRequest().body("Unknown transaction type. Allowed types are: DEFAULT, BOOTSTRAP, CUSTOM.");
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/submit")
    @Operation(
            summary = "Submit a transaction",
            description = "Submits a transaction to the Cardano blockchain using the provided transaction data and witness set. "
                    + "Returns the result of the submission or a 500 status code in case of server errors."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction submitted successfully",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> submitTransaction(@RequestBody SubmitTransactionRequest request) {
        try {
            return ResponseEntity.ok(transactionService.submit(request.getTransaction(), request.getWitnessSet()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
