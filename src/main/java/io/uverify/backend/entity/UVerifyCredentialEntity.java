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

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "uverify_credential", indexes = {
        @Index(name = "idx_uverify_credential_lookup",
                columnList = "payment_credential, credential_type, revoked")
})
public class UVerifyCredentialEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_cert_hash", nullable = false, unique = true, length = 100)
    private String authCertHash;

    @Column(name = "payment_credential", nullable = false, length = 100)
    private String paymentCredential;

    @Column(name = "credential_type", nullable = false, length = 100)
    private String credentialType;

    @Column(name = "keri_aid", length = 200)
    private String keriAid;

    @Column(name = "keri_schema", length = 200)
    private String keriSchema;

    @Column(name = "keri_oobi", length = 500)
    private String keriOobi;

    @Column(name = "keri_verified", nullable = false)
    @Builder.Default
    private boolean keriVerified = false;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "tx_hash", length = 100)
    private String txHash;

    @Column(name = "slot", nullable = false)
    private Long slot;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "acdc_attributes", columnDefinition = "TEXT")
    private String acdcAttributes;
}
