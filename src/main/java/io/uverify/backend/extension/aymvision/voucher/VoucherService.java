package io.uverify.backend.extension.aymvision.voucher;

import io.uverify.backend.extension.aymvision.exception.VoucherNotFoundException;
import io.uverify.backend.extension.aymvision.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepo;
    private final RedeemedVoucherRepository redeemedRepo;
    private final ContentService contentService;

    public VoucherService(VoucherRepository voucherRepo,
                          RedeemedVoucherRepository redeemedRepo,
                          ContentService contentService) {
        this.voucherRepo = voucherRepo;
        this.redeemedRepo = redeemedRepo;
        this.contentService = contentService;
    }

    public List<VoucherEntity> create(String contentId, int count, String note) {
        return IntStream.range(0, count)
                .mapToObj(i -> voucherRepo.save(new VoucherEntity(contentId, note)))
                .toList();
    }

    public List<Map<String, Object>> listActive() {
        return voucherRepo.findAll().stream()
                .sorted(Comparator.comparing(VoucherEntity::getCreatedAt).reversed())
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
                .sorted(Comparator.comparing(RedeemedVoucherEntity::getRedeemedAt).reversed())
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
        VoucherEntity voucher = voucherRepo.findById(voucherId)
                .orElseThrow(() -> new VoucherNotFoundException(voucherId));

        // throws AlreadyOwnedException (→409) if already owned — rolls back, voucher survives
        contentService.grantContent(publicKeyHex, profileId, voucher.getContentId(), "VOUCHER");

        // only reached on success
        voucherRepo.delete(voucher);
        redeemedRepo.save(new RedeemedVoucherEntity(voucherId, voucher.getContentId(), voucher.getNote()));

        List<String> ownedContent = contentService.getContent(publicKeyHex, profileId)
                .stream().map(AymUserContentEntity::getContentId).toList();

        return new RedeemResult(voucher.getContentId(), ownedContent);
    }
}
