package io.uverify.backend.extension.aymvision.user;

import java.io.Serializable;
import java.util.Objects;

public class AymUserCourseStateId implements Serializable {
    private String publicKey;
    private String profileId;
    private String courseId;

    public AymUserCourseStateId() {}

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AymUserCourseStateId that)) return false;
        return Objects.equals(publicKey, that.publicKey) &&
               Objects.equals(profileId, that.profileId) &&
               Objects.equals(courseId, that.courseId);
    }
    @Override public int hashCode() { return Objects.hash(publicKey, profileId, courseId); }
}
