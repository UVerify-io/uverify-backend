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

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.uverify.backend.dto.BuildTransactionResponse;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.extension.dto.ClaimUpdateConnectedGoodsRequest;
import io.uverify.backend.extension.dto.MintConnectedGoodsRequest;
import io.uverify.backend.extension.dto.MintConnectedGoodsResponse;
import io.uverify.backend.extension.dto.SocialHub;
import io.uverify.backend.extension.entity.ConnectedGoodEntity;
import io.uverify.backend.extension.entity.ConnectedGoodUpdateEntity;
import io.uverify.backend.extension.entity.SocialHubEntity;
import io.uverify.backend.extension.service.ConnectedGoodsService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import static io.uverify.backend.extension.dto.SocialHub.fromSocialHubEntity;
import static io.uverify.backend.extension.utils.ConnectedGoodUtils.applySHA3_256;
import static io.uverify.backend.util.CardanoUtils.fromCardanoNetwork;

@Slf4j
@ConditionalOnProperty(value = "extensions.connected-goods.enabled", havingValue = "true")
@RestController
@SuppressWarnings("unused")
@RequestMapping("/api/v1/extension/connected-goods")
public class ConnectedGoodsController {

    @Autowired
    private final ConnectedGoodsService connectedGoodsService;
    private final CardanoNetwork network;

    public ConnectedGoodsController(@Value("${cardano.network}") String network, ConnectedGoodsService connectedGoodsService) {
        this.connectedGoodsService = connectedGoodsService;
        this.network = CardanoNetwork.valueOf(network);
    }

    // TODO: Remove it in the future and replace it by proper UVerify certificate re-minting strategies
    private String correctBatchIds(String batchId) {
        return switch (batchId) {
            case "0ef210c95e39bbfc1f3bff7354f05e02d6ef751aedb6087df684ad655d2fb13b" ->
                    "189b6ba68788f9e4806aecac2724ce0cfa92553ea384d2c1607b8af383598860";
            case "2898c9849a49f216e0c389040eda20610819c1e4b64b9d41b2137bc54f374455" ->
                    "c78ef5bed568c856a113e16f559feb73b2cd3c161561fb97ae91d0450c552571";
            case "f696dd2126597392fdb4f564af149f14c2df75544e9a844152ccfd054dae57f5" ->
                    "1d6f25308d25e3d955ced55025c484d178f1fa6b7932e86a74bbd9ee790b6e22";
            default -> batchId;
        };
    }

    @GetMapping("/{batchIds}/{itemId}")
    public ResponseEntity<?> getConnectedGood(@PathVariable String batchIds, @PathVariable String itemId) {
        batchIds = correctBatchIds(batchIds);

        SocialHubEntity socialHubEntity = connectedGoodsService.getSocialHubByBatchIdsAndItemId(batchIds, itemId);
        try {
            return ResponseEntity.ok(
                    connectedGoodsService.decryptSocialHub(
                            fromSocialHubEntity(socialHubEntity, fromCardanoNetwork(network)), itemId));
        } catch (Exception exception) {
            log.error("Error decrypting social hub: {}", exception.getMessage(), exception);
            return ResponseEntity.badRequest().body("Error while decrypting social hub. Please check the provided item id and try again.");
        }
    }

