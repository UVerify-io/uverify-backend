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

package io.uverify.backend.extension.service;

import io.uverify.backend.extension.exception.AlreadyOwnedException;
import io.uverify.backend.extension.entity.AymRedeemedVoucherEntity;
import io.uverify.backend.extension.repository.AymRedeemedVoucherRepository;
import io.uverify.backend.extension.entity.AymUserContentEntity;
import io.uverify.backend.extension.entity.AymVoucherEntity;
import io.uverify.backend.extension.repository.AymVoucherRepository;
import io.uverify.backend.extension.dto.aymvision.RedeemResult;
import io.uverify.backend.extension.exception.VoucherNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class AymVoucherService {

    private final AymVoucherRepository voucherRepo;
    private final AymRedeemedVoucherRepository redeemedRepo;
    private final AymContentService contentService;

    public AymVoucherService(AymVoucherRepository voucherRepo,
                          AymRedeemedVoucherRepository redeemedRepo,
                          AymContentService contentService) {
        this.voucherRepo = voucherRepo;
        this.redeemedRepo = redeemedRepo;
        this.contentService = contentService;
    }

    public List<AymVoucherEntity> create(String contentId, int count, String note) {
        return IntStream.range(0, count)
                .mapToObj(i -> voucherRepo.save(new AymVoucherEntity(contentId, note)))
                .toList();
    }

    public List<Map<String, Object>> listActive() {
        return voucherRepo.findAll().stream()
                .sorted(Comparator.comparing(AymVoucherEntity::getCreatedAt).reversed())
                .map(v -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", v.getId().toString());
                    m.put("contentId", v.getContentId());
                    m.put("createdAt", v.getCreatedAt().toString());
                    m.put("note", v.getNote() != null ? v.getNote() : "");
                    return m;
                })
                .toList();
    }

    public List<Map<String, Object>> listRedeemed() {
        return redeemedRepo.findAll().stream()
                .sorted(Comparator.comparing(AymRedeemedVoucherEntity::getRedeemedAt).reversed())
                .map(v -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", v.getId().toString());
                    m.put("contentId", v.getContentId());
                    m.put("redeemedAt", v.getRedeemedAt().toString());
                    m.put("note", v.getNote() != null ? v.getNote() : "");
                    return m;
                })
                .toList();
    }

    @Transactional
    public RedeemResult redeem(String publicKeyHex, String profileId, UUID voucherId) {
        AymVoucherEntity voucher = voucherRepo.findById(voucherId)
                .orElseThrow(() -> new VoucherNotFoundException(voucherId));

        // throws AlreadyOwnedException (→409) if already owned — rolls back, voucher survives
        contentService.grantContent(publicKeyHex, profileId, voucher.getContentId(), "VOUCHER");

        // only reached on success
        voucherRepo.delete(voucher);
        redeemedRepo.save(new AymRedeemedVoucherEntity(voucherId, voucher.getContentId(), voucher.getNote()));

        List<String> ownedContent = contentService.getContent(publicKeyHex, profileId)
                .stream().map(AymUserContentEntity::getContentId).toList();

        return new RedeemResult(voucher.getContentId(), ownedContent);
    }
}
