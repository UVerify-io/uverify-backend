package io.uverify.backend.extension.aymvision.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AymUserContentRepository
        extends JpaRepository<AymUserContentEntity, AymUserContentId> {

    List<AymUserContentEntity> findByPublicKeyAndProfileId(String publicKey, String profileId);

    boolean existsByPublicKeyAndProfileIdAndContentId(String publicKey, String profileId, String contentId);
}