    @PostMapping("/mint/batch")
    @Operation(
            summary = "Returns an unsigned transaction to mint a batch of connected goods",
            description = """
                    Returns an unsigned transaction to mint a batch of connected goods.
                    The transaction needs to be signed by a user wallet.
                    The request contains a list of connected goods, each with an asset name and a unique claiming password.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "transaction successfully created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuildTransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or unknown action type",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuildTransactionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<MintConnectedGoodsResponse> mintConnectedGoods(@RequestBody @NotNull MintConnectedGoodsRequest mintConnectedGoodsRequest) {
        try {
            return ResponseEntity.ok(connectedGoodsService.mint(mintConnectedGoodsRequest.getTokenName(), mintConnectedGoodsRequest.getItems(), mintConnectedGoodsRequest.getAddress()));
        } catch (Exception exception) {
            MintConnectedGoodsResponse response = new MintConnectedGoodsResponse();
            log.error("Error building transaction: {}", exception.getMessage(), exception);
            response.setStatus(HttpStatus.BAD_REQUEST);
            response.setError(exception.getMessage());
            response.setMessage("Transaction building failed. Please check the request and try again.");
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/claim/item")
    public ResponseEntity<?> claimItem(@RequestBody @NotNull ClaimUpdateConnectedGoodsRequest claimUpdateConnectedGoodsRequest) {
        String batchId = correctBatchIds(claimUpdateConnectedGoodsRequest.getBatchId());

        try {
            SocialHubEntity socialHubEntity = connectedGoodsService.getSocialHubByBatchIdAndMintHash(
                    batchId,
                    applySHA3_256(claimUpdateConnectedGoodsRequest.getPassword())
            );
            SocialHub socialHub = claimUpdateConnectedGoodsRequest.getSocialHub();

            if (socialHub.getOwner().startsWith("addr")) {
                Address address = new Address(claimUpdateConnectedGoodsRequest.getUserAddress());
                Optional<Credential> optionalCredential = address.getPaymentCredential();

                if (optionalCredential.isEmpty()) {
                    return ResponseEntity.badRequest().body("Invalid user address");
                }

                socialHub.setOwner(HexUtil.encodeHexString(optionalCredential.get().getBytes()));
            }
            ConnectedGoodEntity connectedGood = socialHubEntity.getConnectedGood();
            ConnectedGoodUpdateEntity latestConnectedGoodUpdate = connectedGoodsService.getLatestUpdateByConnectedGoodId(connectedGood.getId());

            Transaction transaction = connectedGoodsService.claim(
                    claimUpdateConnectedGoodsRequest.getSocialHub().getItemName(),
                    claimUpdateConnectedGoodsRequest.getPassword(),
                    latestConnectedGoodUpdate.getTransactionId(),
                    latestConnectedGoodUpdate.getOutputIndex(),
                    socialHub.toSocialHubDatum(batchId),
                    claimUpdateConnectedGoodsRequest.getUserAddress()
            );
            return ResponseEntity.ok(transaction.serializeToHex());
        } catch (Exception exception) {
            log.error("Error building transaction: {}", exception.getMessage(), exception);
            return ResponseEntity.badRequest().body("Error while building transaction. Please check the request and try again.");
        }
    }

    @PostMapping("/update/item")
    public ResponseEntity<?> updateItem(@RequestBody @NotNull ClaimUpdateConnectedGoodsRequest claimUpdateConnectedGoodsRequest) {
        String batchId = correctBatchIds(claimUpdateConnectedGoodsRequest.getBatchId());

        try {
            SocialHubEntity socialHubEntity = connectedGoodsService.getSocialHubByBatchIdAndMintHash(
                    batchId,
                    applySHA3_256(claimUpdateConnectedGoodsRequest.getPassword())
            );
            SocialHub socialHub = claimUpdateConnectedGoodsRequest.getSocialHub();

            if (socialHub.getOwner().startsWith("addr")) {
                Address address = new Address(claimUpdateConnectedGoodsRequest.getUserAddress());
                Optional<Credential> optionalCredential = address.getPaymentCredential();

                if (optionalCredential.isEmpty()) {
                    return ResponseEntity.badRequest().body("Invalid user address");
                }

                socialHub.setOwner(HexUtil.encodeHexString(optionalCredential.get().getBytes()));
            }

            Transaction transaction = connectedGoodsService.update(
                    socialHub.toSocialHubDatum(batchId),
                    socialHubEntity.getTransactionId(),
                    socialHubEntity.getOutputIndex(),
                    claimUpdateConnectedGoodsRequest.getUserAddress(),
                    claimUpdateConnectedGoodsRequest.getPassword()
            );
            return ResponseEntity.ok(transaction.serializeToHex());
        } catch (Exception exception) {
            log.error("Error building transaction: {}", exception.getMessage(), exception);
            return ResponseEntity.badRequest().body("Error while building transaction. Please check the request and try again.");
        }
    }
}
