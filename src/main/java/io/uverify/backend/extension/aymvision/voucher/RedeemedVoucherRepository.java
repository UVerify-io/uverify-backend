package io.uverify.backend.extension.aymvision.voucher;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RedeemedVoucherRepository extends JpaRepository<RedeemedVoucherEntity, UUID> {}
