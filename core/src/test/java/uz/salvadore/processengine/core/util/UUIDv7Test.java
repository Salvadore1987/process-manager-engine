package uz.salvadore.processengine.core.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UUIDv7Test {

    @Test
    void shouldGenerateUuidWithVersion7() {
        // Arrange & Act
        UUID uuid = UUIDv7.generate();

        // Assert
        assertThat(uuid.version()).isEqualTo(7);
    }

    @Test
    void shouldGenerateTimeOrderedUuids() {
        // Arrange & Act
        UUID first = UUIDv7.generate();
        UUID second = UUIDv7.generate();

        // Assert
        assertThat(first.compareTo(second)).isLessThanOrEqualTo(0);
    }

    @Test
    void shouldGenerateUniqueUuids() {
        // Arrange
        Set<UUID> uuids = new HashSet<>();

        // Act
        for (int i = 0; i < 1000; i++) {
            uuids.add(UUIDv7.generate());
        }

        // Assert
        assertThat(uuids).hasSize(1000);
    }

    @Test
    void shouldHaveCorrectVariant() {
        // Arrange & Act
        UUID uuid = UUIDv7.generate();

        // Assert
        assertThat(uuid.variant()).isEqualTo(2);
    }
}
