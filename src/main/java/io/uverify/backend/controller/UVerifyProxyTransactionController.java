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

import io.swagger.v3.oas.annotations.tags.Tag;
import io.uverify.backend.dto.BuildTransactionRequest;
import io.uverify.backend.dto.BuildTransactionResponse;
import io.uverify.backend.enums.BuildStatusCode;
import io.uverify.backend.enums.TransactionType;
import io.uverify.backend.service.UVerifyTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v2/transaction")
@Tag(name = "Transaction Management", description = "Endpoints for building and submitting UVerify certificate transactions to the Cardano blockchain.")
public class UVerifyProxyTransactionController {
    @Autowired
    private UVerifyTransactionService transactionService;

    @PostMapping("/build")
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
                BuildTransactionResponse buildTransactionResponse = transactionService.mintProxyBootstrapToken(request.getBootstrapDatum());
                if (buildTransactionResponse.getStatus().getCode().equals(BuildStatusCode.SUCCESS)) {
                    return ResponseEntity.ok(buildTransactionResponse);
                } else {
                    return ResponseEntity.badRequest().body(buildTransactionResponse);
                }
            } else if (request.getType().equals(TransactionType.BURN_STATE)) {
                return ResponseEntity.status(404).body("Burn state hasn't been implemented yet.");
            } else if (request.getType().equals(TransactionType.BURN_BOOTSTRAP)) {
                return ResponseEntity.status(404).body("Burn bootstrap hasn't been implemented yet.");
            } else {
                return ResponseEntity.badRequest().body("Unknown transaction type. Allowed types are: DEFAULT, BOOTSTRAP, CUSTOM.");
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
