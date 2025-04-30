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

package io.uverify.backend.service;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.crypto.api.impl.EdDSASigningProvider;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import io.uverify.backend.dto.*;
import io.uverify.backend.entity.BootstrapDatumEntity;
import io.uverify.backend.entity.StateDatumEntity;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.enums.UserAction;
import io.uverify.backend.repository.BootstrapDatumRepository;
import io.uverify.backend.repository.StateDatumRepository;
import io.uverify.backend.util.CardanoUtils;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.cip30.AddressFormat;
import org.cardanofoundation.cip30.CIP30Verifier;
import org.cardanofoundation.cip30.Cip30VerificationResult;
import org.cardanofoundation.cip30.MessageFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static io.uverify.backend.util.CardanoUtils.fromCardanoNetwork;

@Slf4j
@Service
public class UserStateService {

    private final Account facilitator;
    private final CardanoNetwork network;

    @Autowired
    StateDatumRepository stateDatumRepository;

    @Autowired
    BootstrapDatumRepository bootstrapDatumRepository;

    @Autowired
    CardanoBlockchainService cardanoBlockchainService;

    @Autowired
    public UserStateService(@Value("${cardano.facilitator.user.mnemonic}") String facilitatorAccountMnemonic,
                            @Value("${cardano.network}") String network) {
        this.network = CardanoNetwork.valueOf(network);
        this.facilitator = new Account(fromCardanoNetwork(this.network), facilitatorAccountMnemonic);
    }

    private UserActionResponse buildUserActionResponse(String address, UserAction action) {
        return buildUserActionResponse(address, action, null);
    }

    private UserActionResponse buildUserActionResponse(String address, UserAction action, String stateId) {
        long timestamp = System.currentTimeMillis();
        String actionName = action.name();
        if (stateId != null) {
            actionName += ":" + stateId;
        }

        String message = "[" + actionName + "@" + timestamp + "] Please sign this message with your private key to verify, " +
                "that you are the owner of the address " + address + ".";
        EdDSASigningProvider edDSASigningProvider = new EdDSASigningProvider();
        String signature = HexUtil.encodeHexString(
                edDSASigningProvider.signExtended(message.getBytes(StandardCharsets.UTF_8), facilitator.privateKeyBytes()));
        return UserActionResponse.builder()
                .address(address)
                .action(action)
                .signature(signature)
                .timestamp(timestamp)
                .message(message)
                .status(HttpStatus.OK)
                .build();
    }


    public UserActionResponse requestUserInfo(String address) {
        return buildUserActionResponse(address, UserAction.USER_INFO);
    }

    public UserActionResponse requestInvalidateState(String address, String stateId) {
        return buildUserActionResponse(address, UserAction.INVALIDATE_STATE, stateId);
    }

    public UserActionResponse requestOptOut(String address) {
        return buildUserActionResponse(address, UserAction.OPT_OUT);
    }

    private boolean signaturesAreValid(ExecuteUserActionRequest actionRequest) {
        EdDSASigningProvider edDSASigningProvider = new EdDSASigningProvider();

        String actionName = actionRequest.getAction().name();
        if (actionRequest.getStateId() != null) {
            actionName += ":" + actionRequest.getStateId();
        }

        String message = "[" + actionName + "@" + actionRequest.getTimestamp() + "] Please sign this message with your private key to verify, " +
                "that you are the owner of the address " + actionRequest.getAddress() + ".";

        if (!message.equals(actionRequest.getMessage())) {
            return false;
        }

        CIP30Verifier cip30Verifier = new CIP30Verifier(actionRequest.getUserSignature(), actionRequest.getUserPublicKey());
        Cip30VerificationResult cip30VerificationResult = cip30Verifier.verify();
        Optional<String> optionalUserAddress = cip30VerificationResult.getAddress(AddressFormat.TEXT);

        if (optionalUserAddress.isEmpty()) {
            return false;
        }
        // TODO: Check if timestamp is in validity range

        return edDSASigningProvider.verify(HexUtil.decodeHexString(actionRequest.getSignature()),
                actionRequest.getMessage().getBytes(), facilitator.publicKeyBytes()) &&
                cip30VerificationResult.isValid() && message.equals(cip30VerificationResult.getMessage(MessageFormat.TEXT))
                && actionRequest.getAddress().equals(optionalUserAddress.get());
    }

