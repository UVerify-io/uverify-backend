package io.uverify.backend.extension.aymvision.voucher;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@Entity
@Table(name = "aym_voucher")
public class VoucherEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "content_id", nullable = false)
    private String contentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public VoucherEntity(String contentId) {
        this.id = UUID.randomUUID();
        this.contentId = contentId;
    }
}
