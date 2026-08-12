package io.uverify.backend.extension.aymvision.user;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "aym_user_course_state")
@IdClass(AymUserCourseStateId.class)
public class AymUserCourseStateEntity {

    @Id
    @Column(name = "public_key", length = 64)
    private String publicKey;

    @Id
    @Column(name = "profile_id")
    private String profileId;

    @Id
    @Column(name = "course_id")
    private String courseId;

    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt = Instant.now();

    public AymUserCourseStateEntity(String publicKey, String profileId, String courseId, String status) {
        this.publicKey = publicKey;
        this.profileId = profileId;
        this.courseId = courseId;
        this.status = status;
    }
}
