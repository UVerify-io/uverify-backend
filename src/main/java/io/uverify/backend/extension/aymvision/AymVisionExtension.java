package io.uverify.backend.extension.aymvision;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import io.uverify.backend.dto.UsageStatistics;
import io.uverify.backend.extension.ExtensionManager;
import io.uverify.backend.extension.UVerifyServiceExtension;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;

@Component
@Slf4j
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class AymVisionExtension implements UVerifyServiceExtension {

    public AymVisionExtension(ExtensionManager extensionManager) {
        extensionManager.registerExtension(this);
        log.info("AYM Vision extension registered");
    }

    @Override
    public String getName() {
        return "aym-vision";
    }

    @Override
    public List<AddressUtxo> processAddressUtxos(List<AddressUtxo> addressUtxos) {
        return List.of();
    }

    @Override
    public void handleRollbackToSlot(long slot) {
        // AYM Vision does not store chain-derived state
    }

    @Override
    public void addUsageStatistics(UsageStatistics usageStatistics) {
        // No chain-derived statistics for AYM Vision
    }

    @Override
    public BigInteger addTransactionFees(BigInteger totalFees) {
        return BigInteger.ZERO;
    }
}
