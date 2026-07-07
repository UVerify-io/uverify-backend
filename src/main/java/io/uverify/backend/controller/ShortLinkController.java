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

package io.uverify.backend.controller;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.enums.CardanoNetwork;
import io.uverify.backend.service.ShortLinkService;
import io.uverify.backend.service.UVerifyCertificateService;
import io.uverify.backend.util.OgDataFactory;
import io.uverify.backend.util.OgDataFactory.OgData;
import io.uverify.backend.util.TemplateIdResolver;
import io.uverify.backend.util.UpdatePolicyResolver;
import io.uverify.backend.util.UpdatePolicyResolver.PolicyCertificate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.uverify.backend.util.CardanoUtils.fromCardanoNetwork;

@Slf4j
@RestController
@RequestMapping("/api/v1/resolve")
public class ShortLinkController {

    private final CardanoNetwork network;
    private final String shortLinkDomain;
    private final String frontendUrl;
    private final ShortLinkService shortLinkService;
    private final UVerifyCertificateService certificateService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ShortLinkController(@Value("${cardano.network}") String network,
                               @Value("${shortlink.domain}") String shortLinkDomain,
                               @Value("${shortlink.frontend-url}") String frontendUrl,
                               ShortLinkService shortLinkService,
                               UVerifyCertificateService certificateService) {
        this.network = CardanoNetwork.valueOf(network.toUpperCase());
        this.shortLinkDomain = shortLinkDomain;
        this.frontendUrl = frontendUrl;
        this.shortLinkService = shortLinkService;
        this.certificateService = certificateService;
    }

    @GetMapping(value = "/{code}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resolve(@PathVariable String code, HttpServletRequest request) {
        Optional<String> hash = shortLinkService.resolve(code);
        if (hash.isEmpty()) {
            return ResponseEntity.status(404).contentType(MediaType.TEXT_HTML).body(notFoundPage());
        }

        List<PolicyCertificate> submissions = certificateService.getCertificatesByHash(hash.get()).stream()
                .sorted(Comparator.comparing(UVerifyCertificateEntity::getSlot))
                .map(this::toPolicyCertificate)
                .toList();
        List<PolicyCertificate> displayed = UpdatePolicyResolver.applyPolicy(
                submissions, UpdatePolicyResolver.resolvePolicy(submissions));
        Map<String, Object> metadata = displayed.isEmpty() ? Map.of() : displayed.get(0).metadata();

        Map<String, String> queryParams = firstValues(request.getParameterMap());
        String templateId = TemplateIdResolver.resolveTemplateId(metadata);
        OgData ogData = OgDataFactory.build(metadata, queryParams, templateId, frontendUrl);

        String redirectUrl = frontendUrl + "/verify/" + hash.get()
                + (request.getQueryString() != null && !request.getQueryString().isBlank()
                ? "?" + request.getQueryString() : "");
        String ogUrl = buildOgUrl(code, OgDataFactory.verifyParams(metadata, queryParams));

        shortLinkService.registerClick(code);

        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=300")
                .contentType(MediaType.TEXT_HTML)
                .body(previewPage(ogData, ogUrl, redirectUrl));
    }

    private PolicyCertificate toPolicyCertificate(UVerifyCertificateEntity entity) {
        Map<String, Object> metadata;
        try {
            metadata = objectMapper.readValue(entity.getExtra(), new TypeReference<>() {
            });
        } catch (Exception exception) {
            metadata = Map.of();
        }
        String issuer;
        try {
            issuer = AddressProvider.getEntAddress(
                    Credential.fromKey(entity.getPaymentCredential()), fromCardanoNetwork(network)).toBech32();
        } catch (Exception exception) {
            issuer = entity.getPaymentCredential();
        }
        return new PolicyCertificate(issuer, metadata);
    }

    private static Map<String, String> firstValues(Map<String, String[]> parameterMap) {
        return parameterMap.entrySet().stream()
                .filter(entry -> entry.getValue().length > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue()[0],
                        (left, right) -> left, LinkedHashMap::new));
    }

    private String buildOgUrl(String code, Map<String, String> verifiedParams) {
        String base = shortLinkDomain + "/" + code;
        if (verifiedParams.isEmpty()) {
            return base;
        }
        String query = verifiedParams.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return base + "?" + query;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String previewPage(OgData ogData, String ogUrl, String redirectUrl) {
        return """
                <!doctype html>
                <html lang="en"><head>
                <meta charset="utf-8">
                <title>%s</title>
                <meta property="og:type" content="website">
                <meta property="og:title" content="%s">
                <meta property="og:description" content="%s">
                <meta property="og:image" content="%s">
                <meta property="og:url" content="%s">
                <meta name="twitter:card" content="summary_large_image">
                <meta name="twitter:image" content="%s">
                <meta http-equiv="refresh" content="0; url=%s">
                </head><body><script>location.replace("%s")</script></body></html>
                """.formatted(
                escapeHtml(ogData.title()),
                escapeHtml(ogData.title()),
                escapeHtml(ogData.description()),
                escapeHtml(ogData.imageUrl()),
                escapeHtml(ogUrl),
                escapeHtml(ogData.imageUrl()),
                escapeHtml(redirectUrl),
                escapeJs(redirectUrl));
    }

    private static String notFoundPage() {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><title>UVerify · Unknown link</title></head>
                <body style="font-family:sans-serif;text-align:center;padding:4rem;background:#030812;color:#fff">
                <h1>Unknown link</h1>
                <p>This UVerify short link does not point to a certificate.</p>
                <p><a style="color:#8fc1fe" href="https://uverify.io">uverify.io</a></p>
                </body></html>
                """;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e");
    }
}
