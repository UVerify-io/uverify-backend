package io.uverify.backend.extension.aymvision.user;

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
