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

package io.uverify.backend.model;

import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import io.uverify.backend.dto.CertificateData;
import io.uverify.backend.util.CardanoUtils;
import lombok.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UVerifyCertificate {
    private String hash;
    private String algorithm;
    private String issuer;
    private String extra;

    public static UVerifyCertificate fromCertificateData(CertificateData certificateData, String address) {
        return UVerifyCertificate.builder()
                .hash(certificateData.getHash())
                .algorithm(certificateData.getAlgorithm())
                .issuer(HexUtil.encodeHexString(CardanoUtils.extractCredentialFromAddress(address)))
                .extra(certificateData.getMetadata())
                .build();
    }

    public static List<UVerifyCertificate> listFromPlutusData(PlutusData plutusData) {
        List<PlutusData> certificates = ((ListPlutusData) plutusData).getPlutusDataList();
        List<UVerifyCertificate> uVerifyCertificates = new ArrayList<>();
        for (PlutusData certificate : certificates) {
            uVerifyCertificates.add(fromPlutusData(certificate));
        }
        return uVerifyCertificates;
    }

    public static UVerifyCertificate fromPlutusData(PlutusData plutusData) {
        UVerifyCertificate uVerifyCertificate = new UVerifyCertificate();
        try {
            List<PlutusData> certificate = ((ConstrPlutusData) plutusData).getData().getPlutusDataList();

            if (certificate.isEmpty()) {
                return null;
            }
            uVerifyCertificate.setHash(HexUtil.encodeHexString(((BytesPlutusData) certificate.get(0)).getValue()));
            uVerifyCertificate.setAlgorithm(new String(((BytesPlutusData) certificate.get(1)).getValue()));
            uVerifyCertificate.setIssuer(HexUtil.encodeHexString(((BytesPlutusData) certificate.get(2)).getValue()));
            ListPlutusData metadataListData = (ListPlutusData) certificate.get(3);
            List<PlutusData> metadataList = metadataListData.getPlutusDataList();
            StringBuilder extra = new StringBuilder();
            for (PlutusData item : metadataList) {
                extra.append(new String(((BytesPlutusData) item).getValue()));
            }
            uVerifyCertificate.setExtra(extra.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return uVerifyCertificate;
    }

    private static List<String> splitStringIntoChunks(String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < bytes.length) {
            int end = Math.min(start + 64, bytes.length);

            while (end < bytes.length && (bytes[end] & 0xC0) == 0x80) {
                end--;
            }

            String chunk = new String(bytes, start, end - start, StandardCharsets.UTF_8);
            chunks.add(chunk);
            start = end;
        }

        return chunks;
    }

    private byte[] HashToBytes(String hash) {
        return HexUtil.decodeHexString(hash);
    }

    public PlutusData toPlutusData() {
        ListPlutusData metadataListData = new ListPlutusData();
        if (this.extra != null) {
            if (this.extra.getBytes(StandardCharsets.UTF_8).length > 64) {
                List<String> chunks = splitStringIntoChunks(this.extra);
                for (String chunk : chunks) {
                    metadataListData.add(BytesPlutusData.of(chunk));
                }
            } else {
                metadataListData.add(BytesPlutusData.of(this.extra));
            }
        }

        return ConstrPlutusData.of(0,
                BytesPlutusData.of(HashToBytes(hash)),
                BytesPlutusData.of(algorithm.getBytes()),
                BytesPlutusData.of(HashToBytes(issuer)),
                metadataListData);
    }

    public String toString() {
        StringBuilder certificateData = new StringBuilder();
        certificateData.append(this.hash);
        certificateData.append(HexUtil.encodeHexString(this.algorithm.getBytes()));
        certificateData.append(this.issuer);

        List<String> metadata = splitStringIntoChunks(this.extra);
        for (String chunk : metadata) {
            certificateData.append(HexUtil.encodeHexString(chunk.getBytes()));
        }

        return certificateData.toString();
    }
}
