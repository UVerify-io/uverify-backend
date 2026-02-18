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
public class LibraryController {

    @Autowired
    LibraryService libraryService;

    @GetMapping("/deployments")
    public ResponseEntity<?> getLibraryDeployments() {
        LibraryDeploymentResponse libraryDeploymentResponse = libraryService.getDeployments();

        if (libraryDeploymentResponse == null) {
            return ResponseEntity.badRequest().body("Failed to retrieve library deployments.");
        }

        return ResponseEntity.ok(libraryDeploymentResponse);
    }

    @PostMapping("/deploy/proxy")
    public ResponseEntity<?> deployContracts() {
        BuildTransactionResponse buildTransactionResponse = libraryService.buildDeployTransaction();
        if (buildTransactionResponse.getStatus().getCode().equals(BuildStatusCode.SUCCESS)) {
            return ResponseEntity.ok(buildTransactionResponse);
        } else {
            return ResponseEntity.badRequest().body(buildTransactionResponse);
        }
    }

    @PostMapping("/upgrade/proxy")
    public ResponseEntity<?> upgradeStateContract(@RequestBody String request) {
        BuildTransactionResponse buildTransactionResponse = libraryService.buildDeployTransaction(request);
        if (buildTransactionResponse.getStatus().getCode().equals(BuildStatusCode.SUCCESS)) {
            return ResponseEntity.ok(buildTransactionResponse);
        } else {
            return ResponseEntity.badRequest().body(buildTransactionResponse);
        }
    }

    @GetMapping("/undeploy/unused")
    public ResponseEntity<?> undeployUnusedContracts() {
        BuildTransactionResponse undeployUnusedContractsResponse = libraryService.undeployUnusedContracts();

        if (undeployUnusedContractsResponse.getStatus().getCode().equals(BuildStatusCode.ERROR)) {
            return ResponseEntity.badRequest().body(undeployUnusedContractsResponse);
        }

        return ResponseEntity.ok(undeployUnusedContractsResponse);
    }

    @GetMapping("/undeploy/{transactionHash}/{outputIndex}")
    public ResponseEntity<?> undeployContract(@PathVariable String transactionHash, @PathVariable Integer outputIndex) {
        BuildTransactionResponse undeployContractResponse = libraryService.undeployContract(transactionHash, outputIndex);

        if (undeployContractResponse.getStatus().getCode().equals(BuildStatusCode.ERROR)) {
            return ResponseEntity.badRequest().body(undeployContractResponse);
        }

        return ResponseEntity.ok(undeployContractResponse);
    }
}
