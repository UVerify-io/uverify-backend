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

import io.uverify.backend.entity.BootstrapDatumEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BootstrapDatumRepository extends JpaRepository<BootstrapDatumEntity, String> {
    @Query(value = """
                SELECT bootstrapDatum
                FROM BootstrapDatumEntity bootstrapDatum
                LEFT JOIN bootstrapDatum.allowedCredentials allowedCredential
                WHERE allowedCredential.credential = :credential
                AND bootstrapDatum.version >= :minVersion
                AND bootstrapDatum.invalidationSlot IS NULL
            """)
    List<BootstrapDatumEntity> findByAllowedCredential(@Param("credential") String credential, @Param("minVersion") Integer minVersion);

    @Query(value = """
                SELECT bootstrapDatum
                FROM BootstrapDatumEntity bootstrapDatum
                WHERE bootstrapDatum.allowedCredentials IS EMPTY
                AND bootstrapDatum.version > 1
                AND bootstrapDatum.invalidationSlot IS NULL
            """)
    List<BootstrapDatumEntity> findAllWhitelisted();

    @Modifying
    @Transactional
    @Query("DELETE FROM BootstrapDatumEntity WHERE creationSlot > :target")
    void deleteAllAfterSlot(@Param("target") long target);

    @Modifying
    @Query("UPDATE BootstrapDatumEntity SET invalidationSlot = NULL WHERE invalidationSlot > :slot")
    void undoInvalidationBeforeSlot(long slot);

    @Modifying
    @Query("UPDATE BootstrapDatumEntity SET invalidationSlot = :invalidationSlot WHERE tokenName = :tokenName")
    void markAsInvalid(@Param("tokenName") String tokenName, @Param("invalidationSlot") long invalidationSlot);

    @Query("SELECT bootstrapDatum FROM BootstrapDatumEntity bootstrapDatum WHERE bootstrapDatum.tokenName = :tokenName AND bootstrapDatum.version >= :minVersion AND bootstrapDatum.invalidationSlot IS NULL")
    Optional<BootstrapDatumEntity> findByTokenName(@Param("tokenName") String tokenName, @Param("minVersion") Integer minVersion);
}
