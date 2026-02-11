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

package io.uverify.backend.repository;

import io.uverify.backend.entity.StateDatumEntity;
import jakarta.transaction.Transactional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StateDatumRepository extends JpaRepository<StateDatumEntity, String> {

    @Modifying
    @Transactional
    @Query("DELETE FROM StateDatumEntity WHERE creationSlot > :target")
    void deleteAllAfterSlot(@Param("target") long target);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE state_datum
            SET
                countdown = (
                    SELECT latestUpdate.countdown
                    FROM state_datum_update latestUpdate
                    WHERE latestUpdate.state_datum_id = state_datum.id
                    AND latestUpdate.slot = (
                        SELECT MAX(slot)
                        FROM state_datum_update
                        WHERE state_datum_update.state_datum_id = state_datum.id
                    )
                ),
                transaction_id = (
                    SELECT latestUpdate.transaction_id
                    FROM state_datum_update latestUpdate
                    WHERE latestUpdate.state_datum_id = state_datum.id
                    AND latestUpdate.slot = (
                        SELECT MAX(slot)
                        FROM state_datum_update
                        WHERE state_datum_update.state_datum_id = state_datum.id
                    )
                )
            """, nativeQuery = true)
    void handleRollback();

    @Query("""
                SELECT DISTINCT stateDatum
                FROM StateDatumEntity stateDatum
                LEFT JOIN FETCH stateDatum.updates
                WHERE stateDatum.id = :id
                  AND stateDatum.invalidationSlot IS NULL
                  AND stateDatum.countdown > 0
            """)
    @NotNull
    Optional<StateDatumEntity> findById(@NotNull String id);

    @Modifying
    @Query("UPDATE StateDatumEntity SET invalidationSlot = NULL WHERE invalidationSlot > :slot")
    void undoInvalidationBeforeSlot(long slot);

    @Modifying
    @Query("UPDATE StateDatumEntity SET invalidationSlot = :invalidationSlot WHERE id = :id")
    void markAsInvalid(@Param("id") String id, @Param("invalidationSlot") long invalidationSlot);


    @Query("SELECT stateDatum FROM StateDatumEntity stateDatum WHERE stateDatum.owner = :credential " +
            "AND bootstrapDatum.invalidationSlot IS NULL AND stateDatum.countdown > 0")
    List<StateDatumEntity> findByOwner(@Param("credential") String credential);

    @Query(value = """
            SELECT stateDatum FROM StateDatumEntity stateDatum
            WHERE stateDatum.owner = :credential
                AND stateDatum.bootstrapDatum.tokenName = :tokenName
                AND bootstrapDatum.invalidationSlot IS NULL
                AND stateDatum.countdown > 0
               """)
    Optional<StateDatumEntity> findByUserAndBootstrapToken(@Param("credential") String credential,
                                                           @Param("tokenName") String tokenName);
}
