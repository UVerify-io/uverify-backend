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

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt = Instant.now();

    @Column(name = "note", length = 500)
    private String note;

    public RedeemedVoucherEntity(UUID id, String contentId, String note) {
        this.id = id;
        this.contentId = contentId;
        this.note = note;
    }
}
