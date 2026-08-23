package io.uverify.backend.extension.aymvision.stripe;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "aym_stripe_purchase")
public class StripePurchaseEntity {

    @Id
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "public_key", length = 64, nullable = false)
    private String publicKey;

    @Column(name = "profile_id", nullable = false)
    private String profileId;

    @Column(name = "content_id", nullable = false)
    private String contentId;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt = Instant.now();

    public StripePurchaseEntity(String sessionId, String publicKey, String profileId, String contentId) {
        this.sessionId = sessionId;
        this.publicKey = publicKey;
        this.profileId = profileId;
        this.contentId = contentId;
    }
}
