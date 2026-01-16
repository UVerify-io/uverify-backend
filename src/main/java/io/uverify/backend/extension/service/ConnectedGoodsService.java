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

package io.uverify.backend.extension.service;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.Constants;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.transaction.storage.impl.model.TxnEntity;
import io.uverify.backend.dto.UsageStatistics;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.enums.UseCaseCategory;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.extension.UVerifyServiceExtension;
import io.uverify.backend.extension.dto.Item;
import io.uverify.backend.extension.dto.MintConnectedGoodsResponse;
import io.uverify.backend.extension.dto.SocialHub;
import io.uverify.backend.extension.entity.ConnectedGoodEntity;
import io.uverify.backend.extension.entity.ConnectedGoodUpdateEntity;
import io.uverify.backend.extension.entity.SocialHubEntity;
import io.uverify.backend.extension.repository.ConnectedGoodUpdateRepository;
import io.uverify.backend.extension.repository.ConnectedGoodsRepository;
import io.uverify.backend.extension.repository.SocialHubRepository;
import io.uverify.backend.extension.validators.ConnectedGoodsDatum;
import io.uverify.backend.extension.validators.ConnectedGoodsDatumItem;
import io.uverify.backend.extension.validators.SocialHubDatum;
import io.uverify.backend.extension.validators.SocialHubRedeemer;
import io.uverify.backend.extension.validators.converter.ConnectedGoodsDatumConverter;
import io.uverify.backend.extension.validators.converter.ConnectedGoodsDatumItemConverter;
import io.uverify.backend.extension.validators.converter.SocialHubDatumConverter;
import io.uverify.backend.extension.validators.converter.SocialHubRedeemerConverter;
import io.uverify.backend.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.*;

import static io.uverify.backend.extension.utils.ConnectedGoodUtils.*;
import static io.uverify.backend.util.CardanoUtils.fromCardanoNetwork;

@Service
@Slf4j
@ConditionalOnProperty(value = "extensions.connected-goods.enabled", havingValue = "true")
public class ConnectedGoodsService implements UVerifyServiceExtension {

    private static final int CTR_IV_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;
    @Autowired
    private final ConnectedGoodsRepository connectedGoodsRepository;
    @Autowired
    private final ConnectedGoodUpdateRepository connectedGoodUpdateRepository;
    @Autowired
    private final SocialHubRepository socialHubRepository;

    @Autowired
    private final TransactionRepository transactionRepository;

    private final Network network;
    private final String salt;
    private BackendService backendService;

    @Autowired
    public ConnectedGoodsService(
            @Value("${cardano.backend.service.type}") String cardanoBackendServiceType,
            @Value("${cardano.backend.blockfrost.baseUrl}") String blockfrostBaseUrl,
            @Value("${cardano.backend.blockfrost.projectId}") String blockfrostProjectId,
            @Value("${extensions.connected-goods.encryption.salt}") String salt,
            @Value("${cardano.network}") String network,
            ConnectedGoodsRepository connectedGoodsRepository, SocialHubRepository socialHubRepository,
            @Autowired ExtensionManager extensionManager, ConnectedGoodUpdateRepository connectedGoodUpdateRepository,
            @Autowired TransactionRepository transactionRepository) {
        this.connectedGoodsRepository = connectedGoodsRepository;
        this.connectedGoodUpdateRepository = connectedGoodUpdateRepository;
        this.socialHubRepository = socialHubRepository;
        this.transactionRepository = transactionRepository;
        this.salt = salt;
        this.network = fromCardanoNetwork(CardanoNetwork.valueOf(network));
        extensionManager.registerExtension(this);

        if (cardanoBackendServiceType.equals("blockfrost")) {
            if (blockfrostProjectId == null || blockfrostProjectId.isEmpty()) {
                throw new IllegalArgumentException("Blockfrost projectId is required when using Blockfrost backend service");
            }

            this.backendService = new BFBackendService(
                    blockfrostBaseUrl,
                    blockfrostProjectId);
        } else if (cardanoBackendServiceType.equals("koios")) {
            this.backendService = new KoiosBackendService(Constants.KOIOS_PREPROD_URL);
        }
    }

