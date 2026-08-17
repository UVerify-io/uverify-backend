package io.uverify.backend.extension.aymvision.anchor;

import io.uverify.backend.extension.aymvision.mpf.AymMpfAnchorEntity;
import io.uverify.backend.extension.aymvision.mpf.AymMpfAnchorRepository;
import io.uverify.backend.extension.aymvision.mpf.MpfService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class AnchorScheduler {

    private final MpfService mpfService;
    private final AymMpfAnchorRepository anchorRepo;
    private final UVerifyIssuer issuer;

    public AnchorScheduler(MpfService mpfService,
                           AymMpfAnchorRepository anchorRepo,
                           UVerifyIssuer issuer) {
        this.mpfService = mpfService;
        this.anchorRepo = anchorRepo;
        this.issuer = issuer;
    }

    @Scheduled(fixedDelayString = "${aym.anchor.interval-ms:172800000}")
    public void anchorIfChanged() {
        String currentRoot = mpfService.currentRoot();
        long currentVersion = mpfService.currentTreeVersion();

        AymMpfAnchorEntity lastAnchor = anchorRepo.findTopByOrderByTreeVersionDesc().orElse(null);

        if (lastAnchor != null && lastAnchor.getRoot().equals(currentRoot)) {
            log.debug("MPF root unchanged ({}), skipping anchor", currentRoot);
            return;
        }

        log.info("Anchoring MPF root {} (version {})", currentRoot, currentVersion);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("uverify_template_id", "aymAnchor");
        metadata.put("tree_version", currentVersion);
        metadata.put("previous_root", lastAnchor != null ? lastAnchor.getRoot() : null);

        String txHash = issuer.issue(currentRoot, metadata);

        anchorRepo.save(new AymMpfAnchorEntity(currentVersion, currentRoot, txHash, Instant.now()));
        log.info("MPF anchor stored — treeVersion={} txHash={}", currentVersion, txHash);
    }
}
