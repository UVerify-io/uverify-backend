package io.uverify.backend.extension.aymvision.mpf;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AymMpfAnchorRepository extends JpaRepository<AymMpfAnchorEntity, Long> {

    Optional<AymMpfAnchorEntity> findTopByOrderByTreeVersionDesc();

    @Query("SELECT MAX(a.treeVersion) FROM AymMpfAnchorEntity a")
    Optional<Long> findMaxTreeVersion();
}
