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
import io.uverify.backend.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/statistic")
@Tag(name = "Statistics", description = "Endpoints for retrieving certificate and transaction fee statistics.")
public class StatisticsController {
    @Autowired
    private StatisticsService statisticsService;

    @GetMapping("/certificate/by-category")
    @Operation(
            summary = "Certificate counts by category",
            description = "Returns the total number of UVerify certificates grouped by template/category."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "object", additionalPropertiesSchema = @Schema(type = "integer"),
                                    description = "Map of category name to certificate count"))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getTransactionStatistics() {
        Map<String, Integer> totalUVerifyCertificates = statisticsService.getTotalUVerifyCertificates();
        return ResponseEntity.ok(totalUVerifyCertificates);
    }

    @GetMapping("/tx-fees")
    @Operation(
            summary = "Total transaction fees",
            description = "Returns the accumulated UVerify transaction fees in lovelace (1 ADA = 1 000 000 lovelace)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fee amount retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "integer", format = "int64",
                                    description = "Total fees in lovelace"))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getTransactionFees() {
        Long transactionFees = statisticsService.getTransactionFees();
        return ResponseEntity.ok(transactionFees);
    }
}
