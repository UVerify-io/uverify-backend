package io.uverify.backend.extension.aymvision.dto.content;

import io.uverify.backend.extension.aymvision.user.AymUserContentEntity;

import java.time.Instant;

public record ContentItemDto(String contentId, String source, Instant grantedAt) {
    public static ContentItemDto from(AymUserContentEntity e) {
        return new ContentItemDto(e.getContentId(), e.getSource(), e.getGrantedAt());
    }
}
