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

import io.uverify.backend.extension.entity.ConnectedGoodUpdateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConnectedGoodUpdateRepository extends JpaRepository<ConnectedGoodUpdateEntity, Long> {
    @Modifying
    @Query("DELETE FROM ConnectedGoodUpdateEntity WHERE slot > :target")
    void deleteAllAfterSlot(@Param("target") long target);

    @Query("SELECT updateEntity FROM ConnectedGoodUpdateEntity updateEntity WHERE " +
            "updateEntity.connectedGood.id = :connectedGoodId ORDER BY updateEntity.slot DESC LIMIT 1")
    ConnectedGoodUpdateEntity getLatestUpdateByConnectedGoodId(String connectedGoodId);
}
