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

package io.uverify.backend;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.model.TxContentUtxoOutputs;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.core.model.TransactionBody;
import com.bloxbean.cardano.yaci.core.model.Witnesses;
import com.bloxbean.cardano.yaci.core.model.serializers.TransactionBodySerializer;
import com.bloxbean.cardano.yaci.core.model.serializers.WitnessesSerializer;
import com.bloxbean.cardano.yaci.helper.model.Utxo;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.events.EventMetadata;
import com.bloxbean.cardano.yaci.store.events.TransactionEvent;
import com.bloxbean.cardano.yaci.test.Funding;
import com.bloxbean.cardano.yaci.test.YaciCardanoContainer;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.repository.BootstrapDatumRepository;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.StateDatumRepository;
import io.uverify.backend.service.*;
import io.uverify.backend.simulation.SimulationUtils;
import io.uverify.backend.util.ValidatorHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CardanoBlockchainTest {
    protected final YaciCardanoContainer yaciCardanoContainer = new YaciCardanoContainer();

    protected final Account serviceAccount;
    protected final Account userAccount;
    protected final Account feeReceiverAccount;

    protected final Account facilitatorAccount;
    @Autowired
    protected final CardanoBlockchainService cardanoBlockchainService;
    @Autowired
    protected final StateDatumService stateDatumService;
    @Autowired
    protected final BootstrapDatumService bootstrapDatumService;
    @Autowired
    protected final UVerifyCertificateService uVerifyCertificateService;

    @Autowired
    protected final StateDatumRepository stateDatumRepository;

    @Autowired
    protected final BootstrapDatumRepository bootstrapDatumRepository;

    @Autowired
    protected final CertificateRepository certificateRepository;

    @Autowired
    protected final ExtensionManager extensionManager;

    @Autowired
    protected final ValidatorHelper validatorHelper;

    @Autowired
    protected final LibraryService libraryService;

    @Autowired
    public CardanoBlockchainTest(@Value("${cardano.service.user.mnemonic}") String testServiceUserMnemonic,
                                 @Value("${cardano.test.user.mnemonic}") String testUserMnemonic,
                                 @Value("${cardano.service.fee.receiver.mnemonic}") String feeReceiverMnemonic,
                                 @Value("${cardano.facilitator.user.mnemonic}") String facilitatorMnemonic,
                                 CardanoBlockchainService cardanoBlockchainService,
                                 StateDatumService stateDatumService,
                                 BootstrapDatumService bootstrapDatumService,
                                 UVerifyCertificateService uVerifyCertificateService,
                                 StateDatumRepository stateDatumRepository,
                                 BootstrapDatumRepository bootstrapDatumRepository,
                                 CertificateRepository certificateRepository,
                                 ExtensionManager extensionManager,
                                 ValidatorHelper validatorHelper,
                                 LibraryService libraryService,
                                 List<String> additionalFundingAddresses) {
        this.cardanoBlockchainService = cardanoBlockchainService;
        this.libraryService = libraryService;
        this.stateDatumService = stateDatumService;
        this.bootstrapDatumService = bootstrapDatumService;
        this.uVerifyCertificateService = uVerifyCertificateService;
        this.stateDatumRepository = stateDatumRepository;
        this.bootstrapDatumRepository = bootstrapDatumRepository;
        this.certificateRepository = certificateRepository;
        this.extensionManager = extensionManager;
        this.validatorHelper = validatorHelper;

        serviceAccount = Account.createFromMnemonic(Networks.testnet(), testServiceUserMnemonic);
        userAccount = Account.createFromMnemonic(Networks.testnet(), testUserMnemonic);
        feeReceiverAccount = Account.createFromMnemonic(Networks.testnet(), feeReceiverMnemonic);
        facilitatorAccount = Account.createFromMnemonic(Networks.testnet(), facilitatorMnemonic);

        if (!yaciCardanoContainer.isRunning()) {
            List<Funding> fundingList = new ArrayList<>();
            fundingList.add(new Funding(serviceAccount.baseAddress(), 20000));
            fundingList.add(new Funding(serviceAccount.baseAddress(), 50000));
            fundingList.add(new Funding(serviceAccount.baseAddress(), 100000));
            fundingList.add(new Funding(serviceAccount.baseAddress(), 60000));
            fundingList.add(new Funding(userAccount.baseAddress(), 2000));
            fundingList.add(new Funding(userAccount.baseAddress(), 5));
            fundingList.add(new Funding(userAccount.baseAddress(), 1000));
            fundingList.add(new Funding(facilitatorAccount.baseAddress(), 10000));
            fundingList.add(new Funding(facilitatorAccount.baseAddress(), 10));

            for (String address : additionalFundingAddresses) {
                fundingList.add(new Funding(address, 200));
                fundingList.add(new Funding(address, 20));
            }

            Funding[] fundingArray = fundingList.toArray(new Funding[0]);

            yaciCardanoContainer
                    .withInitialFunding(fundingArray)
                    .withLogConsumer(outputFrame -> log.info(outputFrame.getUtf8String()))
                    .start();
            this.cardanoBlockchainService.setBackendService(yaciCardanoContainer.getBackendService());
            this.libraryService.setBackendService(yaciCardanoContainer.getBackendService());
        }
    }

    @AfterAll
    void tearDown() {
        yaciCardanoContainer.stop();
        certificateRepository.deleteAll();
        stateDatumRepository.deleteAll();
        bootstrapDatumRepository.deleteAll();
        this.validatorHelper.setProxy("", 0);
    }

    protected void simulateYaciStoreBehavior(String transactionId) throws InterruptedException, ApiException {
        List<AddressUtxo> addressUtxos = SimulationUtils.getAddressUtxos(transactionId, yaciCardanoContainer.getBackendService());
        cardanoBlockchainService.processAddressUtxos(addressUtxos);
        extensionManager.processAddressUtxos(addressUtxos);
        Thread.sleep(1000);
    }

    protected void simulateYaciStoreBehavior(List<AddressUtxo> addressUtxos) throws InterruptedException {
        cardanoBlockchainService.processAddressUtxos(addressUtxos);
        extensionManager.processAddressUtxos(addressUtxos);
        Thread.sleep(1000);
    }

    protected void simulateYaciStoreBehavior(String transactionId, Transaction transaction) throws InterruptedException, ApiException, AddressExcepion, CborSerializationException, CborException {
        Result<Block> latestBlock = yaciCardanoContainer.getBackendService().getBlockService().getLatestBlock();

        DataItem bodyDataItem = transaction.getBody().serialize();
        byte[] bytes = CborSerializationUtil.serialize(bodyDataItem);
        TransactionBody txBody = TransactionBodySerializer.INSTANCE.deserializeDI(bodyDataItem, bytes);

        DataItem witnessSetDataItem = transaction.getWitnessSet().serialize();
        Witnesses witnesses = WitnessesSerializer.INSTANCE.deserializeDI(witnessSetDataItem);

        Result<TxContentUtxo> transactionUtxos = yaciCardanoContainer.getBackendService().getTransactionService().getTransactionUtxos(transactionId);

        ArrayList<Utxo> utxos = new ArrayList<>();
        if (transactionUtxos.isSuccessful()) {
            List<TransactionOutput> txOutputsWithScripts = transaction.getBody().getOutputs()
                    .stream()
                    .filter(output -> output.getScriptRef() != null)
                    .toList();

            for (TxContentUtxoOutputs output : transactionUtxos.getValue().getOutputs()) {
                String scriptRef = null;
                if (output.getReferenceScriptHash() != null && !txOutputsWithScripts.isEmpty()) {
                    for (TransactionOutput txOutput : txOutputsWithScripts) {
                        PlutusScript script = PlutusScript.deserializeScriptRef(txOutput.getScriptRef());
                        if (script.getPolicyId().equals(output.getReferenceScriptHash())) {
                            scriptRef = HexUtil.encodeHexString(txOutput.getScriptRef());
                        }
                    }
                }

                utxos.add(Utxo.builder()
                        .txHash(transactionId)
                        .index(output.getOutputIndex())
                        .address(output.getAddress())
                        .inlineDatum(output.getInlineDatum())
                        .scriptRef(scriptRef)
                        .build());
            }
        }

        try {
            TransactionEvent transactionEvent = TransactionEvent.builder()
                    .metadata(EventMetadata.builder()
                            .blockHash(latestBlock.getValue().getHash())
                            .blockTime(latestBlock.getValue().getTime())
                            .epochNumber(latestBlock.getValue().getEpoch())
                            .slot(latestBlock.getValue().getSlot())
                            .parallelMode(false)
                            .build())
                    .transactions(List.of(com.bloxbean.cardano.yaci.helper.model.Transaction.builder()
                            .blockNumber(latestBlock.getValue().getHeight())
                            .txHash(transactionId)
                            .body(txBody)
                            .utxos(utxos)
                            .slot(latestBlock.getValue().getSlot())
                            .witnesses(witnesses)
                            .build())).build();

            cardanoBlockchainService.processTransactionEvent(transactionEvent);
        } catch (Exception exception) {
            log.error("Unable to process tx scripts: " + exception.getMessage());
        }

        Thread.sleep(1000);
    }
}
