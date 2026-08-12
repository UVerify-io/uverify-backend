package io.uverify.backend.extension.aymvision.voucher;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@Entity
@Table(name = "aym_redeemed_voucher")
public class RedeemedVoucherEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "content_id", nullable = false)
    private String contentId;

    @Column(name = "redeemed_by_public_key", length = 64, nullable = false)
    private String redeemedByPublicKey;

    @Column(name = "redeemed_by_profile_id", nullable = false)
    private String redeemedByProfileId;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt = Instant.now();

    public RedeemedVoucherEntity(UUID id, String contentId, String publicKey, String profileId) {
        this.id = id;
        this.contentId = contentId;
        this.redeemedByPublicKey = publicKey;
        this.redeemedByProfileId = profileId;
    }
}
