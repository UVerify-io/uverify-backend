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

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import io.uverify.backend.entity.StateDatumEntity;
import io.uverify.backend.entity.StateDatumUpdateEntity;
import io.uverify.backend.model.StateDatum;
import io.uverify.backend.repository.StateDatumRepository;
import io.uverify.backend.repository.StateDatumUpdateRepository;
import io.uverify.backend.util.CardanoUtils;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class StateDatumService {

    @Autowired
    private final StateDatumRepository stateDatumRepository;

    @Autowired
    private final StateDatumUpdateRepository stateDatumUpdateRepository;

    @Autowired
    public StateDatumService(StateDatumRepository stateDatumRepository, StateDatumUpdateRepository stateDatumUpdateRepository) {
        this.stateDatumRepository = stateDatumRepository;
        this.stateDatumUpdateRepository = stateDatumUpdateRepository;
    }

    public StateDatumEntity selectCheapestStateDatum(List<StateDatumEntity> stateDatums) {
        List<StateDatumEntity> stateDatumEntities = stateDatums.stream().filter(
                stateDatumEntity -> stateDatumEntity.getInvalidationSlot() == null
                        && stateDatumEntity.getCountdown() > 0
                        && stateDatumEntity.getVersion() > 1
        ).toList();

        if (stateDatumEntities.isEmpty()) {
            throw new IllegalArgumentException("List of valid state datum entities with countdown > 0 is empty.");
        }

        if (stateDatumEntities.size() == 1) {
            return stateDatumEntities.get(0);
        }

        StateDatumEntity stateDatumEntity = stateDatumEntities.get(0);
        for (StateDatumEntity entity : stateDatumEntities) {
            if (entity.getCountdown() % entity.getBootstrapDatum().getFeeInterval() != 0) {
                stateDatumEntity = entity;
                break;
            } else {
                if (entity.getBootstrapDatum().getFee() < stateDatumEntity.getBootstrapDatum().getFee()) {
                    stateDatumEntity = entity;
                }
            }
        }
        return stateDatumEntity;
    }

    @Transactional
    public void undoInvalidationBeforeSlot(long slot) {
        stateDatumRepository.undoInvalidationBeforeSlot(slot);
    }

    public void invalidateStateDatum(String id, long slot) {
        stateDatumRepository.markAsInvalid(id, slot);
    }

    @Transactional
    public void updateStateDatum(StateDatumEntity stateDatumEntity, Long slot) {
        StateDatumUpdateEntity stateDatumUpdateEntity = StateDatumUpdateEntity.builder()
                .countdown(stateDatumEntity.getCountdown())
                .transactionId(stateDatumEntity.getTransactionId())
                .slot(slot)
                .build();

        stateDatumEntity.addUpdate(stateDatumUpdateEntity);
        stateDatumRepository.save(stateDatumEntity);
    }

    public Optional<StateDatumEntity> findByAddressUtxo(AddressUtxo addressUtxo) {
        StateDatum stateDatum = StateDatum.fromLegacyUtxoDatum(addressUtxo.getInlineDatum());
        return this.findById(stateDatum.getId());
    }

    public Optional<StateDatumEntity> findById(String id) {
        return stateDatumRepository.findById(id);
    }

    public void save(StateDatumEntity stateDatumEntity) {
        stateDatumRepository.save(stateDatumEntity);
    }

    public List<StateDatumEntity> findByOwner(String address) {
        return stateDatumRepository.findByOwner(
                HexUtil.encodeHexString(CardanoUtils.extractCredentialFromAddress(address)));
    }

    public Optional<StateDatumEntity> findByUserAndBootstrapToken(String address, String bootstrapTokenName) {
        return stateDatumRepository.findByUserAndBootstrapToken(
                HexUtil.encodeHexString(CardanoUtils.extractCredentialFromAddress(address)), bootstrapTokenName);
    }

    @Transactional
    public void handleRollbackToSlot(long slot) {
        stateDatumUpdateRepository.deleteAllAfterSlot(slot);
        stateDatumRepository.deleteAllAfterSlot(slot);
        stateDatumRepository.handleRollback();
    }
}
