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

import io.uverify.backend.util.UpdatePolicyResolver.PolicyCertificate;
import io.uverify.backend.util.UpdatePolicyResolver.PolicyMode;
import io.uverify.backend.util.UpdatePolicyResolver.ResolvedPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UpdatePolicyResolverTest {

    private static final String OWNER = "addr1owner";
    private static final String OTHER = "addr1other";

    private PolicyCertificate cert(String issuer, Map<String, Object> metadata) {
        return new PolicyCertificate(issuer, metadata);
    }

    @Test
    void defaultsToAppendWithFirstIssuerAsOwner() {
        ResolvedPolicy policy = UpdatePolicyResolver.resolvePolicy(List.of(cert(OWNER, Map.of())));
        assertEquals(PolicyMode.APPEND, policy.mode());
        assertEquals(OWNER, policy.owner());
    }

    @Test
    void firstPolicyDisplaysOnlyTheFirstSubmission() {
        List<PolicyCertificate> certs = List.of(
                cert(OWNER, Map.of("uverify_update_policy", "first", "title", "v1")),
                cert(OWNER, Map.of("title", "v2")));
        ResolvedPolicy policy = UpdatePolicyResolver.resolvePolicy(certs);
        List<PolicyCertificate> displayed = UpdatePolicyResolver.applyPolicy(certs, policy);
        assertEquals(1, displayed.size());
        assertEquals("v1", displayed.get(0).metadata().get("title"));
    }

    @Test
    void overridePolicyDisplaysLatestContentSubmission() {
        List<PolicyCertificate> certs = List.of(
                cert(OWNER, Map.of("uverify_update_policy", "override", "title", "v1")),
                cert(OWNER, Map.of("title", "v2")),
                cert(OWNER, Map.of("uverify_freeze", "false")));
        ResolvedPolicy policy = UpdatePolicyResolver.resolvePolicy(certs);
        List<PolicyCertificate> displayed = UpdatePolicyResolver.applyPolicy(certs, policy);
        assertEquals("v2", displayed.get(0).metadata().get("title"));
    }

    @Test
    void overrideSkipsSubmissionsWithOnlyReservedKeys() {
        List<PolicyCertificate> certs = List.of(
                cert(OWNER, Map.of("uverify_update_policy", "override", "title", "v1")),
                cert(OWNER, Map.of("uv_og_title", "sneaky", "uv_tid", "diploma")));
        ResolvedPolicy policy = UpdatePolicyResolver.resolvePolicy(certs);
        List<PolicyCertificate> displayed = UpdatePolicyResolver.applyPolicy(certs, policy);
        assertEquals("v1", displayed.get(0).metadata().get("title"));
    }

    @Test
    void restrictedPolicyDropsForeignSubmissions() {
        List<PolicyCertificate> certs = List.of(
                cert(OWNER, Map.of("uverify_update_policy", "restricted")),
                cert(OTHER, Map.of("title", "spam")),
                cert(OWNER, Map.of("title", "legit")));
        List<PolicyCertificate> displayed =
                UpdatePolicyResolver.applyPolicy(certs, UpdatePolicyResolver.resolvePolicy(certs));
        assertEquals(2, displayed.size());
        assertEquals("legit", displayed.get(1).metadata().get("title"));
    }

    @Test
    void whitelistAllowsListedIssuers() {
        List<PolicyCertificate> certs = List.of(
                cert(OWNER, Map.of("uverify_update_policy", "whitelist", "uverify_update_whitelist", OTHER)),
                cert(OTHER, Map.of("title", "allowed")),
                cert("addr1stranger", Map.of("title", "blocked")));
        List<PolicyCertificate> displayed =
                UpdatePolicyResolver.applyPolicy(certs, UpdatePolicyResolver.resolvePolicy(certs));
        assertEquals(2, displayed.size());
        assertEquals("allowed", displayed.get(1).metadata().get("title"));
    }

    @Test
    void accumulateMergesOnlyNewUnreservedKeys() {
        List<PolicyCertificate> certs = List.of(
                cert(OWNER, Map.of("uverify_update_policy", "accumulate", "title", "v1")),
                cert(OWNER, Map.of("title", "hijack", "grade", "A", "uv_og_title", "sneaky")));
        List<PolicyCertificate> displayed =
                UpdatePolicyResolver.applyPolicy(certs, UpdatePolicyResolver.resolvePolicy(certs));
        assertEquals(1, displayed.size());
        Map<String, Object> merged = displayed.get(0).metadata();
        assertEquals("v1", merged.get("title"));
        assertEquals("A", merged.get("grade"));
        assertFalse(merged.containsKey("uv_og_title"));
    }

    @Test
    void ownerCanFreezeAndTransferOwnership() {
        List<PolicyCertificate> certs = List.of(
                cert(OWNER, Map.of()),
                cert(OWNER, Map.of("uverify_transfer_ownership", OTHER)),
                cert(OTHER, Map.of("uverify_freeze", "true")));
        ResolvedPolicy policy = UpdatePolicyResolver.resolvePolicy(certs);
        assertEquals(PolicyMode.FROZEN, policy.mode());
        assertEquals(OTHER, policy.owner());
    }

    @Test
    void reservedKeys() {
        assertTrue(UpdatePolicyResolver.isReservedKey("uverify_update_policy"));
        assertTrue(UpdatePolicyResolver.isReservedKey("uv_tid"));
        assertTrue(UpdatePolicyResolver.isReservedKey("uv_og_title"));
        assertFalse(UpdatePolicyResolver.isReservedKey("uv_url_name"));
        assertFalse(UpdatePolicyResolver.isReservedKey("title"));
    }
}
