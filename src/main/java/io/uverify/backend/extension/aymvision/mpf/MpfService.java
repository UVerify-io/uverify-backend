package io.uverify.backend.extension.aymvision.mpf;

import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbNodeStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.uverify.backend.extension.aymvision.config.AymVisionProperties;
import io.uverify.backend.extension.aymvision.user.AymUserContentEntity;
import io.uverify.backend.extension.aymvision.user.AymUserContentRepository;
import io.uverify.backend.extension.aymvision.user.AymUserCourseStateEntity;
import io.uverify.backend.extension.aymvision.user.AymUserCourseStateRepository;
import io.uverify.backend.extension.aymvision.user.AymUserProfileRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class MpfService {

    private final MpfTrie trie;
    private final AymMpfAnchorRepository anchorRepo;
    private final AymUserProfileRepository profileRepo;
    private final AymUserContentRepository contentRepo;
    private final AymUserCourseStateRepository courseStateRepo;
    private final AtomicLong treeVersion;
    private volatile String currentRoot;

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public MpfService(AymVisionProperties props,
                      AymMpfAnchorRepository anchorRepo,
                      AymUserProfileRepository profileRepo,
                      AymUserContentRepository contentRepo,
                      AymUserCourseStateRepository courseStateRepo) {
        this(new RocksDbNodeStore(props.getMpf().getDbPath()),
             anchorRepo, profileRepo, contentRepo, courseStateRepo);
    }

    MpfService(NodeStore nodeStore,
               AymMpfAnchorRepository anchorRepo,
               AymUserProfileRepository profileRepo,
               AymUserContentRepository contentRepo,
               AymUserCourseStateRepository courseStateRepo) {
        this.trie = new MpfTrie(nodeStore);
        this.anchorRepo = anchorRepo;
        this.profileRepo = profileRepo;
        this.contentRepo = contentRepo;
        this.courseStateRepo = courseStateRepo;
        long initVersion = anchorRepo.findMaxTreeVersion().orElse(0L);
        this.treeVersion = new AtomicLong(initVersion);
        this.currentRoot = rootHex(trie.getRootHash());
    }

    @PostConstruct
    void rebuildFromSql() {
        log.info("Rebuilding MPF trie from SQL...");
        profileRepo.findAll().forEach(profile -> {
            List<String> owned = contentRepo
                    .findByPublicKeyAndProfileId(profile.getPublicKey(), profile.getProfileId())
                    .stream().map(AymUserContentEntity::getContentId).sorted().toList();
            List<String> finished = courseStateRepo
                    .findByPublicKeyAndProfileId(profile.getPublicKey(), profile.getProfileId())
                    .stream()
                    .filter(s -> "FINISHED".equalsIgnoreCase(s.getStatus()))
                    .map(AymUserCourseStateEntity::getCourseId).sorted().toList();
            putLeaf(profile.getPublicKey(), profile.getProfileId(), owned, finished);
        });
        currentRoot = rootHex(trie.getRootHash());
        log.info("MPF trie rebuilt — root={} version={}", currentRoot, treeVersion.get());
    }

    /**
     * Inserts or updates the MPF leaf for (publicKey, profileId).
     * key  = blake2b-224(publicKey + profileId) bytes [= profileHash]
     * value = sha256(canonicalJson({finishedCourses: sorted, ownedContent: sorted}))
     *
     * @return new treeVersion (unchanged if root did not change)
     */
    public synchronized long upsertLeaf(String publicKeyHex, String profileId,
                                        List<String> owned, List<String> finished) {
        String oldRoot = currentRoot;
        putLeaf(publicKeyHex, profileId, owned, finished);
        String newRoot = rootHex(trie.getRootHash());
        currentRoot = newRoot;
        if (!newRoot.equals(oldRoot)) {
            treeVersion.incrementAndGet();
        }
        return treeVersion.get();
    }

    public String currentRoot() {
        return currentRoot;
    }

    public long currentTreeVersion() {
        return treeVersion.get();
    }

    public MpfProof proofFor(String profileHash) {
        byte[] keyBytes = HexFormat.of().parseHex(profileHash);
        String proofHex = trie.getProofWire(keyBytes)
                .map(HexFormat.of()::formatHex)
                .orElse(null);
        return new MpfProof(proofHex, currentRoot, treeVersion.get());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void putLeaf(String publicKeyHex, String profileId,
                         List<String> owned, List<String> finished) {
        byte[] keyBytes = profileHashBytes(publicKeyHex, profileId);
        byte[] valueBytes = leafValueBytes(owned, finished);
        trie.put(keyBytes, valueBytes);
    }

    static byte[] profileHashBytes(String publicKeyHex, String profileId) {
        // blake2b-224 of (publicKeyHex + profileId) — same as ContentService.blake2b224Hex
        org.bouncycastle.crypto.digests.Blake2bDigest digest = new org.bouncycastle.crypto.digests.Blake2bDigest(224);
        byte[] input = (publicKeyHex + profileId).getBytes(StandardCharsets.UTF_8);
        digest.update(input, 0, input.length);
        byte[] result = new byte[28];
        digest.doFinal(result, 0);
        return result;
    }

    static byte[] leafValueBytes(List<String> owned, List<String> finished) {
        try {
            TreeMap<String, Object> map = new TreeMap<>();
            map.put("finishedCourses", finished.stream().sorted().toList());
            map.put("ownedContent", owned.stream().sorted().toList());
            String json = CANONICAL_MAPPER.writeValueAsString(map);
            return sha256(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize leaf", e);
        }
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String rootHex(byte[] rootHash) {
        return rootHash != null ? HexFormat.of().formatHex(rootHash) : "";
    }
}
