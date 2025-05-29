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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.uverify.backend.extension.dto.TadamonTransactionRequest;
import io.uverify.backend.extension.service.TadamonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@ConditionalOnProperty(value = "extensions.tadamon.enabled", havingValue = "true")
@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/extension/tadamon")
public class TadamonController {

    @Autowired
    private final TadamonService tadamonService;

    public TadamonController(TadamonService tadamonService) {
        this.tadamonService = tadamonService;
    }

    @PostMapping("/tx/submit")
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
    public ResponseEntity<?> submitTransaction(@RequestBody TadamonTransactionRequest request) {
        try {
            return ResponseEntity.ok(tadamonService.submit(request));
        } catch (Exception e) {
            log.error("Error submitting transaction: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
