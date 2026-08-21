package io.uverify.backend.extension.aymvision.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "aym")
public class AymVisionProperties {

    /** Master public key (Ed25519, hex) — used to authenticate voucher creation. */
    private String masterPublicKey = "";

    /** Base URL for UVerify UI, e.g. https://app.uverify.io */
    private String uiBaseUrl = "";

    private Stripe stripe = new Stripe();
    private ContentRepo contentRepo = new ContentRepo();

    @Data
    public static class Stripe {
        private String apiKey = "";
        /** productId → contentId mapping */
        private Map<String, String> products = new HashMap<>();
    }

    @Data
    public static class ContentRepo {
        private String url = "";
        private String token = "";
        /** Cache TTL as ISO-8601 duration, e.g. PT6H */
        private String cacheTtl = "PT6H";
    }
}
