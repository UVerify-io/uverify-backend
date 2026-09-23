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

package io.uverify.backend.devnet;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.backend.model.ScriptDatum;
import com.bloxbean.cardano.client.backend.model.ScriptDatumCbor;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.yaci.core.util.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.util.HexUtil;
import co.nstant.in.cbor.model.Array;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.yanoproject.testkit.devnet.YanoDevnetTestKit;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Wraps the embedded Yano {@link BackendService} and adds the one script lookup the
 * backend needs. cardano-client-lib resolves reference scripts through
 * {@link ScriptService#getPlutusScript(String)} while building fees for inputs that
 * carry a reference script, and the embedded adapter does not implement that service.
 */
@Slf4j
final class YanoDevnetBackendService implements BackendService {

    record SubmittedTransaction(String submittedHash, String hashOnChain) {
    }

    private final BackendService delegate;
    private final ScriptService scriptService;
    private final TransactionService transactionService;

    YanoDevnetBackendService(BackendService delegate, YanoDevnetTestKit kit,
                             Consumer<SubmittedTransaction> submittedTransactionListener) {
        this.delegate = delegate;
        this.scriptService = new ReferenceScriptService(kit);
        this.transactionService = new RecordingTransactionService(delegate.getTransactionService(), submittedTransactionListener);
    }

    @Override
    public AssetService getAssetService() {
        return delegate.getAssetService();
    }

    @Override
    public BlockService getBlockService() {
        return delegate.getBlockService();
    }

    @Override
    public NetworkInfoService getNetworkInfoService() {
        return delegate.getNetworkInfoService();
    }

    @Override
    public PoolService getPoolService() {
        return delegate.getPoolService();
    }

    @Override
    public TransactionService getTransactionService() {
        return transactionService;
    }

    @Override
    public UtxoService getUtxoService() {
        return delegate.getUtxoService();
    }

    @Override
    public AddressService getAddressService() {
        return delegate.getAddressService();
    }

    @Override
    public AccountService getAccountService() {
        return delegate.getAccountService();
    }

    @Override
    public EpochService getEpochService() {
        return delegate.getEpochService();
    }

    @Override
    public MetadataService getMetadataService() {
        return delegate.getMetadataService();
    }

    @Override
    public ScriptService getScriptService() {
        return scriptService;
    }

    /**
     * Remembers every submitted transaction hash so the devnet can evict transactions
     * that are still pending when a test class ends and the chain is restored.
     */
    private static final class RecordingTransactionService implements TransactionService {
        private final TransactionService delegate;
        private final Consumer<SubmittedTransaction> submittedTransactionListener;

        private RecordingTransactionService(TransactionService delegate,
                                            Consumer<SubmittedTransaction> submittedTransactionListener) {
            this.delegate = delegate;
            this.submittedTransactionListener = submittedTransactionListener;
        }

        @Override
        public Result<String> submitTransaction(byte[] cborData) throws ApiException {
            Result<String> result = delegate.submitTransaction(cborData);
            if (result.isSuccessful()) {
                String rebuiltHash = hashAfterBlockRebuild(cborData);
                if (rebuiltHash.equals(result.getValue())) {
                    log.info("Submitted transaction {}", result.getValue());
                } else {
                    log.info("Submitted transaction {} (included on chain as {})", result.getValue(), rebuiltHash);
                }
                submittedTransactionListener.accept(new SubmittedTransaction(result.getValue(), rebuiltHash));
            }
            return result;
        }

        /**
         * Yano's devnet block builder round trips every transaction through cbor-java
         * data items. That merges chunked byte strings (Plutus data longer than 64 bytes)
         * into definite ones, so the body hash of such a transaction differs from the
         * submitted hash once it is in a block. Compute that hash the same way.
         */
        private static String hashAfterBlockRebuild(byte[] cborData) {
            try {
                Array transaction = (Array) CborSerializationUtil.deserializeOne(cborData);
                byte[] rebuiltBody = CborSerializationUtil.serialize(transaction.getDataItems().get(0));
                return HexUtil.encodeHexString(Blake2bUtil.blake2bHash256(rebuiltBody));
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to rebuild transaction body", exception);
            }
        }

        @Override
        public Result<TransactionContent> getTransaction(String txnHash) throws ApiException {
            return delegate.getTransaction(txnHash);
        }

        @Override
        public Result<List<TransactionContent>> getTransactions(List<String> txnHashCollection) throws ApiException {
            return delegate.getTransactions(txnHashCollection);
        }

        @Override
        public Result<TxContentUtxo> getTransactionUtxos(String txnHash) throws ApiException {
            return delegate.getTransactionUtxos(txnHash);
        }

        @Override
        public Result<List<TxContentRedeemers>> getTransactionRedeemers(String txnHash) throws ApiException {
            return delegate.getTransactionRedeemers(txnHash);
        }

        @Override
        public Result<List<EvaluationResult>> evaluateTx(byte[] cborData) throws ApiException {
            return delegate.evaluateTx(cborData);
        }
    }

    private static final class ReferenceScriptService implements ScriptService {
        private final YanoDevnetTestKit kit;

        private ReferenceScriptService(YanoDevnetTestKit kit) {
            this.kit = kit;
        }

        @Override
        public Result<PlutusScript> getPlutusScript(String scriptHash) throws ApiException {
            Optional<byte[]> scriptRef = kit.ledger().getUtxoState().getScriptRefBytesByHash(scriptHash);
            if (scriptRef.isEmpty()) {
                return Result.<PlutusScript>error("No reference script found for hash " + scriptHash).code(404);
            }
            try {
                return Result.success("OK").withValue(PlutusScript.deserializeScriptRef(scriptRef.get())).code(200);
            } catch (Exception exception) {
                throw new ApiException("Unable to decode reference script " + scriptHash, exception);
            }
        }

        @Override
        public Result<String> getPlutusScriptCbor(String scriptHash) throws ApiException {
            Result<PlutusScript> script = getPlutusScript(scriptHash);
            if (!script.isSuccessful()) {
                return Result.<String>error(script.getResponse()).code(script.code());
            }
            return Result.success("OK").withValue(script.getValue().getCborHex()).code(200);
        }

        @Override
        public Result<ScriptDatum> getScriptDatum(String datumHash) {
            return Result.<ScriptDatum>error("Datum lookup is not available on the in-process devnet").code(404);
        }

        @Override
        public Result<ScriptDatumCbor> getScriptDatumCbor(String datumHash) {
            return Result.<ScriptDatumCbor>error("Datum lookup is not available on the in-process devnet").code(404);
        }

        @Override
        public Result<JsonNode> getNativeScriptJson(String scriptHash) {
            return Result.<JsonNode>error("Native script lookup is not available on the in-process devnet").code(404);
        }
    }
}
