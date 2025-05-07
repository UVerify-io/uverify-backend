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

import io.uverify.backend.extension.entity.TadamonTransactionEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TadamonGoogleSheetsService {

    private final String spreadsheetId;
    private final String accessToken;

    @Autowired
    public TadamonGoogleSheetsService(@Value("${extensions.tadamon.google.sheets.id}") String spreadsheetId,
                                  @Value("${extensions.tadamon.google.sheets.access-token}") String accessToken) {
        this.spreadsheetId = spreadsheetId;
        this.accessToken = accessToken;
    }

    private final RestTemplate restTemplate = new RestTemplate();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public String formatDate(LocalDateTime dateTime) {
        return dateTime.format(FORMATTER);
    }
    private String createRequestBodyFromList(List<String> values) {
        StringBuilder json = new StringBuilder("{\"values\": [[");
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i) != null ? values.get(i).replace("\"", "\\\"") : "";
            json.append("\"").append(value).append("\"");
            if (i < values.size() - 1) {
                json.append(",");
            }
        }
        json.append("]]}");
        return json.toString();
    }

    public void appendRowToSheet(TadamonTransactionEntity transactionEntity) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        String requestBody = createRequestBodyFromList(
                List.of(
                        transactionEntity.getCsoName(),
                        transactionEntity.getCsoEmail(),
                        formatDate(transactionEntity.getCsoEstablishmentDate()),
                        transactionEntity.getCsoOrganizationType(),
                        transactionEntity.getCsoRegistrationCountry(),
                        transactionEntity.getCsoStatusApproved() ? "Y" : "N",
                        transactionEntity.getTadamonId(),
                        transactionEntity.getVeridianAid(),
                        formatDate(transactionEntity.getUndpSigningDate()),
                        formatDate(transactionEntity.getBeneficiarySigningDate()),
                        formatDate(transactionEntity.getCertificateCreationDate()),
                        transactionEntity.getCertificateDataHash(),
                        "https://app.uverify.io/verify/" + transactionEntity.getCertificateDataHash() + "/1"
                )
        );

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        String apiUrl = "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + "/values/Sheet1!A5:M5:append?valueInputOption=RAW&insertDataOption=INSERT_ROWS";
        restTemplate.exchange(apiUrl, HttpMethod.POST, request, String.class);
    }
}
