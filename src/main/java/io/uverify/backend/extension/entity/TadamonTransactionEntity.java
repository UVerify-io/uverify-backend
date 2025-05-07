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

import jakarta.persistence.*;
import lombok.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "tadamon_transaction")
public class TadamonTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot", nullable = false)
    private Long slot;

    @Lob
    @Column(name = "transaction_hex", nullable = false)
    private String transactionHex;

    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    @Column(name = "cso_name", nullable = false, length = 128)
    private String csoName;

    @Column(name = "cso_email", nullable = false, length = 128)
    private String csoEmail;

    @Column(name = "cso_establishment_date", nullable = false)
    private LocalDateTime csoEstablishmentDate;

    @Column(name = "cso_organization_type", nullable = false, length = 128)
    private String csoOrganizationType;

    @Column(name = "cso_registration_country", nullable = false, length = 128)
    private String csoRegistrationCountry;

    @Column(name = "cso_status_approved", nullable = false)
    private Boolean csoStatusApproved;

    @Column(name = "tadamon_id", nullable = false, length = 64)
    private String tadamonId;

    @Column(name = "veridian_aid", nullable = false, length = 64)
    private String veridianAid;

    @Column(name = "undp_signing_date", nullable = false)
    private LocalDateTime undpSigningDate;

    @Column(name = "beneficiary_signing_date", nullable = false)
    private LocalDateTime beneficiarySigningDate;

    @Column(name = "certificate_creation_date", nullable = false)
    private LocalDateTime certificateCreationDate;
}
