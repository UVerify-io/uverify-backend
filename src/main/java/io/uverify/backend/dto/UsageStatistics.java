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

package io.uverify.backend.dto;

import io.uverify.backend.enums.UseCaseCategory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class UsageStatistics {
    private final Map<UseCaseCategory, Integer> CertificateCategories = new HashMap<>();

    public UsageStatistics() {
        for (UseCaseCategory category : UseCaseCategory.values()) {
            CertificateCategories.put(category, 0);
        }
    }

    public Map<String, Integer> getUseCaseStatistics() {
        return CertificateCategories.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().getDisplayName(),
                Map.Entry::getValue
            ));
    }

    public void addCertificateToCategory(UseCaseCategory category) {
        CertificateCategories.put(category, CertificateCategories.get(category) + 1);
    }

    public void addCertificatesToCategory(UseCaseCategory category, int count) {
        CertificateCategories.put(category, CertificateCategories.get(category) + count);
    }
}
