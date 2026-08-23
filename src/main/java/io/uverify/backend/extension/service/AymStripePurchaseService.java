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

import io.uverify.backend.extension.entity.AymStripePurchaseEntity;
import io.uverify.backend.extension.repository.AymStripePurchaseRepository;
import io.uverify.backend.extension.entity.AymUserContentEntity;
import io.uverify.backend.extension.dto.aymvision.CheckoutInfo;
import io.uverify.backend.extension.config.AymVisionProperties;
import io.uverify.backend.extension.exception.KeyMismatchException;
import io.uverify.backend.extension.exception.PaymentRequiredException;
import io.uverify.backend.extension.exception.SessionAlreadyUsedException;
import io.uverify.backend.extension.dto.aymvision.RedeemResult;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

@Service
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class AymStripePurchaseService {

    private final AymStripeGateway stripeGateway;
    private final AymStripePurchaseRepository purchaseRepo;
    private final AymContentService contentService;
    private final AymVisionProperties properties;

    public AymStripePurchaseService(AymStripeGateway stripeGateway,
                                 AymStripePurchaseRepository purchaseRepo,
                                 AymContentService contentService,
                                 AymVisionProperties properties) {
        this.stripeGateway = stripeGateway;
        this.purchaseRepo = purchaseRepo;
        this.contentService = contentService;
        this.properties = properties;
    }

    @Transactional
    public RedeemResult verifyAndGrant(String publicKeyHex, String profileId, String sessionId) {
        CheckoutInfo info = stripeGateway.retrieveSession(sessionId);

        if (!"paid".equals(info.paymentStatus())) {
            throw new PaymentRequiredException(sessionId);
        }

        String expectedRef = profileHash(publicKeyHex, profileId);
        if (!expectedRef.equals(info.clientReferenceId())) {
            throw new KeyMismatchException(
                    "client_reference_id does not match caller's profileHash");
        }

        if (purchaseRepo.existsById(sessionId)) {
            throw new SessionAlreadyUsedException(sessionId);
        }

        String contentId = properties.getStripe().getProducts().get(info.productId());
        if (contentId == null) {
            throw new KeyMismatchException("Unmapped Stripe product: " + info.productId());
        }

        contentService.grantContent(publicKeyHex, profileId, contentId, "STRIPE");

        purchaseRepo.save(new AymStripePurchaseEntity(sessionId, publicKeyHex, profileId, contentId));

        List<String> ownedContent = contentService.getContent(publicKeyHex, profileId)
                .stream().map(AymUserContentEntity::getContentId).toList();

        return new RedeemResult(contentId, ownedContent);
    }

    static String profileHash(String publicKeyHex, String profileId) {
        byte[] pubKeyBytes = HexFormat.of().parseHex(publicKeyHex.toLowerCase());
        byte[] profileBytes = profileId.getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[pubKeyBytes.length + profileBytes.length];
        System.arraycopy(pubKeyBytes, 0, input, 0, pubKeyBytes.length);
        System.arraycopy(profileBytes, 0, input, pubKeyBytes.length, profileBytes.length);
        Blake2bDigest digest = new Blake2bDigest(224);
        digest.update(input, 0, input.length);
        byte[] result = new byte[28];
        digest.doFinal(result, 0);
        return HexFormat.of().formatHex(result);
    }
}
