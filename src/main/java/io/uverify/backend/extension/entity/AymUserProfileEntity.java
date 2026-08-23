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
@Table(name = "aym_user_profile")
@IdClass(AymUserProfileId.class)
public class AymUserProfileEntity {

    @Id
    @Column(name = "public_key", length = 64)
    private String publicKey;

    @Id
    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "salt", length = 64, nullable = false)
    private String salt;

    @Column(name = "profile_hash", length = 56)
    private String profileHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AymUserProfileEntity(String publicKey, String profileId, String salt, String profileHash) {
        this.publicKey = publicKey;
        this.profileId = profileId;
        this.salt = salt;
        this.profileHash = profileHash;
    }
}
