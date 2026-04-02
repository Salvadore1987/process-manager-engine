package uz.salvadore.processengine.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisInstanceDefinitionMappingTest extends AbstractRedisTest {

    private RedisInstanceDefinitionMapping mapping;

    @BeforeEach
    void setUp() {
        mapping = new RedisInstanceDefinitionMapping(redisTemplate);
    }

    @Nested
    @DisplayName("put() and get()")
    class PutAndGet {

        @Test
        @DisplayName("should store and retrieve mapping")
        void shouldStoreAndRetrieveMapping() {
            // Arrange
            UUID instanceId = UUIDv7.generate();
            UUID definitionId = UUIDv7.generate();

            // Act
            mapping.put(instanceId, definitionId);

            // Assert
            UUID retrieved = mapping.get(instanceId);
            assertThat(retrieved).isEqualTo(definitionId);
        }
    }

    @Nested
    @DisplayName("get()")
    class Get {

        @Test
        @DisplayName("should return null for unknown instanceId")
        void shouldReturnNullForUnknownInstanceId() {
            // Arrange
            UUID unknownId = UUIDv7.generate();

            // Act
            UUID result = mapping.get(unknownId);

            // Assert
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("remove()")
    class Remove {

        @Test
        @DisplayName("should remove mapping so get() returns null")
        void shouldRemoveMapping() {
            // Arrange
            UUID instanceId = UUIDv7.generate();
            UUID definitionId = UUIDv7.generate();
            mapping.put(instanceId, definitionId);

            // Act
            mapping.remove(instanceId);

            // Assert
            assertThat(mapping.get(instanceId)).isNull();
        }
    }

    @Nested
    @DisplayName("allInstanceIds()")
    class AllInstanceIds {

        @Test
        @DisplayName("should return empty set when no mappings exist")
        void shouldReturnEmptySetInitially() {
            // Arrange — empty state after flushAll

            // Act
            Set<UUID> instanceIds = mapping.allInstanceIds();

            // Assert
            assertThat(instanceIds).isEmpty();
        }

        @Test
        @DisplayName("should return all stored instance IDs")
        void shouldReturnAllStoredInstanceIds() {
            // Arrange
            UUID instanceA = UUIDv7.generate();
            UUID instanceB = UUIDv7.generate();
            UUID instanceC = UUIDv7.generate();
            mapping.put(instanceA, UUIDv7.generate());
            mapping.put(instanceB, UUIDv7.generate());
            mapping.put(instanceC, UUIDv7.generate());

            // Act
            Set<UUID> instanceIds = mapping.allInstanceIds();

            // Assert
            assertThat(instanceIds).containsExactlyInAnyOrder(instanceA, instanceB, instanceC);
        }
    }
}
