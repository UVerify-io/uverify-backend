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

package io.uverify.backend.extension.dto;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.uverify.backend.extension.entity.SocialHubEntity;
import io.uverify.backend.extension.validators.SocialHubDatum;
import lombok.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class SocialHub {
    private String owner;
    private String picture;
    private String name;
    private String subtitle;
    private String x;
    private String telegram;
    private String discord;
    private String youtube;
    private String website;
    private String email;
    @JsonProperty("ada_handle")
    private String adaHandle;
    private String reddit;
    private String instagram;
    private String github;
    private String linkedin;
    @JsonProperty("item_name")
    private String itemName;

    private static String addressFromCredential(String credential, Network network) {
        if (credential == null || credential.isEmpty()) {
            return null;
        }

        return AddressProvider.getEntAddress(Credential.fromKey(credential), network).getAddress();
    }

    public static SocialHub fromSocialHubEntity(SocialHubEntity entity, Network network) {
        String owner = null;
        if (entity.getOwner() != null) {
            owner = addressFromCredential(entity.getOwner(), network);
        }

        return SocialHub.builder()
                .owner(owner)
                .picture(entity.getPicture())
                .name(entity.getName())
                .subtitle(entity.getSubtitle())
                .x(entity.getX())
                .telegram(entity.getTelegram())
                .discord(entity.getDiscord())
                .youtube(entity.getYoutube())
                .website(entity.getWebsite())
                .reddit(entity.getReddit())
                .instagram(entity.getInstagram())
                .linkedin(entity.getLinkedin())
                .github(entity.getGithub())
                .email(entity.getEmail())
                .adaHandle(entity.getAdaHandle())
                .itemName(entity.getAssetId())
                .build();
    }

    private static String fieldFromBase64ToString(byte[] data) {
        if (data == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(data);
    }

    private static byte[] fieldToBase64ByteArray(String data) {
        if (data == null) {
            return null;
        }
        return Base64.getDecoder().decode(data);
    }

    private static String fieldToString(byte[] data) {
        if (data == null) {
            return null;
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static byte[] fieldToByteArray(String data) {
        if (data == null) {
            return null;
        }
        return data.getBytes(StandardCharsets.UTF_8);
    }

    public static SocialHub fromSocialHubDatum(SocialHubDatum socialHubDatum, Network network, String itemName) {
        return SocialHub.builder()
                .owner(addressFromCredential(HexUtil.encodeHexString(socialHubDatum.getOwner()), network))
                .picture(fieldFromBase64ToString(socialHubDatum.getPicture().orElse(null)))
                .name(fieldFromBase64ToString(socialHubDatum.getName().orElse(null)))
                .subtitle(fieldFromBase64ToString(socialHubDatum.getSubtitle().orElse(null)))
                .x(fieldFromBase64ToString(socialHubDatum.getX().orElse(null)))
                .telegram(fieldFromBase64ToString(socialHubDatum.getTelegram().orElse(null)))
                .discord(fieldFromBase64ToString(socialHubDatum.getDiscord().orElse(null)))
                .youtube(fieldFromBase64ToString(socialHubDatum.getYoutube().orElse(null)))
                .website(fieldFromBase64ToString(socialHubDatum.getWebsite().orElse(null)))
                .reddit(fieldFromBase64ToString(socialHubDatum.getReddit().orElse(null)))
                .instagram(fieldFromBase64ToString(socialHubDatum.getInstagram().orElse(null)))
                .linkedin(fieldFromBase64ToString(socialHubDatum.getLinkedin().orElse(null)))
                .github(fieldFromBase64ToString(socialHubDatum.getGithub().orElse(null)))
                .email(fieldFromBase64ToString(socialHubDatum.getEmail().orElse(null)))
                .adaHandle(fieldFromBase64ToString(socialHubDatum.getAdahandle().orElse(null)))
                .itemName(itemName)
                .build();
    }

    public byte[] asBinaryName() {
        return fieldToBase64ByteArray(name);
    }

    public byte[] asBinarySubtitle() {
        return fieldToBase64ByteArray(subtitle);
    }

    public byte[] asBinaryX() {
        return fieldToBase64ByteArray(x);
    }

    public byte[] asBinaryTelegram() {
        return fieldToBase64ByteArray(telegram);
    }

    public byte[] asBinaryDiscord() {
        return fieldToBase64ByteArray(discord);
    }

    public byte[] asBinaryYoutube() {
        return fieldToBase64ByteArray(youtube);
    }

    public byte[] asBinaryWebsite() {
        return fieldToBase64ByteArray(website);
    }

    public byte[] asBinaryReddit() {
        return fieldToBase64ByteArray(reddit);
    }

    public byte[] asBinaryLinkedin() {
        return fieldToBase64ByteArray(linkedin);
    }

    public byte[] asBinaryInstagram() {
        return fieldToBase64ByteArray(instagram);
    }

    public byte[] asBinaryGithub() {
        return fieldToBase64ByteArray(github);
    }

    public byte[] asBinaryEmail() {
        return fieldToBase64ByteArray(email);
    }

    public byte[] asBinaryAdahandle() {
        return fieldToBase64ByteArray(adaHandle);
    }

    public byte[] asBinaryPicture() {
        return fieldToBase64ByteArray(picture);
    }

    public SocialHubDatum toSocialHubDatum(String batchId) {
        byte[] socialHubOwner = null;
        if (owner != null) {
            socialHubOwner = HexUtil.decodeHexString(owner);
        }

        return SocialHubDatum.builder()
                .owner(socialHubOwner)
                .batchId(HexUtil.decodeHexString(batchId))
                .picture(Optional.ofNullable(fieldToByteArray(picture)))
                .name(Optional.ofNullable((fieldToByteArray(name))))
                .subtitle(Optional.ofNullable((fieldToByteArray(subtitle))))
                .x(Optional.ofNullable((fieldToByteArray(x))))
                .telegram(Optional.ofNullable(fieldToByteArray(telegram)))
                .discord(Optional.ofNullable(fieldToByteArray(discord)))
                .youtube(Optional.ofNullable(fieldToByteArray(youtube)))
                .website(Optional.ofNullable(fieldToByteArray(website)))
                .reddit(Optional.ofNullable(fieldToByteArray(reddit)))
                .linkedin(Optional.ofNullable(fieldToByteArray(linkedin)))
                .instagram(Optional.ofNullable(fieldToByteArray(instagram)))
                .github(Optional.ofNullable(fieldToByteArray(github)))
                .email(Optional.ofNullable(fieldToByteArray(email)))
                .adahandle(Optional.ofNullable(fieldToByteArray(adaHandle)))
                .build();
    }

    public SocialHubDatum toBase64SocialHubDatum(String batchId) {
        byte[] socialHubOwner = null;
        if (owner != null) {
            socialHubOwner = HexUtil.decodeHexString(owner);
        }

        return SocialHubDatum.builder()
                .owner(socialHubOwner)
                .batchId(fieldToBase64ByteArray(batchId))
                .picture(Optional.ofNullable(fieldToBase64ByteArray(picture)))
                .name(Optional.ofNullable(fieldToBase64ByteArray(name)))
                .subtitle(Optional.ofNullable(fieldToBase64ByteArray(subtitle)))
                .x(Optional.ofNullable(fieldToBase64ByteArray(x)))
                .telegram(Optional.ofNullable(fieldToBase64ByteArray(telegram)))
                .discord(Optional.ofNullable(fieldToBase64ByteArray(discord)))
                .youtube(Optional.ofNullable(fieldToBase64ByteArray(youtube)))
                .website(Optional.ofNullable(fieldToBase64ByteArray(website)))
                .reddit(Optional.ofNullable(fieldToBase64ByteArray(reddit)))
                .linkedin(Optional.ofNullable(fieldToBase64ByteArray(linkedin)))
                .instagram(Optional.ofNullable(fieldToBase64ByteArray(instagram)))
                .github(Optional.ofNullable(fieldToBase64ByteArray(github)))
                .email(Optional.ofNullable(fieldToBase64ByteArray(email)))
                .adahandle(Optional.ofNullable(fieldToBase64ByteArray(adaHandle)))
                .build();
    }
}
