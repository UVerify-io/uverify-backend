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

package io.uverify.backend.entity;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.script.domain.TxScript;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.model.BootstrapDatum;
import io.uverify.backend.util.ValidatorUtils;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bootstrap_datum")
public class BootstrapDatumEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "bootstrap_datum_id")
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserCredentialEntity> allowedCredentials;

    @Column(name = "authorization_token_script_hash", nullable = false)
    private String authorizationTokenScriptHash;

    @Column(name = "token_name", nullable = false, unique = true)
    private String tokenName;

    @Column(name = "update_token_contract_credential", nullable = false)
    private String updateTokenContractCredential;

    @Column(name = "fee", nullable = false)
    private Integer fee;

    @Column(name = "fee_interval", nullable = false)
    private Integer feeInterval;

    @JoinColumn(name = "bootstrap_datum_id")
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FeeReceiverEntity> feeReceivers;

    @Column(name = "ttl", nullable = false)
    private Long ttl;

    @Column(name = "transaction_limit", nullable = false)
    private Integer transactionLimit;

    @Column(name = "creation_slot", nullable = false)
    private Long creationSlot;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "batch_size", nullable = false)
    private Integer batchSize;

    @Column(name = "invalidation_slot")
    private Long invalidationSlot;

    @Column(name = "version", nullable = false)
    private Integer version;

    public static BootstrapDatumEntity fromAddressUtxo(AddressUtxo addressUtxo, CardanoNetwork network) {
        BootstrapDatum bootstrapDatum = BootstrapDatum.fromLegancyUtxoDatum(addressUtxo.getInlineDatum());
        String transactionId = addressUtxo.getTxHash();
        long slot = addressUtxo.getSlot();

        return BootstrapDatumEntity.fromBootstrapDatum(bootstrapDatum, transactionId, slot, network, 1);
    }

    public static BootstrapDatumEntity fromTxScript(TxScript txScript, CardanoNetwork network) {
        BootstrapDatum bootstrapDatum = BootstrapDatum.fromScriptTxDatum(txScript.getDatum());
        String transactionId = txScript.getTxHash();
        long slot = txScript.getSlot();

        return BootstrapDatumEntity.fromBootstrapDatum(bootstrapDatum, transactionId, slot, network, 2);
    }

    private static BootstrapDatumEntity fromBootstrapDatum(BootstrapDatum bootstrapDatum, String transactionId, long slot,
                                                           CardanoNetwork network, int version) {
        List<UserCredentialEntity> userCredentialEntities = bootstrapDatum.getAllowedCredentials().stream()
                .map(credential -> UserCredentialEntity.builder()
                        .credential(HexUtil.encodeHexString(credential))
                        .build())
                .toList();

        List<FeeReceiverEntity> feeReceiverEntities = bootstrapDatum.getFeeReceivers().stream()
                .map(credential -> FeeReceiverEntity.builder()
                        .credential(HexUtil.encodeHexString(credential))
                        .build())
                .toList();

        return BootstrapDatumEntity.builder()
                .allowedCredentials(userCredentialEntities)
                .authorizationTokenScriptHash(ValidatorUtils.getMintOrBurnAuthTokenHash(network))
                .tokenName(bootstrapDatum.getTokenName())
                .updateTokenContractCredential(ValidatorUtils.getUpdateStateTokenHash(network))
                .fee(bootstrapDatum.getFee())
                .feeInterval(bootstrapDatum.getFeeInterval())
                .feeReceivers(feeReceiverEntities)
                .ttl(bootstrapDatum.getTtl())
                .transactionLimit(bootstrapDatum.getTransactionLimit())
                .creationSlot(slot)
                .transactionId(transactionId)
                .batchSize(bootstrapDatum.getBatchSize())
                .version(version)
                .build();
    }
}
