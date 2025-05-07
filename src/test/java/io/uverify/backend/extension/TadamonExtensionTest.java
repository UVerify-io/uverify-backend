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

import io.uverify.backend.extension.entity.TadamonTransactionEntity;
import io.uverify.backend.extension.service.TadamonGoogleSheetsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
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
                .csoName("Test CSO")
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
        tadamonGoogleSheetsService.appendRowToSheet(tadamonTransactionEntity);
    }
}
