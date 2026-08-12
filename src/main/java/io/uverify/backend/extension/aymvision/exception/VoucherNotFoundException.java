package io.uverify.backend.extension.aymvision.exception;

import java.util.UUID;

public class VoucherNotFoundException extends RuntimeException {
    public VoucherNotFoundException(UUID voucherId) {
        super("Voucher not found: " + voucherId);
    }
}
