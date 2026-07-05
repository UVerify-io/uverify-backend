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

package io.uverify.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.uverify.backend.enums.UseCaseCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatisticsCategorizeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void nullOrEmptyExtraIsNotary() {
        assertEquals(UseCaseCategory.NOTARY, StatisticsService.categorize(null, mapper));
        assertEquals(UseCaseCategory.NOTARY, StatisticsService.categorize("", mapper));
    }

    @Test
    void invalidJsonIsNotary() {
        assertEquals(UseCaseCategory.NOTARY, StatisticsService.categorize("not-json", mapper));
    }

    @Test
    void missingTemplateIdIsNotary() {
        assertEquals(UseCaseCategory.NOTARY, StatisticsService.categorize("{\"other\":\"x\"}", mapper));
    }

    @Test
    void knownTemplateIdsMapToCategories() {
        assertEquals(UseCaseCategory.IDENTITY,
                StatisticsService.categorize("{\"uverify_template_id\":\"tadamon\"}", mapper));
        assertEquals(UseCaseCategory.CONNECTED_GOODS,
                StatisticsService.categorize("{\"uverify_template_id\":\"socialHub\"}", mapper));
        assertEquals(UseCaseCategory.CONNECTED_GOODS,
                StatisticsService.categorize("{\"uverify_template_id\":\"linktree\"}", mapper));
        assertEquals(UseCaseCategory.CONNECTED_GOODS,
                StatisticsService.categorize("{\"uverify_template_id\":\"productVerification\"}", mapper));
        assertEquals(UseCaseCategory.STUDENT_CERTIFICATION,
                StatisticsService.categorize("{\"uverify_template_id\":\"diploma\"}", mapper));
        assertEquals(UseCaseCategory.CROSS_CHAIN_ATTESTATION,
                StatisticsService.categorize("{\"uverify_template_id\":\"blockforce\"}", mapper));
        assertEquals(UseCaseCategory.NOTARY,
                StatisticsService.categorize("{\"uverify_template_id\":\"somethingElse\"}", mapper));
    }

    @Test
    void shortTemplateIdKeyMapsToCategories() {
        assertEquals(UseCaseCategory.IDENTITY,
                StatisticsService.categorize("{\"uv_tid\":\"tadamon\"}", mapper));
        assertEquals(UseCaseCategory.CONNECTED_GOODS,
                StatisticsService.categorize("{\"uv_tid\":\"socialHub\"}", mapper));
        assertEquals(UseCaseCategory.STUDENT_CERTIFICATION,
                StatisticsService.categorize("{\"uv_tid\":\"diploma\"}", mapper));
        assertEquals(UseCaseCategory.NOTARY,
                StatisticsService.categorize("{\"uv_tid\":\"somethingElse\"}", mapper));
    }

    @Test
    void shortTemplateIdKeyWinsOverLegacyKey() {
        assertEquals(UseCaseCategory.STUDENT_CERTIFICATION,
                StatisticsService.categorize(
                        "{\"uv_tid\":\"diploma\",\"uverify_template_id\":\"tadamon\"}", mapper));
    }

    @Test
    void nonTextualShortKeyFallsBackToLegacyKey() {
        assertEquals(UseCaseCategory.STUDENT_CERTIFICATION,
                StatisticsService.categorize(
                        "{\"uv_tid\":42,\"uverify_template_id\":\"diploma\"}", mapper));
    }
}
