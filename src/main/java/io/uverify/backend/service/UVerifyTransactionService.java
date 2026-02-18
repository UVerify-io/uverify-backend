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

import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.util.HexUtil;
import io.uverify.backend.dto.*;
import io.uverify.backend.enums.BuildStatusCode;
import io.uverify.backend.enums.TransactionType;
import io.uverify.backend.model.BootstrapDatum;
import io.uverify.backend.model.UVerifyCertificate;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class UVerifyTransactionService {

    @Autowired
    private final CardanoBlockchainService cardanoBlockchainService;
    @Autowired
    private final LibraryService libraryService;

    public Result<String> submit(String transactionHex, String witnessSetHex) throws CborDeserializationException, CborSerializationException, ApiException {
        Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(transactionHex));

        if (witnessSetHex != null && !witnessSetHex.isEmpty()) {
            TransactionWitnessSet witnessSet = TransactionWitnessSet.deserialize((Map) CborSerializationUtil.deserialize(HexUtil.decodeHexString(witnessSetHex)));
            if (transaction.getWitnessSet() == null) {
                transaction.setWitnessSet(witnessSet);
            } else {
                transaction.getWitnessSet().setVkeyWitnesses(witnessSet.getVkeyWitnesses());
            }
        }

        return cardanoBlockchainService.submitTransaction(transaction);
    }

    public BuildTransactionResponse buildUVerifyTransaction(List<CertificateData> certificates, String address) {
        List<UVerifyCertificate> uVerifyCertificates = certificates.stream()
                .map(certificate -> UVerifyCertificate.fromCertificateData(certificate, address))
                .toList();
        try {
            Transaction transaction = cardanoBlockchainService.persistUVerifyCertificates(address, uVerifyCertificates);
            return BuildTransactionResponse.builder()
                    .unsignedTransaction(transaction.serializeToHex())
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.SUCCESS)
                            .build())
                    .type(TransactionType.DEFAULT)
                    .build();
        } catch (Exception exception) {
            return BuildTransactionResponse.builder()
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.ERROR)
                            .message(exception.getMessage())
                            .build())
                    .type(TransactionType.DEFAULT)
                    .build();
        }
    }

    public ProxyInitResponse buildInitProxyTx() {
        ProxyInitResponse proxyInitResponse;
        try {
            proxyInitResponse = cardanoBlockchainService.initProxyContract();
        } catch (ApiException | CborSerializationException e) {
            proxyInitResponse = ProxyInitResponse.builder()
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.ERROR)
                            .message(e.getMessage())
                            .build()).build();
        }
        return proxyInitResponse;
    }

    public BuildTransactionResponse buildBootstrapDatum(BootstrapData bootstrapData) {
        BootstrapDatum bootstrapDatum = BootstrapDatum.fromBootstrapData(bootstrapData);
        try {
            Transaction transaction = cardanoBlockchainService.mintProxyBootstrapDatum(bootstrapDatum);
            return BuildTransactionResponse.builder()
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.SUCCESS)
                            .build())
                    .unsignedTransaction(transaction.serializeToHex())
                    .type(TransactionType.BOOTSTRAP)
                    .build();
        } catch (Exception exception) {
            return BuildTransactionResponse.builder()
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.ERROR)
                            .message(exception.getMessage())
                            .build())
                    .type(TransactionType.BOOTSTRAP)
                    .build();
        }
    }

    public BuildTransactionResponse buildCustomTransaction(List<CertificateData> certificates, String address, String bootstrapDatumName) {
        List<UVerifyCertificate> uVerifyCertificates = certificates.stream()
                .map(certificate -> UVerifyCertificate.fromCertificateData(certificate, address))
                .toList();
        if (bootstrapDatumName != null && !bootstrapDatumName.isEmpty()) {
            try {
                Transaction transaction = cardanoBlockchainService.updateStateDatum(address, uVerifyCertificates, bootstrapDatumName);
                return BuildTransactionResponse.builder()
                        .unsignedTransaction(transaction.serializeToHex())
                        .status(BuildStatus.builder()
                                .code(BuildStatusCode.SUCCESS)
                                .build())
                        .type(TransactionType.CUSTOM)
                        .build();
            } catch (Exception exception) {
                return BuildTransactionResponse.builder()
                        .status(BuildStatus.builder()
                                .code(BuildStatusCode.ERROR)
                                .message(exception.getMessage())
                                .build())
                        .type(TransactionType.CUSTOM)
                        .build();
            }
        }

        try {
            BuildTransactionResponse buildTransactionResponse = buildUVerifyTransaction(certificates, address);
            buildTransactionResponse.setType(TransactionType.CUSTOM);
            return buildTransactionResponse;
        } catch (Exception exception) {
            return BuildTransactionResponse.builder()
                    .status(BuildStatus.builder()
                            .code(BuildStatusCode.ERROR)
                            .message(exception.getMessage())
                            .build())
                    .type(TransactionType.CUSTOM)
                    .build();
        }
    }
}
