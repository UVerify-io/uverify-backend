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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "aym_chapter_progress")
@IdClass(AymChapterProgressId.class)
public class AymChapterProgressEntity {

    @Id
    @Column(name = "public_key", length = 64)
    private String publicKey;

    @Id
    @Column(name = "profile_id")
    private String profileId;

    @Id
    @Column(name = "course_id")
    private String courseId;

    @Column(name = "chapter_index", nullable = false)
    private int chapterIndex;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AymChapterProgressEntity(String publicKey, String profileId, String courseId, int chapterIndex) {
        this.publicKey = publicKey;
        this.profileId = profileId;
        this.courseId = courseId;
        this.chapterIndex = chapterIndex;
    }
}
