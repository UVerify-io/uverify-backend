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

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import io.uverify.backend.model.StateDatum;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "state_datum")
public class StateDatumEntity {
    @Id
    @Column(name = "id", nullable = false, unique = true)
    private String id;

    @Column(name = "owner", nullable = false)
    private String owner;

    @Column(name = "countdown", nullable = false)
    private Integer countdown;

    @Column(name = "creation_slot", nullable = false)
    private Long creationSlot;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @JoinColumn(name = "state_datum_id")
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StateDatumUpdateEntity> updates;

    @ManyToOne
    @JoinColumn(name = "bootstrap_datum_id")
    private BootstrapDatumEntity bootstrapDatum;

    @Column(name = "invalidation_slot")
    private Long invalidationSlot;

    public static StateDatumEntity fromAddressUtxo(AddressUtxo addressUtxo, BootstrapDatumEntity bootstrapDatumEntity) {
        StateDatum stateDatum = StateDatum.fromUtxoDatum(addressUtxo.getInlineDatum());
        String transactionId = addressUtxo.getTxHash();
        long slot = addressUtxo.getSlot();

        return StateDatumEntity.fromStateDatum(stateDatum, transactionId, bootstrapDatumEntity, slot);
    }

    public static StateDatumEntity fromStateDatum(StateDatum stateDatum, String transactionId, BootstrapDatumEntity bootstrapDatumEntity, long slot) {
        List<StateDatumUpdateEntity> updates = List.of(
                StateDatumUpdateEntity.builder()
                        .slot(slot)
                        .countdown(stateDatum.getCountdown())
                        .transactionId(transactionId)
                        .build()
        );
        return StateDatumEntity.builder()
                .id(stateDatum.getId())
                .owner(stateDatum.getOwner())
                .countdown(stateDatum.getCountdown())
                .creationSlot(slot)
                .updates(updates)
                .transactionId(transactionId)
                .bootstrapDatum(bootstrapDatumEntity)
                .build();
    }

    public void addUpdate(StateDatumUpdateEntity update) {
        updates.add(update);
    }
}
