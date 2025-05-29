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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.uverify.backend.extension.entity.TadamonTransactionEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@ConditionalOnProperty(value = "extensions.tadamon.enabled", havingValue = "true")
public class TadamonGoogleSheetsService {

    private final String spreadsheetId;
    private final String serviceAccountEmail;
    private final PrivateKey privateKey;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");


    @Autowired
    public TadamonGoogleSheetsService(@Value("${extensions.tadamon.google.sheets.id}") String spreadsheetId,
                                      @Value("${extensions.tadamon.google.sheets.private-key}") String privateKeyString,
                                      @Value("${extensions.tadamon.google.sheets.service-account}") String serviceAccountEmail) {
        this.spreadsheetId = spreadsheetId;
        this.serviceAccountEmail = serviceAccountEmail;
        privateKeyString = privateKeyString
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] privateKeyBytes = Base64.decodeBase64(privateKeyString);
        KeyFactory keyFactory = null;
        try {
            keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            this.privateKey = keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }
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

    private String generateJwt() throws Exception {
        Instant now = Instant.now();
        long expirySeconds = now.plusSeconds(3600).getEpochSecond();

        Map<String, Object> header = Map.of("alg", "RS256", "typ", "JWT");
        Map<String, Object> payload = Map.of(
                "iss", serviceAccountEmail,
                "scope", "https://www.googleapis.com/auth/spreadsheets",
                "aud", "https://oauth2.googleapis.com/token",
                "exp", expirySeconds,
                "iat", now.getEpochSecond()
        );

        String encodedHeader = Base64.encodeBase64URLSafeString(objectMapper.writeValueAsBytes(header));
        String encodedPayload = Base64.encodeBase64URLSafeString(objectMapper.writeValueAsBytes(payload));

        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update((encodedHeader + "." + encodedPayload).getBytes(StandardCharsets.UTF_8));
        String encodedSignature = Base64.encodeBase64URLSafeString(signature.sign());

        return encodedHeader + "." + encodedPayload + "." + encodedSignature;
    }

    private String getAccessToken() throws Exception {
        String jwt = generateJwt();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        requestBody.add("assertion", jwt);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

        String tokenEndpoint = "https://oauth2.googleapis.com/token";
        JsonNode response = restTemplate.postForObject(tokenEndpoint, request, JsonNode.class);

        if (response != null && response.has("access_token")) {
            return response.get("access_token").asText();
        } else if (response != null && response.has("error")) {
            throw new RuntimeException("Error getting access token: " + response.get("error_description").asText());
        } else {
            throw new RuntimeException("Failed to retrieve access token");
        }
    }

    public void writeRowToSheet(TadamonTransactionEntity transactionEntity, int row) {
        String accessToken = null;
        try {
            accessToken = getAccessToken();
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
                            // formatDate(transactionEntity.getBeneficiarySigningDate()),
                            formatDate(transactionEntity.getCertificateCreationDate()),
                            transactionEntity.getCertificateDataHash(),
                            "https://app.uverify.io/verify/" + transactionEntity.getCertificateDataHash() + "/1"
                    )
            );

            final String range = "Sheet1!A" + row + ":M" + row;

            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
            String apiUrl = "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + "/values/" + range + "?valueInputOption=RAW";
            restTemplate.exchange(apiUrl, HttpMethod.PUT, request, String.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int findRowByDataHash() {
        return findRowByDataHash(null);
    }
    public int findRowByDataHash(String dataHash) {
        String accessToken = null;
        try {
            accessToken = getAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<String> request = new HttpEntity<>(headers);
            String apiUrl = "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + "/values/Sheet1!L:L";
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.GET, request, String.class);
            String responseBody = response.getBody();
            if (dataHash == null) {
                return parseFirstEmptyRow(responseBody);
            } else {
                return findExistingRowOrAppend(responseBody, dataHash);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get the first empty row", e);
        }
    }

    private int parseFirstEmptyRow(String responseBody) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode valuesNode = rootNode.path("values");
            if (valuesNode.isMissingNode() || !valuesNode.isArray()) {
                return 1;
            }
            return valuesNode.size() + 1;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse the response for the first empty row", e);
        }
    }

    private int findExistingRowOrAppend(String responseBody, String searchString) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(responseBody);

            JsonNode valuesNode = rootNode.path("values");
            if (valuesNode.isMissingNode() || !valuesNode.isArray()) {
                return 1;
            }

            for (int i = 0; i < valuesNode.size(); i++) {
                JsonNode row = valuesNode.get(i);
                if (row.isArray() && row.size() > 0) {
                    String cellValue = row.get(0).asText();
                    if (searchString.equals(cellValue)) {
                        return i + 1;
                    }
                }
            }

            return valuesNode.size() + 1;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse the response for the row by string or first empty row", e);
        }
    }
}
