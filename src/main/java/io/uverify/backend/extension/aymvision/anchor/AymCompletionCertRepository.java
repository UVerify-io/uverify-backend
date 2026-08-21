package io.uverify.backend.extension.aymvision.anchor;

import io.uverify.backend.extension.aymvision.user.AymUserProfileId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AymCompletionCertRepository extends JpaRepository<AymCompletionCertEntity, AymUserProfileId> {}
