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
import io.uverify.backend.entity.BootstrapDatumEntity;
import io.uverify.backend.model.BootstrapDatum;
import io.uverify.backend.repository.BootstrapDatumRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BootstrapDatumService {

    @Autowired
    private final BootstrapDatumRepository bootstrapDatumRepository;

    @Autowired
    public BootstrapDatumService(BootstrapDatumRepository bootstrapDatumRepository) {
        this.bootstrapDatumRepository = bootstrapDatumRepository;
    }

    public boolean bootstrapDatumAlreadyExists(String tokenName) {
        return bootstrapDatumRepository.findByTokenName(tokenName).isPresent();
    }

    public Optional<BootstrapDatumEntity> getBootstrapDatum(String tokenName) {
        return bootstrapDatumRepository.findByTokenName(tokenName);
    }

    public Optional<BootstrapDatum> selectCheapestBootstrapDatum(byte[] credential, int version) {
        List<BootstrapDatumEntity> bootstrapDatumEntities = bootstrapDatumRepository.findAllWhitelisted();
        List<BootstrapDatumEntity> customBootstrapDatumEntities = bootstrapDatumRepository.findByAllowedCredential(HexUtil.encodeHexString(credential));

        bootstrapDatumEntities.addAll(customBootstrapDatumEntities);
        bootstrapDatumEntities = bootstrapDatumEntities.stream()
                .filter(bootstrapDatumEntity -> bootstrapDatumEntity.getVersion().equals(version))
                .toList();

        double lovelaceEveryHundredTransactions = Double.MAX_VALUE;
        BootstrapDatumEntity cheapestBootstrapDatum = null;
        for (BootstrapDatumEntity entity : bootstrapDatumEntities) {
            double feeEveryHundredTransactions = (100.0 / entity.getFeeInterval()) * entity.getFee();
            if (feeEveryHundredTransactions < lovelaceEveryHundredTransactions) {
                lovelaceEveryHundredTransactions = feeEveryHundredTransactions;
                cheapestBootstrapDatum = entity;
            }
        }

        if (cheapestBootstrapDatum == null) {
            return Optional.empty();
        }

        return Optional.of(BootstrapDatum.fromBootstapDatumEntity(cheapestBootstrapDatum));
    }

    public void save(BootstrapDatumEntity bootstrapDatumEntity) {
        bootstrapDatumRepository.save(bootstrapDatumEntity);
    }

    public void markAsInvalid(String uverify_bootstrap_token, long currentSlot) {
        bootstrapDatumRepository.markAsInvalid(uverify_bootstrap_token, currentSlot);
    }

    @Transactional
    public void undoInvalidationBeforeSlot(long slot) {
        bootstrapDatumRepository.undoInvalidationBeforeSlot(slot);
    }

    @Transactional
    public void deleteAllAfterSlot(long slot) {
        bootstrapDatumRepository.deleteAllAfterSlot(slot);
    }
}
