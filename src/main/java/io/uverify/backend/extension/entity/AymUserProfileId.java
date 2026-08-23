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

import java.io.Serializable;
import java.util.Objects;

public class AymUserProfileId implements Serializable {
    private String publicKey;
    private String profileId;

    public AymUserProfileId() {}
    public AymUserProfileId(String publicKey, String profileId) {
        this.publicKey = publicKey;
        this.profileId = profileId;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AymUserProfileId that)) return false;
        return Objects.equals(publicKey, that.publicKey) && Objects.equals(profileId, that.profileId);
    }
    @Override public int hashCode() { return Objects.hash(publicKey, profileId); }
}
