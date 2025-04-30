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

package io.uverify.backend.mapper;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import io.uverify.backend.dto.CertificateResponse;
import io.uverify.backend.entity.UVerifyCertificateEntity;
import io.uverify.backend.enums.CardanoNetwork;
import lombok.extern.slf4j.Slf4j;

import static io.uverify.backend.util.CardanoUtils.fromCardanoNetwork;

@Slf4j
public class CertificateMapper {
    public static CertificateResponse fromCertificate(UVerifyCertificateEntity UVerifyCertificateEntity, CardanoNetwork network) {
        CertificateResponse certificateResponse = new CertificateResponse();
        certificateResponse.setBlockHash(UVerifyCertificateEntity.getBlockHash());
        certificateResponse.setBlockNumber(UVerifyCertificateEntity.getBlockNumber());
        certificateResponse.setHash(UVerifyCertificateEntity.getHash());
        certificateResponse.setSlot(UVerifyCertificateEntity.getSlot());
        certificateResponse.setAddress(UVerifyCertificateEntity.getPaymentCredential());
        certificateResponse.setTransactionHash(UVerifyCertificateEntity.getTransactionId());
        certificateResponse.setCreationTime(UVerifyCertificateEntity.getCreationTime().getTime());
        certificateResponse.setMetadata(UVerifyCertificateEntity.getExtra());
        certificateResponse.setIssuer(AddressProvider.getEntAddress(Credential.fromKey(UVerifyCertificateEntity.getPaymentCredential()), fromCardanoNetwork(network)).toBech32());
        return certificateResponse;
    }
}
