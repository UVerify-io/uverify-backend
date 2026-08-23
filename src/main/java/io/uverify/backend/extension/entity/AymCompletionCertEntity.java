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

package io.uverify.backend.extension.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "aym_completion_certificate")
@IdClass(AymUserProfileId.class)
public class AymCompletionCertEntity {

    @Id
    @Column(name = "public_key", length = 64)
    private String publicKey;

    @Id
    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "cert_hash", length = 64, nullable = false)
    private String certHash;

    @Column(name = "verify_url", length = 2000, nullable = false)
    private String verifyUrl;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    public AymCompletionCertEntity(String publicKey, String profileId, String certHash, String verifyUrl) {
        this.publicKey = publicKey;
        this.profileId = profileId;
        this.certHash = certHash;
        this.verifyUrl = verifyUrl;
        this.issuedAt = Instant.now();
    }
}
