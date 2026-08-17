package io.uverify.backend.extension.aymvision.anchor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnMissingBean(UVerifyIssuer.class)
class NoOpUVerifyIssuer implements UVerifyIssuer {

    @Override
    public String issue(String hashHex, Map<String, Object> metadata) {
        log.warn("UVerify anchor wallet not configured — skipping on-chain anchoring for root {}", hashHex);
        return "pending";
    }
}
