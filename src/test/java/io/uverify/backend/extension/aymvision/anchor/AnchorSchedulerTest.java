package io.uverify.backend.extension.aymvision.anchor;

import io.uverify.backend.extension.aymvision.mpf.AymMpfAnchorEntity;
import io.uverify.backend.extension.aymvision.mpf.AymMpfAnchorRepository;
import io.uverify.backend.extension.aymvision.mpf.MpfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnchorSchedulerTest {

    private static final String ROOT_HEX = "ab".repeat(32);
    private static final long VERSION = 5L;
    private static final String TX_HASH = "tx123";

    @Mock MpfService mpfService;
    @Mock AymMpfAnchorRepository anchorRepo;
    @Mock UVerifyIssuer issuer;

    private AnchorScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(mpfService.currentRoot()).thenReturn(ROOT_HEX);
        when(mpfService.currentTreeVersion()).thenReturn(VERSION);
        scheduler = new AnchorScheduler(mpfService, anchorRepo, issuer);
    }

    @Test
    void anchorIfChanged_issuesAndPersists_whenRootChanged() {
        when(anchorRepo.findTopByOrderByTreeVersionDesc()).thenReturn(Optional.empty());
        when(issuer.issue(eq(ROOT_HEX), any())).thenReturn(TX_HASH);
        when(anchorRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.anchorIfChanged();

        verify(issuer).issue(eq(ROOT_HEX), any());
        ArgumentCaptor<AymMpfAnchorEntity> captor = ArgumentCaptor.forClass(AymMpfAnchorEntity.class);
        verify(anchorRepo).save(captor.capture());
        AymMpfAnchorEntity saved = captor.getValue();
        assertThat(saved.getTreeVersion()).isEqualTo(VERSION);
        assertThat(saved.getRoot()).isEqualTo(ROOT_HEX);
        assertThat(saved.getUverifyTxHash()).isEqualTo(TX_HASH);
    }

    @Test
    void anchorIfChanged_skips_whenRootUnchanged() {
        AymMpfAnchorEntity lastAnchor = new AymMpfAnchorEntity(VERSION, ROOT_HEX, TX_HASH, java.time.Instant.now());
        when(anchorRepo.findTopByOrderByTreeVersionDesc()).thenReturn(Optional.of(lastAnchor));

        scheduler.anchorIfChanged();

        verify(issuer, never()).issue(anyString(), any());
        verify(anchorRepo, never()).save(any());
    }

    @Test
    void anchorIfChanged_includesMetadata() {
        when(anchorRepo.findTopByOrderByTreeVersionDesc()).thenReturn(Optional.empty());
        when(issuer.issue(any(), any())).thenReturn(TX_HASH);
        when(anchorRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.anchorIfChanged();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(Map.class);
        verify(issuer).issue(anyString(), metaCaptor.capture());
        Map<String, Object> meta = metaCaptor.getValue();
        assertThat(meta.get("uverify_template_id")).isEqualTo("aymAnchor");
        assertThat(meta.get("tree_version")).isEqualTo(VERSION);
    }
}
