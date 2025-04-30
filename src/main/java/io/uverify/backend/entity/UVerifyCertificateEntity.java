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

import io.uverify.backend.model.UVerifyCertificate;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Date;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "uverify_certificate")
public class UVerifyCertificateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hash", nullable = false, length = 100)
    private String hash;

    @Column(name = "payment_credential")
    private String paymentCredential;

    @Column(name = "block_hash", nullable = false, length = 255)
    private String blockHash;

    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    @Column(name = "transaction_id", nullable = false, length = 255)
    private String transactionId;

    @Column(name = "output_index", nullable = false)
    private Integer outputIndex;

    @Column(name = "creation_time", nullable = false)
    private Date creationTime;

    @Column(name = "slot", nullable = false)
    private Long slot;

    @Column(name = "extra", nullable = false)
    private String extra;

    @Column(name = "hash_algorithm", nullable = false, length = 100)
    private String hashAlgorithm;

    @ManyToOne
    @JoinColumn(name = "state_datum_id")
    private StateDatumEntity stateDatum;

    public static UVerifyCertificateEntity fromUVerifyCertificate(UVerifyCertificate uVerifyCertificate) {
        return UVerifyCertificateEntity.builder()
                .hash(uVerifyCertificate.getHash())
                .paymentCredential(uVerifyCertificate.getIssuer())
                .creationTime(Date.from(Instant.now()))
                .extra(uVerifyCertificate.getExtra())
                .hashAlgorithm(uVerifyCertificate.getAlgorithm())
                .build();
    }
}
