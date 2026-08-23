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

import io.uverify.backend.extension.dto.aymvision.CheckoutInfo;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionRetrieveParams;
import io.uverify.backend.extension.config.AymVisionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "extensions.aym-vision.enabled", havingValue = "true")
public class AymStripeGatewayImpl implements AymStripeGateway {

    public AymStripeGatewayImpl(AymVisionProperties properties) {
        Stripe.apiKey = properties.getStripe().getApiKey();
    }

    @Override
    public CheckoutInfo retrieveSession(String sessionId) {
        try {
            Session session = Session.retrieve(sessionId,
                    SessionRetrieveParams.builder()
                            .addExpand("line_items")
                            .build(),
                    null);
            String productId = null;
            if (session.getLineItems() != null
                    && session.getLineItems().getData() != null
                    && !session.getLineItems().getData().isEmpty()) {
                productId = session.getLineItems().getData().get(0).getPrice().getProduct();
            }
            return new CheckoutInfo(
                    session.getId(),
                    session.getPaymentStatus(),
                    session.getClientReferenceId(),
                    productId);
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe API error: " + e.getMessage(), e);
        }
    }
}
