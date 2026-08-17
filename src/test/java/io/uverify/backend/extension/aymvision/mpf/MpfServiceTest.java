package io.uverify.backend.extension.aymvision.mpf;

import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.internal.TestNodeStore;
import io.uverify.backend.extension.aymvision.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MpfServiceTest {

    private static final String PUB_KEY  = "aa".repeat(32);
    private static final String PROFILE_A = "profile-a";
    private static final String PROFILE_B = "profile-b";

    @Mock AymMpfAnchorRepository anchorRepo;
    @Mock AymUserProfileRepository profileRepo;
    @Mock AymUserContentRepository contentRepo;
    @Mock AymUserCourseStateRepository courseStateRepo;

    private MpfService service;

    @BeforeEach
    void setUp() {
        when(anchorRepo.findMaxTreeVersion()).thenReturn(Optional.empty());
        service = new MpfService(new TestNodeStore(), anchorRepo, profileRepo, contentRepo, courseStateRepo);
    }

    @Test
    void upsertLeaf_changesRoot_whenDataInserted() {
        String rootBefore = service.currentRoot();

        service.upsertLeaf(PUB_KEY, PROFILE_A, List.of("s1e01"), List.of());

        assertThat(service.currentRoot()).isNotEqualTo(rootBefore);
    }

    @Test
    void upsertLeaf_keepsRootAndVersion_whenSameDataInsertedTwice() {
        service.upsertLeaf(PUB_KEY, PROFILE_A, List.of("s1e01"), List.of());
        String rootAfterFirst = service.currentRoot();
        long versionAfterFirst = service.currentTreeVersion();

        service.upsertLeaf(PUB_KEY, PROFILE_A, List.of("s1e01"), List.of());

        assertThat(service.currentRoot()).isEqualTo(rootAfterFirst);
        assertThat(service.currentTreeVersion()).isEqualTo(versionAfterFirst);
    }

    @Test
    void upsertLeaf_roundTripProof_verifies() {
        service.upsertLeaf(PUB_KEY, PROFILE_A, List.of("s1e01"), List.of());

        String profileHash = java.util.HexFormat.of().formatHex(
                MpfService.profileHashBytes(PUB_KEY, PROFILE_A));

        MpfProof proof = service.proofFor(profileHash);

        assertThat(proof.root()).isEqualTo(service.currentRoot());
        assertThat(proof.proofHex()).isNotNull();
        assertThat(proof.treeVersion()).isEqualTo(service.currentTreeVersion());
    }

    @Test
    void leafValue_isOrderIndependent() {
        byte[] ab = MpfService.leafValueBytes(List.of("b", "a"), List.of());
        byte[] ba = MpfService.leafValueBytes(List.of("a", "b"), List.of());

        assertThat(ab).isEqualTo(ba);
    }

    @Test
    void upsertLeaf_twoDifferentProfiles_haveIndependentLeaves() {
        service.upsertLeaf(PUB_KEY, PROFILE_A, List.of("s1e01"), List.of());
        service.upsertLeaf(PUB_KEY, PROFILE_B, List.of("s1e02"), List.of());

        String hashA = java.util.HexFormat.of().formatHex(MpfService.profileHashBytes(PUB_KEY, PROFILE_A));
        String hashB = java.util.HexFormat.of().formatHex(MpfService.profileHashBytes(PUB_KEY, PROFILE_B));

        MpfProof proofA = service.proofFor(hashA);
        MpfProof proofB = service.proofFor(hashB);

        assertThat(proofA.proofHex()).isNotNull();
        assertThat(proofB.proofHex()).isNotNull();
        assertThat(hashA).isNotEqualTo(hashB);
    }
}
