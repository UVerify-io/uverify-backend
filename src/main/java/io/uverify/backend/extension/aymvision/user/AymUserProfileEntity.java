package io.uverify.backend.extension.aymvision.user;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "aym_user_profile")
@IdClass(AymUserProfileId.class)
public class AymUserProfileEntity {

    @Id
    @Column(name = "public_key", length = 64)
    private String publicKey;

    @Id
    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "salt", length = 64, nullable = false)
    private String salt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AymUserProfileEntity(String publicKey, String profileId, String salt) {
        this.publicKey = publicKey;
        this.profileId = profileId;
        this.salt = salt;
    }
}
