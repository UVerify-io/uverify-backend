package io.uverify.backend.extension.aymvision.anchor;

import java.util.Map;

public interface UVerifyIssuer {
    /** Issues a certificate anchoring the given hash on-chain.
     *  @return transaction hash (or placeholder) */
    String issue(String hashHex, Map<String, Object> metadata);
}