    public void setBackendService(BackendService backendService) {
        this.backendService = backendService;
    }

    public SocialHubEntity getSocialHubByBatchIdAndMintHash(String batchId, String mintHash) {
        Optional<SocialHubEntity> socialHubEntity = socialHubRepository.findByBatchIdAndMintHash(batchId, mintHash);
        return socialHubEntity.orElse(null);
    }

    public SocialHubEntity getSocialHubByBatchIdsAndItemId(String batchIds, String itemId) {
        Optional<SocialHubEntity> socialHubEntity = socialHubRepository.findByBatchIdsAndMintHash(Arrays.stream(batchIds.split(",")).toList(), applySHA3_256(itemId));
        return socialHubEntity.orElse(null);
    }

    public List<AddressUtxo> processAddressUtxos(List<AddressUtxo> addressUtxos) {
        List<AddressUtxo> processedUtxos = new ArrayList<>();
        for (AddressUtxo addressUtxo : addressUtxos) {
            try {
                if (includesConnectedGoodsToken(addressUtxo)) {
                    ConnectedGoodsDatum connectedGoodsDatum = new ConnectedGoodsDatumConverter().deserialize(addressUtxo.getInlineDatum());
                    String transactionId = addressUtxo.getTxHash();
                    long slot = addressUtxo.getSlot();

                    String batchId = HexUtil.encodeHexString(connectedGoodsDatum.getId());
                    Optional<ConnectedGoodEntity> entry = connectedGoodsRepository.findById(batchId);

                    ConnectedGoodEntity connectedGoodEntity;
                    ConnectedGoodUpdateEntity connectedGoodUpdateEntity = new ConnectedGoodUpdateEntity();
                    connectedGoodUpdateEntity.setSlot(slot);
                    connectedGoodUpdateEntity.setTransactionId(transactionId);
                    connectedGoodUpdateEntity.setOutputIndex(addressUtxo.getOutputIndex());
                    if (entry.isEmpty()) {
                        connectedGoodEntity = new ConnectedGoodEntity();
                        connectedGoodEntity.setId(batchId);
                        connectedGoodEntity.setCreationSlot(slot);

                        List<SocialHubEntity> socialHubEntities = new ArrayList<>();
                        for (ConnectedGoodsDatumItem item : connectedGoodsDatum.getItems()) {
                            SocialHubEntity socialHubEntity = new SocialHubEntity();
                            socialHubEntity.setPassword(HexUtil.encodeHexString(item.getPassword()));
                            socialHubEntity.setAssetId(item.getTokenName());
                            socialHubEntity.setTransactionId(transactionId);
                            socialHubEntity.setOutputIndex(addressUtxo.getOutputIndex());
                            socialHubEntity.setCreationSlot(slot);
                            socialHubEntities.add(socialHubEntity);
                        }

                        connectedGoodEntity.setUpdates(List.of(connectedGoodUpdateEntity));
                        connectedGoodEntity.setSocialHubEntities(socialHubEntities);
                    } else {
                        connectedGoodEntity = entry.get();
                        connectedGoodEntity.getUpdates().add(connectedGoodUpdateEntity);

                    }
                    connectedGoodsRepository.save(connectedGoodEntity);
                    processedUtxos.add(addressUtxo);
                }

                if (includesSocialHubToken(addressUtxo)) {
                    SocialHubDatum socialHubDatum = new SocialHubDatumConverter().deserialize(addressUtxo.getInlineDatum());
                    String transactionId = addressUtxo.getTxHash();

                    String batchId = HexUtil.encodeHexString(socialHubDatum.getBatchId());
                    String tokenName = getSocialHubTokenName(addressUtxo);

                    if (tokenName != null) {
                        Optional<SocialHubEntity> optionalSocialHub = socialHubRepository.findByBatchIdAndAssetId(batchId, tokenName);

                        if (optionalSocialHub.isPresent()) {
                            SocialHubEntity socialHubEntity = SocialHubEntity.fromSocialHubDatum(socialHubDatum);

                            socialHubEntity.setTransactionId(transactionId);
                            socialHubEntity.setOutputIndex(addressUtxo.getOutputIndex());
                            socialHubEntity.setCreationSlot(addressUtxo.getSlot());

                            SocialHubEntity previousSocialHubEntity = optionalSocialHub.get();
                            socialHubEntity.setPassword(previousSocialHubEntity.getPassword());
                            socialHubEntity.setAssetId(previousSocialHubEntity.getAssetId());
                            socialHubEntity.setConnectedGood(previousSocialHubEntity.getConnectedGood());
                            socialHubRepository.save(socialHubEntity);
                            processedUtxos.add(addressUtxo);
                        } else {
                            log.warn("SocialHub not found for batchId: {} and tokenName: {}", batchId, tokenName);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return processedUtxos;
    }

    public void handleRollbackToSlot(long slot) {
        connectedGoodsRepository.deleteAllAfterSlot(slot);
        connectedGoodUpdateRepository.deleteAllAfterSlot(slot);
        socialHubRepository.deleteAllAfterSlot(slot);
    }

    @Override
    public void addUsageStatistics(UsageStatistics usageStatistics) {
        List<ConnectedGoodEntity> registeredBatches = connectedGoodsRepository.findAll();
        List<ConnectedGoodUpdateEntity> claimedItems = connectedGoodUpdateRepository.findAll();
        usageStatistics.addCertificatesToCategory(UseCaseCategory.CONNECTED_GOODS, registeredBatches.size() + claimedItems.size());
    }

    @Override
    public BigInteger addTransactionFees(BigInteger totalFees) {
        // registered batches are already included in the certificate transaction fee itself
        List<ConnectedGoodUpdateEntity> claimedItems = connectedGoodUpdateRepository.findAll();
        for (ConnectedGoodUpdateEntity claimedItem : claimedItems) {
            Optional<TxnEntity> transaction = transactionRepository.findById(claimedItem.getTransactionId());
            if (transaction.isPresent()) {
                totalFees = totalFees.add(transaction.get().getFee());
            } else {
                log.warn("Transaction not found for UTXO with hash: {}", claimedItem.getTransactionId());
            }
        }

        return totalFees;
    }

    private SecretKey generateKey(String key) throws Exception {
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(key.toCharArray(), saltBytes, ITERATION_COUNT, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    private Optional<byte[]> encrypt(Optional<byte[]> plaintext, String key) throws Exception {
        if (plaintext.isPresent()) {
            return Optional.of(encrypt(plaintext.get(), key));
        } else {
            return Optional.empty();
        }
    }

    public byte[] encrypt(byte[] plaintext, String key) throws Exception {
        byte[] iv = new byte[CTR_IV_LENGTH];
        SecureRandom random = SecureRandom.getInstanceStrong();
        random.nextBytes(iv);

        SecretKey secretKey = generateKey(key);

        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));

        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] encrypted = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, encrypted, 0, iv.length);
        System.arraycopy(ciphertext, 0, encrypted, iv.length, ciphertext.length);

        return encrypted;
    }

    private String decrypt(byte[] encryptedText, String key) throws Exception {
        if (encryptedText != null && encryptedText.length > CTR_IV_LENGTH) {
            return decryptString(encryptedText, key);
        } else {
            return null;
        }
    }

    private String decryptString(byte[] encryptedText, String key) throws Exception {
        byte[] iv = Arrays.copyOfRange(encryptedText, 0, CTR_IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(encryptedText, CTR_IV_LENGTH, encryptedText.length);

        SecretKey secretKey = generateKey(key);

        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));

        byte[] decrypted = cipher.doFinal(ciphertext);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private SocialHubDatum encryptSocialHub(SocialHubDatum plainSocialHub, String password) throws Exception {
        SocialHubDatum encryptedSocialHub = new SocialHubDatum();
        encryptedSocialHub.setOwner(plainSocialHub.getOwner());
        encryptedSocialHub.setBatchId(plainSocialHub.getBatchId());
        encryptedSocialHub.setName(encrypt(plainSocialHub.getName(), password));
        encryptedSocialHub.setSubtitle(encrypt(plainSocialHub.getSubtitle(), password));
        encryptedSocialHub.setX(encrypt(plainSocialHub.getX(), password));
        encryptedSocialHub.setTelegram(encrypt(plainSocialHub.getTelegram(), password));
        encryptedSocialHub.setDiscord(encrypt(plainSocialHub.getDiscord(), password));
        encryptedSocialHub.setYoutube(encrypt(plainSocialHub.getYoutube(), password));
        encryptedSocialHub.setWebsite(encrypt(plainSocialHub.getWebsite(), password));
        encryptedSocialHub.setEmail(encrypt(plainSocialHub.getEmail(), password));
        encryptedSocialHub.setAdahandle(encrypt(plainSocialHub.getAdahandle(), password));
        encryptedSocialHub.setReddit(encrypt(plainSocialHub.getReddit(), password));
        encryptedSocialHub.setInstagram(encrypt(plainSocialHub.getInstagram(), password));
        encryptedSocialHub.setGithub(encrypt(plainSocialHub.getGithub(), password));
        encryptedSocialHub.setLinkedin(encrypt(plainSocialHub.getLinkedin(), password));
        encryptedSocialHub.setPicture(encrypt(plainSocialHub.getPicture(), password));
        return encryptedSocialHub;
    }

    public SocialHub decryptSocialHub(SocialHub encryptedSocialHub, String password) throws Exception {
        SocialHub decryptedSocialHub = new SocialHub();
        decryptedSocialHub.setOwner(encryptedSocialHub.getOwner());
        decryptedSocialHub.setItemName(encryptedSocialHub.getItemName());
        decryptedSocialHub.setName(decrypt(encryptedSocialHub.asBinaryName(), password));
        decryptedSocialHub.setSubtitle(decrypt(encryptedSocialHub.asBinarySubtitle(), password));
        decryptedSocialHub.setX(decrypt(encryptedSocialHub.asBinaryX(), password));
        decryptedSocialHub.setTelegram(decrypt(encryptedSocialHub.asBinaryTelegram(), password));
        decryptedSocialHub.setDiscord(decrypt(encryptedSocialHub.asBinaryDiscord(), password));
        decryptedSocialHub.setYoutube(decrypt(encryptedSocialHub.asBinaryYoutube(), password));
        decryptedSocialHub.setWebsite(decrypt(encryptedSocialHub.asBinaryWebsite(), password));
        decryptedSocialHub.setEmail(decrypt(encryptedSocialHub.asBinaryEmail(), password));
        decryptedSocialHub.setAdaHandle(decrypt(encryptedSocialHub.asBinaryAdahandle(), password));
        decryptedSocialHub.setReddit(decrypt(encryptedSocialHub.asBinaryReddit(), password));
        decryptedSocialHub.setInstagram(decrypt(encryptedSocialHub.asBinaryInstagram(), password));
        decryptedSocialHub.setGithub(decrypt(encryptedSocialHub.asBinaryGithub(), password));
        decryptedSocialHub.setLinkedin(decrypt(encryptedSocialHub.asBinaryLinkedin(), password));
        decryptedSocialHub.setPicture(decrypt(encryptedSocialHub.asBinaryPicture(), password));
        return decryptedSocialHub;
    }

    public Transaction claim(String assetName, String password, String transactionId, int outputIndex, SocialHubDatum socialHubDatum, String userAddress) throws ApiException {
        Result<Utxo> output = backendService.getUtxoService().getTxOutput(transactionId, outputIndex);
        Utxo utxo = output.getValue();

        ConnectedGoodsDatum inputDatum = new ConnectedGoodsDatumConverter().deserialize(utxo.getInlineDatum());
        inputDatum.setItems(inputDatum.getItems().stream().filter(item -> !item.getTokenName().equals(assetName)).toList());
        ConstrPlutusData datum = new ConnectedGoodsDatumConverter().toPlutusData(inputDatum);

        String scriptAddress = AddressProvider.getEntAddress(connectedGoodsScript, network).toBech32();
        ConnectedGoodsDatumItem connectedGood = new ConnectedGoodsDatumItem(assetName, password.getBytes());
        ConstrPlutusData redeemer = new ConnectedGoodsDatumItemConverter().toPlutusData(connectedGood);

        Address address = new Address(userAddress);
        byte[] ownerCredential = address.getPaymentCredentialHash().orElseThrow();

        socialHubDatum.setOwner(ownerCredential);
        socialHubDatum.setBatchId(inputDatum.getId());

        String socialHubScriptAddress = AddressProvider.getEntAddress(socialHubScript, network).toBech32();
        Asset asset = Asset.builder()
                .name(connectedGood.getTokenName())
                .value(BigInteger.ONE)
                .build();

        SocialHubDatum encryptedSocialHub;
        try {
            encryptedSocialHub = encryptSocialHub(socialHubDatum, password);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ApiException("Failed to encrypt social hub data", exception);
        }

        ScriptTx transaction = new ScriptTx()
                .mintAsset(socialHubScript, List.of(asset),
                        PlutusData.unit(),
                        socialHubScriptAddress, new SocialHubDatumConverter().toPlutusData(encryptedSocialHub))
                .attachSpendingValidator(connectedGoodsScript)
                .collectFrom(utxo, redeemer)
                .payToContract(scriptAddress, utxo.getAmount(), datum);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        return quickTxBuilder.compose(transaction)
                .collateralPayer(userAddress)
                .feePayer(userAddress)
                .withRequiredSigners(address)
                .build();
    }

    public MintConnectedGoodsResponse mint(String tokenName, List<Item> items, String userAddress) throws CborSerializationException {
        Map<String, String> itemMap = new HashMap<>();
        for (Item item : items) {
            itemMap.put(item.getAssetName(), item.getPassword());
        }
        return mint(tokenName, itemMap, userAddress);
    }

    public MintConnectedGoodsResponse mint(String tokenName, Map<String, String> items, String userAddress) throws CborSerializationException {
        List<ConnectedGoodsDatumItem> connectedGoods = new ArrayList<>();
        items.forEach((assetName, password) -> connectedGoods.add(new ConnectedGoodsDatumItem(assetName, HexUtil.decodeHexString(applySHA3_256(password)))));

        UtxoService utxoService = backendService.getUtxoService();
        DefaultUtxoSupplier utxoSupplier = new DefaultUtxoSupplier(utxoService);

        List<Utxo> utxos = utxoSupplier.getAll(userAddress);
        if (utxos.isEmpty()) {
            throw new IllegalStateException("No UTxOs found for facilitator account");
        }

        // TODO: find UTXO with at least 2 ADA but also the smallest UTXO
        Optional<Utxo> optionalUtxo = utxos.stream().filter(utxo -> {
            Amount lovelace = utxo.getAmount().stream()
                    .filter(amount -> amount.getUnit().equals("lovelace")).findFirst().orElse(null);

            if (lovelace == null) {
                return false;
            }

            return lovelace.getQuantity().compareTo(BigInteger.valueOf(3_000_000)) > 0;
        }).findFirst();

        if (optionalUtxo.isEmpty()) {
            throw new IllegalStateException("No UTxOs found with at least 3 ADA");
        }

        Utxo utxo = optionalUtxo.get();
        String batchId = applySHA3_256(HexUtil.decodeHexString(utxo.getTxHash() + HexUtil.encodeHexString(String.valueOf(utxo.getOutputIndex()).getBytes())));
        ConnectedGoodsDatum connectedGoodsDatum = new ConnectedGoodsDatum(
                HexUtil.decodeHexString(batchId), connectedGoods);
        ConstrPlutusData datum = new ConnectedGoodsDatumConverter().toPlutusData(connectedGoodsDatum);

        String scriptAddress = AddressProvider.getEntAddress(connectedGoodsScript, network).toBech32();
        Asset asset = Asset.builder()
                .name(tokenName)
                .value(BigInteger.ONE)
                .build();

        ScriptTx mintTransaction = new ScriptTx()
                .mintAsset(connectedGoodsScript, asset, PlutusData.unit());

        Tx sendTransaction = new Tx()
                .from(userAddress)
                .collectFrom(List.of(utxo))
                .payToContract(scriptAddress, List.of(Amount.ada(1.0), Amount.asset(
                                connectedGoodsScript.getPolicyId(), asset.getName(), BigInteger.ONE)),
                        datum);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Transaction unsignedTransaction = quickTxBuilder.compose(mintTransaction, sendTransaction)
                .collateralPayer(userAddress)
                .feePayer(userAddress)
                .withRequiredSigners(new Address(userAddress))
                .build();

        String unsignedTransactionHex = unsignedTransaction.serializeToHex();
        MintConnectedGoodsResponse response = new MintConnectedGoodsResponse();
        response.setUnsignedTransaction(unsignedTransactionHex);
        response.setBatchId(batchId);
        response.setStatus(HttpStatus.OK);
        return response;
    }

    private Transaction modify(SocialHubDatum socialHubDatum, String transactionId, int outputIndex,
                               String userAddress, SocialHubRedeemer socialHubRedeemer, String password) throws ApiException {
        Result<Utxo> output = backendService.getUtxoService().getTxOutput(transactionId, outputIndex);
        Utxo utxo = output.getValue();

        SocialHubDatum encryptedSocialHubDatum;
        try {
            encryptedSocialHubDatum = encryptSocialHub(socialHubDatum, password);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ApiException("Failed to encrypt social hub data", exception);
        }

        ConstrPlutusData datum = new SocialHubDatumConverter().toPlutusData(encryptedSocialHubDatum);
        ConstrPlutusData redeemer = new SocialHubRedeemerConverter().toPlutusData(socialHubRedeemer);

        String socialHubScriptAddress = AddressProvider.getEntAddress(socialHubScript, network).toBech32();
        ScriptTx transaction = new ScriptTx()
                .attachSpendingValidator(socialHubScript)
                .collectFrom(utxo, redeemer)
                .payToContract(socialHubScriptAddress, utxo.getAmount(), datum);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Address address = new Address(userAddress);

        return quickTxBuilder.compose(transaction)
                .collateralPayer(userAddress)
                .feePayer(userAddress)
                .withRequiredSigners(address)
                .build();
    }

    public Transaction update(SocialHubDatum socialHubDatum, String transactionId, int outputIndex, String userAddress, String password) throws ApiException {
        return modify(socialHubDatum, transactionId, outputIndex, userAddress, SocialHubRedeemer.UPDATE, password);
    }

    public Transaction transfer(SocialHubDatum socialHubDatum, String transactionId, int outputIndex, String userAddress, String password) throws ApiException {
        return modify(socialHubDatum, transactionId, outputIndex, userAddress, SocialHubRedeemer.TRANSFER, password);
    }

    public ConnectedGoodUpdateEntity getLatestUpdateByConnectedGoodId(String id) {
        return connectedGoodUpdateRepository.getLatestUpdateByConnectedGoodId(id);
    }
}
