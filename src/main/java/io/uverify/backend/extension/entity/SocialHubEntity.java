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

package io.uverify.backend.extension.entity;

import com.bloxbean.cardano.client.util.HexUtil;
import io.uverify.backend.extension.validators.SocialHubDatum;
import jakarta.persistence.*;
import lombok.*;

import java.util.Base64;
import java.util.Optional;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "social_hub")
public class SocialHubEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "owner")
    private String owner;

    @Column(name = "picture")
    private String picture;

    @Column(name = "name")
    private String name;

    @Column(name = "subtitle")
    private String subtitle;

    @Column(name = "x")
    private String x;

    @Column(name = "telegram")
    private String telegram;

    @Column(name = "instagram")
    private String instagram;

    @Column(name = "discord")
    private String discord;

    @Column(name = "reddit")
    private String reddit;

    @Column(name = "youtube")
    private String youtube;

    @Column(name = "linkedin")
    private String linkedin;

    @Column(name = "github")
    private String github;

    @Column(name = "website")
    private String website;

    @Column(name = "adahandle")
    private String adaHandle;

    @Column(name = "email")
    private String email;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "output_index", nullable = false)
    private Integer outputIndex;

    @Column(name = "creation_slot", nullable = false)
    private Long creationSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", referencedColumnName = "id")
    private ConnectedGoodEntity connectedGood;

    private static String fieldToString(byte[] data) {
        if (data == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(data);
    }

    private static byte[] fieldToByteArray(String data) {
        if (data == null) {
            return null;
        }
        return Base64.getDecoder().decode(data);
    }

    public static SocialHubEntity fromSocialHubDatum(SocialHubDatum socialHubDatum) {
        return SocialHubEntity.builder()
                .owner(HexUtil.encodeHexString(socialHubDatum.getOwner()))
                .picture(fieldToString(socialHubDatum.getPicture().orElse(null)))
                .name(fieldToString(socialHubDatum.getName().orElse(null)))
                .subtitle(fieldToString(socialHubDatum.getSubtitle().orElse(null)))
                .x(fieldToString(socialHubDatum.getX().orElse(null)))
                .telegram(fieldToString(socialHubDatum.getTelegram().orElse(null)))
                .discord(fieldToString(socialHubDatum.getDiscord().orElse(null)))
                .youtube(fieldToString(socialHubDatum.getYoutube().orElse(null)))
                .website(fieldToString(socialHubDatum.getWebsite().orElse(null)))
                .reddit(fieldToString(socialHubDatum.getReddit().orElse(null)))
                .instagram(fieldToString(socialHubDatum.getInstagram().orElse(null)))
                .linkedin(fieldToString(socialHubDatum.getLinkedin().orElse(null)))
                .github(fieldToString(socialHubDatum.getGithub().orElse(null)))
                .email(fieldToString(socialHubDatum.getEmail().orElse(null)))
                .adaHandle(fieldToString(socialHubDatum.getAdahandle().orElse(null)))
                .build();
    }

    public SocialHubDatum toSocialHubDatum() {
        byte[] socialHubOwner = null;
        if (owner != null) {
            socialHubOwner = HexUtil.decodeHexString(owner);
        }

        byte[] socialHubBatchId = null;
        if (connectedGood != null) {
            socialHubBatchId = HexUtil.decodeHexString(connectedGood.getId());
        }

        return SocialHubDatum.builder()
                .owner(socialHubOwner)
                .batchId(socialHubBatchId)
                .picture(Optional.ofNullable(fieldToByteArray(picture)))
                .name(Optional.ofNullable(fieldToByteArray(name)))
                .subtitle(Optional.ofNullable(fieldToByteArray(subtitle)))
                .x(Optional.ofNullable(fieldToByteArray(x)))
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
}
