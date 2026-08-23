package io.uverify.backend.extension.aymvision.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AymUserProfileRepository
        extends JpaRepository<AymUserProfileEntity, AymUserProfileId> {

    java.util.Optional<AymUserProfileEntity> findByProfileHash(String profileHash);
}
