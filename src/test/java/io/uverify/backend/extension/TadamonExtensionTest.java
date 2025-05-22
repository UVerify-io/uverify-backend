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

package io.uverify.backend.extension;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.transaction.spec.VkeyWitness;
import com.bloxbean.cardano.client.util.HexUtil;
import io.uverify.backend.extension.entity.TadamonTransactionEntity;
import io.uverify.backend.extension.service.TadamonGoogleSheetsService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.EnabledIf;

import java.time.LocalDateTime;
import java.util.Arrays;

@SpringBootTest
@EnabledIf(
        expression = "${extensions.tadamon.enabled}",
        loadContext = true,
        reason = "Tadamon extension must be enabled for this test"
)
public class TadamonExtensionTest {

    @Autowired
    private TadamonGoogleSheetsService tadamonGoogleSheetsService;

    @Test
    public void testWriteToGoogleSheet() {
        TadamonTransactionEntity tadamonTransactionEntity = TadamonTransactionEntity.builder()
                .tadamonId("1234567890")
                .transactionId("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")
                .beneficiarySigningDate(LocalDateTime.of(2023, 10, 1, 12, 0))
                .certificateDataHash("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")
                .csoName("Test CSO 1")
                .csoEmail("test@csotest.tld")
                .csoEstablishmentDate(LocalDateTime.of(2023, 10, 1, 12, 0))
                .csoOrganizationType("Test Organization Type")
                .certificateCreationDate(LocalDateTime.of(2024, 10, 1, 12, 0))
                .undpSigningDate(LocalDateTime.of(2024, 10, 1, 12, 0))
                .veridianAid("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")
                .csoStatusApproved(true)
                .csoRegistrationCountry("Test Country")
                .transactionHex("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")
                .build();
        tadamonGoogleSheetsService.writeRowToSheet(tadamonTransactionEntity, 5);
    }

    @Test
    void compareKeys() {
        String vkey = "51dbae874ef94ba4a42d6bc935696e50b545eb8dc0cd0bb8d3c7ddb2699fa581";
        String addressStr = "addr_test1qpftcj63cky29z6xq69hm454c4ru0tyq89aqcm5kd65wzsevvxgywp50vfnt0raqf0p6y9rq07y4rsrc4fu3k528rc0q8gvagn";
        Address address = new Address(addressStr);

        String paymentCredHash = HexUtil.encodeHexString(address.getPaymentCredentialHash().get());
        byte[] vkeyHash = Blake2bUtil.blake2bHash224(HexUtil.decodeHexString(vkey));
        String vkeyHashHex = HexUtil.encodeHexString(vkeyHash);

        Assertions.assertEquals(paymentCredHash, vkeyHashHex);
    }

    @Test
    public void testFindRowByDataHash() {
        int expectedRow = tadamonGoogleSheetsService.findRowByDataHash("xds2318732837");
        Assertions.assertEquals(4, expectedRow, "The row should be 4");
    }

    @Test
    public void testGetFirstEmptyRow() {
        int firstEmptyRow = tadamonGoogleSheetsService.findRowByDataHash();
        Assertions.assertEquals(6, firstEmptyRow, "The first empty row should be 6");
    }
}
