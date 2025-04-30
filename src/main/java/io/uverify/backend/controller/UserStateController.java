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
import io.uverify.backend.dto.ExecuteUserActionRequest;
import io.uverify.backend.dto.ExecuteUserActionResponse;
import io.uverify.backend.dto.UserActionRequest;
import io.uverify.backend.dto.UserActionResponse;
import io.uverify.backend.enums.UserAction;
import io.uverify.backend.service.UserStateService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/user")
@Tag(name = "User State Management", description = "Endpoints for managing user states and performing user-related actions.")
public class UserStateController {

    @Autowired
    private UserStateService userStateService;

    @PostMapping("/request/action")
    @Operation(
            summary = "Create a user state action request",
            description = """
                    Creates a request object for a specific user state action. The backend signs the request message, and the user must also sign the message to execute the action in the next step. Supported actions include:
                    - **USER_INFO**: Retrieve user information based on the provided address.
                    - **INVALIDATE_STATE**: Create a request to invalidate a specific user state. This will prepare an unsigned transaction to burn the state token, collect the UTXO locked in the contract, and send it (including ADA) to the user's wallet.
                    - **OPT_OUT**: Create a request to invalidate all states and claim back all locked UTXOs."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Action request successfully created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserActionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or unknown action type",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserActionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<UserActionResponse> requestUserState(@RequestBody @NotNull UserActionRequest request) {
        UserActionResponse response = new UserActionResponse();
        if (request.getAction().equals(UserAction.USER_INFO)) {
            return ResponseEntity.ok(userStateService.requestUserInfo(request.getAddress()));
        } else if (request.getAction().equals(UserAction.INVALIDATE_STATE)) {
            if (request.getStateId() == null) {
                response.setError("State ID is required for invalidation.");
                response.setStatus(HttpStatus.BAD_REQUEST);
                return ResponseEntity.badRequest().body(response);
            }
            return ResponseEntity.ok(userStateService.requestInvalidateState(request.getAddress(), request.getStateId()));
        } else if (request.getAction().equals(UserAction.OPT_OUT)) {
            return ResponseEntity.ok(userStateService.requestOptOut(request.getAddress()));
        }

        response.setError("Unknown action type or invalid request.");
        response.setStatus(HttpStatus.BAD_REQUEST);
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/state/action")
    @Operation(
            summary = "Execute a user state action",
            description = """
                    Executes a user state action based on the signed request created in the previous step. The backend prepares an unsigned transaction for the action, which must be signed and submitted by the user. Supported actions include:
                    - **USER_INFO**: Execute a request to retrieve user information.
                    - **INVALIDATE_STATE**: Execute a request to invalidate a specific user state. This will return an unsigned transaction to burn the state token, collect the UTXO locked in the contract, and send it (including ADA) to the user's wallet.
                    - **OPT_OUT**: Execute a request to invalidate all states and claim back all locked UTXOs.

                    The unsigned transaction returned by this endpoint must be signed by the user using their wallet or submitted to the `/api/v1/transaction/submit` endpoint."""
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Action successfully executed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecuteUserActionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or unknown action type",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> executeStateAction(@RequestBody @NotNull ExecuteUserActionRequest request) {
        if (request.getAction().equals(UserAction.USER_INFO)) {
            return ResponseEntity.ok(userStateService.executeUserInfoRequest(request));
        } else if (request.getAction().equals(UserAction.INVALIDATE_STATE)) {
            return ResponseEntity.ok(userStateService.executeStateInvalidationRequest(request));
        } else if (request.getAction().equals(UserAction.OPT_OUT)) {
            return ResponseEntity.ok(userStateService.executeUserOptOut(request));
        }
        return ResponseEntity.badRequest().body("Unknown action type or invalid request.");
    }
}
