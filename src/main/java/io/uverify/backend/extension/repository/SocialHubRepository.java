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

package io.uverify.backend.extension.repository;

import io.uverify.backend.extension.entity.SocialHubEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SocialHubRepository extends JpaRepository<SocialHubEntity, Long> {

    @Query(value = """ 
                SELECT socialHub FROM SocialHubEntity socialHub
                JOIN socialHub.connectedGood connectedGood
                WHERE socialHub.connectedGood.id = :batchId
                AND socialHub.assetId = :assetId
                ORDER BY socialHub.creationSlot DESC
                LIMIT 1
            """)
    Optional<SocialHubEntity> findByBatchIdAndAssetId(@Param("batchId") String batchId, @Param("assetId") String assetId);

    @Query("""
                SELECT socialHub FROM SocialHubEntity socialHub
                JOIN socialHub.connectedGood connectedGood
                WHERE socialHub.connectedGood.id = :batchId
                AND socialHub.password = :mintHash
                ORDER BY socialHub.creationSlot DESC
                LIMIT 1
            """)
    Optional<SocialHubEntity> findByBatchIdAndMintHash(@Param("batchId") String batchId, @Param("mintHash") String mintHash);

    @Query("""
                SELECT socialHub FROM SocialHubEntity socialHub
                JOIN socialHub.connectedGood connectedGood
                WHERE socialHub.connectedGood.id IN :batchIds
                AND socialHub.password = :mintHash
                ORDER BY socialHub.creationSlot DESC
                LIMIT 1
            """)
    Optional<SocialHubEntity> findByBatchIdsAndMintHash(List<String> batchIds, String mintHash);

    @Modifying
    @Query("DELETE FROM SocialHubEntity WHERE creationSlot > :target")
    void deleteAllAfterSlot(@Param("target") long target);
}