    public ExecuteUserActionResponse executeUserOptOut(ExecuteUserActionRequest request) {
        if (signaturesAreValid(request)) {
            String userCredential = HexUtil.encodeHexString(CardanoUtils.extractCredentialFromAddress(request.getAddress()));
            List<StateDatumEntity> stateDatumEntities = stateDatumRepository.findByOwner(userCredential);
            Address userAddress = new Address(request.getAddress());
            ExecuteUserActionResponse response = ExecuteUserActionResponse.builder().status(HttpStatus.OK).build();
            try {
                Transaction unsignedTransaction = cardanoBlockchainService.invalidateStates(userAddress, stateDatumEntities.stream().map(StateDatumEntity::getTransactionId).toList());
                response.setUnsignedTransaction(unsignedTransaction.serializeToHex());
            } catch (ApiException | CborSerializationException exception) {
                log.error("Failed to invalidate states", exception);
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                response.setError("Failed to invalidate states.");
            }
            return response;
        } else {
            return ExecuteUserActionResponse.builder().status(HttpStatus.BAD_REQUEST)
                    .error("The provided signatures are not valid.").build();
        }
    }

    public ExecuteUserActionResponse executeStateInvalidationRequest(ExecuteUserActionRequest actionRequest) {
        if (signaturesAreValid(actionRequest)) {
            Optional<StateDatumEntity> optionalStateDatum = stateDatumRepository.findById(actionRequest.getStateId());

            if (optionalStateDatum.isEmpty()) {
                return ExecuteUserActionResponse.builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .error("State with the provided ID does not exist.")
                        .build();
            }

            StateDatumEntity stateDatumEntity = optionalStateDatum.get();
            Address userAddress = new Address(stateDatumEntity.getOwner());
            ExecuteUserActionResponse response = ExecuteUserActionResponse.builder().status(HttpStatus.OK).build();
            try {
                response.setUnsignedTransaction(cardanoBlockchainService.invalidateState(userAddress, stateDatumEntity.getTransactionId()).serializeToHex());
            } catch (ApiException exception) {
                log.error("Failed to invalidate state", exception);
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                response.setError("Failed to invalidate state.");
            } catch (CborSerializationException exception) {
                log.error("Failed to serialize transaction", exception);
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                response.setError("Failed to serialize transaction.");
            }
            return response;
        }
        return ExecuteUserActionResponse.builder().status(HttpStatus.BAD_REQUEST)
                .error("The provided signatures are not valid.").build();
    }

    public ExecuteUserActionResponse executeUserInfoRequest(ExecuteUserActionRequest actionRequest) {
        if (signaturesAreValid(actionRequest)) {
            String userCredential = HexUtil.encodeHexString(CardanoUtils.extractCredentialFromAddress(actionRequest.getAddress()));
            List<BootstrapDatumEntity> bootstrapDatumEntities = bootstrapDatumRepository.findAllWhitelisted();
            List<BootstrapDatumEntity> customBootstrapDatumEntities = bootstrapDatumRepository.findByAllowedCredential(userCredential);

            bootstrapDatumEntities.addAll(customBootstrapDatumEntities);

            List<StateDatumEntity> stateDatumEntities = stateDatumRepository.findByOwner(userCredential);

            return ExecuteUserActionResponse.builder()
                    .state(UserState.builder()
                            .bootstrapDatums(bootstrapDatumEntities.stream()
                                    .map(bootstrapDatumEntity ->
                                            BootstrapData.fromBootstrapDatumEntity(bootstrapDatumEntity, network))
                                    .toList())
                            .states(stateDatumEntities.stream().map(stateDatumEntity ->
                                            StateData.fromStateDatumEntity(stateDatumEntity, network))
                                    .toList())
                            .build())
                    .status(HttpStatus.OK)
                    .build();
        }
        return ExecuteUserActionResponse.builder().status(HttpStatus.BAD_REQUEST)
                .error("The provided signatures are not valid.").build();
    }
}
