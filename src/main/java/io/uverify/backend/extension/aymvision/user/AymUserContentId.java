package io.uverify.backend.extension.aymvision.user;

import java.io.Serializable;
import java.util.Objects;

public class AymUserContentId implements Serializable {
    private String publicKey;
    private String profileId;
    private String contentId;

    public AymUserContentId() {}

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AymUserContentId that)) return false;
        return Objects.equals(publicKey, that.publicKey) &&
               Objects.equals(profileId, that.profileId) &&
               Objects.equals(contentId, that.contentId);
    }
    @Override public int hashCode() { return Objects.hash(publicKey, profileId, contentId); }
}
