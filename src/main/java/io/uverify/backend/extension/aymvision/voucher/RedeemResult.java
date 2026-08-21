package io.uverify.backend.extension.aymvision.voucher;

import java.util.List;

public record RedeemResult(
        String contentId,
        List<String> ownedContent) {}
