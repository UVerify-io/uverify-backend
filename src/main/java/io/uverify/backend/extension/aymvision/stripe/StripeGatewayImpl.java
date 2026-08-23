package io.uverify.backend.extension.aymvision.stripe;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionRetrieveParams;
import io.uverify.backend.extension.aymvision.config.AymVisionProperties;
import org.springframework.stereotype.Component;

@Component
public class StripeGatewayImpl implements StripeGateway {

    public StripeGatewayImpl(AymVisionProperties properties) {
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
