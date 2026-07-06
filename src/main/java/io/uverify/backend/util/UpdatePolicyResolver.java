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

package io.uverify.backend.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Java port of uverify-ui/src/utils/updatePolicy.ts. The TypeScript file is
 * the reference implementation. Behavioral changes must land there first and
 * be mirrored here.
 */
public final class UpdatePolicyResolver {

    public record PolicyCertificate(String issuer, Map<String, Object> metadata) {
    }

    public record ResolvedPolicy(PolicyMode mode, String owner, List<String> whitelist) {
    }

    public enum PolicyMode {
        APPEND, FIRST, OVERRIDE, RESTRICTED, WHITELIST, ACCUMULATE, FROZEN;

        static PolicyMode fromMetadataValue(Object value) {
            if (value instanceof String text) {
                try {
                    return PolicyMode.valueOf(text.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                }
            }
            return APPEND;
        }
    }

    private UpdatePolicyResolver() {
    }

    public static boolean isReservedKey(String key) {
        return key.startsWith("uverify_") || key.startsWith("uv_og_") || key.equals("uv_tid");
    }

    public static ResolvedPolicy resolvePolicy(List<PolicyCertificate> certificates) {
        if (certificates.isEmpty()) {
            return new ResolvedPolicy(PolicyMode.APPEND, "", List.of());
        }
        Map<String, Object> firstMetadata = certificates.get(0).metadata();
        PolicyMode mode = PolicyMode.fromMetadataValue(firstMetadata.get("uverify_update_policy"));
        String owner = String.valueOf(firstMetadata.getOrDefault("uverify_owner", certificates.get(0).issuer()));
        List<String> whitelist = parseAddressList(firstMetadata.get("uverify_update_whitelist"));

        for (PolicyCertificate certificate : certificates.subList(1, certificates.size())) {
            Map<String, Object> metadata = certificate.metadata();
            boolean isOwner = certificate.issuer().equals(owner);
            if (isOwner) {
                Object requestedMode = firstNonNull(metadata.get("uverify_update_policy"), metadata.get("uverify_policy"));
                if (requestedMode != null) {
                    mode = PolicyMode.fromMetadataValue(requestedMode);
                }
                if (metadata.get("uverify_transfer_ownership") != null) {
                    owner = String.valueOf(metadata.get("uverify_transfer_ownership"));
                }
                if (metadata.get("uverify_whitelist_add") != null) {
                    for (String address : parseAddressList(metadata.get("uverify_whitelist_add"))) {
                        if (!whitelist.contains(address)) {
                            whitelist.add(address);
                        }
                    }
                }
                if (metadata.get("uverify_whitelist_remove") != null) {
                    whitelist.removeAll(parseAddressList(metadata.get("uverify_whitelist_remove")));
                }
                Object freeze = metadata.get("uverify_freeze");
                if (Boolean.TRUE.equals(freeze) || "true".equals(freeze)) {
                    mode = PolicyMode.FROZEN;
                }
            } else if (whitelist.contains(certificate.issuer())) {
                Object requestedMode = firstNonNull(metadata.get("uverify_update_policy"), metadata.get("uverify_policy"));
                if (requestedMode != null) {
                    mode = PolicyMode.fromMetadataValue(requestedMode);
                }
            }
        }
        return new ResolvedPolicy(mode, owner, List.copyOf(whitelist));
    }

    public static List<PolicyCertificate> applyPolicy(List<PolicyCertificate> certificates, ResolvedPolicy policy) {
        if (certificates.size() <= 1) {
            return certificates;
        }
        switch (policy.mode()) {
            case FIRST, FROZEN -> {
                return List.of(certificates.get(0));
            }
            case OVERRIDE -> {
                for (int i = certificates.size() - 1; i >= 0; i--) {
                    boolean hasContent = certificates.get(i).metadata().keySet().stream()
                            .anyMatch(key -> !isReservedKey(key));
                    if (hasContent || i == 0) {
                        return List.of(certificates.get(i));
                    }
                }
                return List.of(certificates.get(0));
            }
            case RESTRICTED -> {
                List<PolicyCertificate> displayed = new ArrayList<>();
                for (int i = 0; i < certificates.size(); i++) {
                    if (i == 0 || certificates.get(i).issuer().equals(policy.owner())) {
                        displayed.add(certificates.get(i));
                    }
                }
                return displayed;
            }
            case WHITELIST -> {
                List<String> currentWhitelist =
                        parseAddressList(certificates.get(0).metadata().get("uverify_update_whitelist"));
                List<PolicyCertificate> displayed = new ArrayList<>();
                displayed.add(certificates.get(0));
                for (int i = 1; i < certificates.size(); i++) {
                    PolicyCertificate certificate = certificates.get(i);
                    if (certificate.issuer().equals(policy.owner())) {
                        Map<String, Object> metadata = certificate.metadata();
                        if (metadata.get("uverify_whitelist_add") != null) {
                            for (String address : parseAddressList(metadata.get("uverify_whitelist_add"))) {
                                if (!currentWhitelist.contains(address)) {
                                    currentWhitelist.add(address);
                                }
                            }
                        }
                        if (metadata.get("uverify_whitelist_remove") != null) {
                            currentWhitelist.removeAll(parseAddressList(metadata.get("uverify_whitelist_remove")));
                        }
                        displayed.add(certificate);
                    } else if (currentWhitelist.contains(certificate.issuer())) {
                        displayed.add(certificate);
                    }
                }
                return displayed;
            }
            case ACCUMULATE -> {
                Map<String, Object> merged = new LinkedHashMap<>(certificates.get(0).metadata());
                for (PolicyCertificate certificate : certificates.subList(1, certificates.size())) {
                    boolean authorised = certificate.issuer().equals(policy.owner())
                            || policy.whitelist().contains(certificate.issuer());
                    if (!authorised) {
                        continue;
                    }
                    for (Map.Entry<String, Object> entry : certificate.metadata().entrySet()) {
                        if (!isReservedKey(entry.getKey()) && !merged.containsKey(entry.getKey())) {
                            merged.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
                return List.of(new PolicyCertificate(certificates.get(0).issuer(), merged));
            }
            default -> {
                return certificates;
            }
        }
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static List<String> parseAddressList(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        return Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
