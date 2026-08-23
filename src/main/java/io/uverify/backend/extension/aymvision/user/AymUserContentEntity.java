package io.uverify.backend.extension.aymvision.user;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "aym_user_content")
@IdClass(AymUserContentId.class)
public class AymUserContentEntity {

    @Id
    @Column(name = "public_key", length = 64)
    private String publicKey;

    @Id
    @Column(name = "profile_id")
    private String profileId;

    @Id
    @Column(name = "content_id")
    private String contentId;

    @Column(name = "source", length = 50, nullable = false)
    private String source;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();

    public AymUserContentEntity(String publicKey, String profileId, String contentId, String source) {
        this.publicKey = publicKey;
        this.profileId = profileId;
        this.contentId = contentId;
        this.source = source;
    }
}
