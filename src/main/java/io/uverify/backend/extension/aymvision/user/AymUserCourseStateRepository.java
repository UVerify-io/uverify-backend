package io.uverify.backend.extension.aymvision.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AymUserCourseStateRepository
        extends JpaRepository<AymUserCourseStateEntity, AymUserCourseStateId> {

    List<AymUserCourseStateEntity> findByPublicKeyAndProfileId(String publicKey, String profileId);

    Optional<AymUserCourseStateEntity> findByPublicKeyAndProfileIdAndCourseId(
            String publicKey, String profileId, String courseId);
}
