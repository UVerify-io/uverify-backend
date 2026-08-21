package io.uverify.backend.extension.aymvision.anchor;

import io.uverify.backend.extension.aymvision.user.AymUserProfileId;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "aym_completion_certificate")
@IdClass(AymUserProfileId.class)
public class AymCompletionCertEntity {

    @Id
    @Column(name = "public_key", length = 64)
    private String publicKey;

    @Id
    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "cert_hash", length = 64, nullable = false)
    private String certHash;

    @Column(name = "verify_url", length = 2000, nullable = false)
    private String verifyUrl;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    public AymCompletionCertEntity(String publicKey, String profileId, String certHash, String verifyUrl) {
        this.publicKey = publicKey;
        this.profileId = profileId;
        this.certHash = certHash;
        this.verifyUrl = verifyUrl;
        this.issuedAt = Instant.now();
    }
}
