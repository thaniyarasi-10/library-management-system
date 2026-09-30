package com.kovanlabs.librarymanagement.database.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BaseEntityTest {

    static class ConcreteEntity extends BaseEntity {
        public ConcreteEntity() {
            super();
        }
    }

    @Test
    @DisplayName("prePersist should initialize uuid, createdAt, and updatedAt if null")
    void prePersist_whenFieldsNull_shouldPopulateDefaults() {
        ConcreteEntity entity = new ConcreteEntity();
        entity.setUuid(null);
        entity.setCreatedAt(null);
        entity.setUpdatedAt(null);

        entity.prePersist();

        assertNotNull(entity.getUuid());
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Test
    @DisplayName("prePersist should preserve existing uuid and createdAt")
    void prePersist_whenFieldsAlreadySet_shouldPreserveExistingValues() {
        ConcreteEntity entity = new ConcreteEntity();
        String customUuid = UUID.randomUUID().toString();
        LocalDateTime customCreated = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime customUpdated = LocalDateTime.of(2025, 1, 1, 10, 0);

        entity.setUuid(customUuid);
        entity.setCreatedAt(customCreated);
        entity.setUpdatedAt(customUpdated);

        entity.prePersist();

        assertEquals(customUuid, entity.getUuid());
        assertEquals(customCreated, entity.getCreatedAt());
        assertEquals(customUpdated, entity.getUpdatedAt());
    }

    @Test
    @DisplayName("preUpdate should update updatedAt timestamp")
    void preUpdate_shouldUpdateTimestamp() {
        ConcreteEntity entity = new ConcreteEntity();
        LocalDateTime oldTime = LocalDateTime.of(2024, 1, 1, 10, 0);
        entity.setUpdatedAt(oldTime);

        entity.preUpdate();

        assertNotNull(entity.getUpdatedAt());
        assertNotEquals(oldTime, entity.getUpdatedAt());
    }

    @Test
    @DisplayName("All entity classes should extend BaseEntity")
    void allEntities_shouldExtendBaseEntity() {
        assertTrue(BaseEntity.class.isAssignableFrom(Book.class));
        assertTrue(BaseEntity.class.isAssignableFrom(Borrow.class));
        assertTrue(BaseEntity.class.isAssignableFrom(Fine.class));
        assertTrue(BaseEntity.class.isAssignableFrom(Membership.class));
        assertTrue(BaseEntity.class.isAssignableFrom(Reward.class));
        assertTrue(BaseEntity.class.isAssignableFrom(User.class));
        assertTrue(BaseEntity.class.isAssignableFrom(UserProvider.class));
    }
}
