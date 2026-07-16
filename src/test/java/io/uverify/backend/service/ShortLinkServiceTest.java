/*
 * UVerify Backend
 * Copyright (C) 2025 Fabian Bormann
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package io.uverify.backend.service;

import io.uverify.backend.entity.ShortLinkEntity;
import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.repository.CertificateRepository;
import io.uverify.backend.repository.ShortLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShortLinkServiceTest {

    private static final String HASH = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e";
    private static final String CODE = "RXYWODQzXG";

    @Mock
    private ShortLinkRepository shortLinkRepository;
    @Mock
    private CertificateRepository certificateRepository;
    @InjectMocks
    private ShortLinkService shortLinkService;

    @Test
    void resolvesFromTheMappingTableFirst() {
        given(shortLinkRepository.findById(CODE)).willReturn(Optional.of(
                ShortLinkEntity.builder().shortCode(CODE).certificateHash(HASH).clickCount(0L).build()));

        assertEquals(Optional.of(HASH), shortLinkService.resolve(CODE));
        verify(certificateRepository, never()).findByHashStartingWith(any());
    }

    @Test
    void fallsBackToPrefixLookupAndCachesTheMapping() {
        given(shortLinkRepository.findById(CODE)).willReturn(Optional.empty());
        given(certificateRepository.findByHashStartingWith("a591a6d40bf420")).willReturn(List.of(
                UVerifyCertificateEntity.builder().hash(HASH).build()));
        given(shortLinkRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        assertEquals(Optional.of(HASH), shortLinkService.resolve(CODE));
        verify(shortLinkRepository).save(any(ShortLinkEntity.class));
    }

    @Test
    void prefixCandidatesWithNonMatchingCodesAreFilteredOut() {
        String neighborHash = "a591a6d40bf420ffffffffffffffffffffffffffffffffffffffffffffffffff";
        given(shortLinkRepository.findById(CODE)).willReturn(Optional.empty());
        given(certificateRepository.findByHashStartingWith("a591a6d40bf420")).willReturn(List.of(
                UVerifyCertificateEntity.builder().hash(neighborHash).build(),
                UVerifyCertificateEntity.builder().hash(HASH).build()));
        given(shortLinkRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        assertEquals(Optional.of(HASH), shortLinkService.resolve(CODE));
    }

    @Test
    void unknownAndMalformedCodesResolveEmpty() {
        given(shortLinkRepository.findById(CODE)).willReturn(Optional.empty());
        given(certificateRepository.findByHashStartingWith("a591a6d40bf420")).willReturn(List.of());

        assertEquals(Optional.empty(), shortLinkService.resolve(CODE));
        assertEquals(Optional.empty(), shortLinkService.resolve("not-a-code"));
        assertEquals(Optional.empty(), shortLinkService.resolve(null));
    }

    @Test
    void registerClickDelegatesToTheRepository() {
        shortLinkService.registerClick(CODE);
        verify(shortLinkRepository).incrementClickCount(CODE);
    }
}
