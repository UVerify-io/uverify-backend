package io.uverify.backend.extension.aymvision.mpf;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "aym_mpf_anchor")
public class AymMpfAnchorEntity {

    @Id
    @Column(name = "tree_version")
    private Long treeVersion;

    @Column(name = "root", length = 128, nullable = false)
    private String root;

    @Column(name = "uverify_tx_hash", length = 128)
    private String uverifyTxHash;

    @Column(name = "anchored_at", nullable = false)
    private Instant anchoredAt;

    public AymMpfAnchorEntity(long treeVersion, String root, String uverifyTxHash, Instant anchoredAt) {
        this.treeVersion = treeVersion;
        this.root = root;
        this.uverifyTxHash = uverifyTxHash;
        this.anchoredAt = anchoredAt;
    }
}
