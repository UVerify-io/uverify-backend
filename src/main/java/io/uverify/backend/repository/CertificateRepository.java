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

import io.uverify.backend.entity.UVerifyCertificateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CertificateRepository extends JpaRepository<UVerifyCertificateEntity, String> {
    List<UVerifyCertificateEntity> findAllByHash(String hash);

    @Modifying
    @Query("DELETE FROM UVerifyCertificateEntity WHERE slot > :target")
    void deleteAllAfterSlot(@Param("target") long target);

    @Query("SELECT c FROM UVerifyCertificateEntity c WHERE c.transactionId = :transactionId AND c.hash = :dataHash")
    UVerifyCertificateEntity findByTransactionHashAndDataHash(String transactionId, String dataHash);

    List<UVerifyCertificateEntity> findByPaymentCredential(String credential);
}
