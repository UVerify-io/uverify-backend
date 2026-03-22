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
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry endpoint that reports which UVerify extensions are currently enabled.
 *
 * <p>Returns a JSON map of {@code extensionId → enabled} for every known extension.
 * Clients can use this to discover available functionality without querying each
 * extension endpoint individually.
 */
@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/extensions")
@Tag(name = "Extensions", description = "Query which UVerify extensions are enabled on this instance.")
public class ExtensionController {

    private final Environment environment;

    public ExtensionController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping
    @Operation(
            summary = "List enabled extensions",
            description = "Returns a map of extension identifiers to their enabled status."
    )
    @ApiResponse(responseCode = "200", description = "Extension registry retrieved",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(example = "{\"fractionized-certificate\":true,\"tokenizable-certificate\":false}")))
    public ResponseEntity<Map<String, Boolean>> list() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put("fractionized-certificate",
                environment.getProperty("extensions.fractionized-certificate.enabled", Boolean.class, false));
        result.put("tokenizable-certificate",
                environment.getProperty("extensions.tokenizable-certificate.enabled", Boolean.class, false));
        return ResponseEntity.ok(result);
    }
}
