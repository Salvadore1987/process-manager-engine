package uz.salvadore.processengine.core.adapter.inmemory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryChildInstanceMappingTest {

    private InMemoryChildInstanceMapping mapping;

    @BeforeEach
    void setUp() {
        mapping = new InMemoryChildInstanceMapping();
    }

    @Nested
    @DisplayName("put() and getParent()")
    class PutAndGetParentTests {

        @Test
        @DisplayName("Should store and retrieve parent instance by child instance")
        void shouldStoreAndRetrieveParent() {
            // Arrange
            UUID childId = UUID.randomUUID();
            UUID parentId = UUID.randomUUID();

            // Act
            mapping.put(childId, parentId);

            // Assert
            assertThat(mapping.getParent(childId)).isEqualTo(parentId);
        }

        @Test
        @DisplayName("Should return null for unknown child instance")
        void shouldReturnNullForUnknownChild() {
            // Arrange
            UUID unknownChildId = UUID.randomUUID();

            // Act
            UUID result = mapping.getParent(unknownChildId);

            // Assert
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getChildren()")
    class GetChildrenTests {

        @Test
        @DisplayName("Should return all children for a given parent")
        void shouldReturnAllChildrenForParent() {
            // Arrange
            UUID parentId = UUID.randomUUID();
            UUID child1 = UUID.randomUUID();
            UUID child2 = UUID.randomUUID();
            UUID child3 = UUID.randomUUID();
            mapping.put(child1, parentId);
            mapping.put(child2, parentId);
            mapping.put(child3, parentId);

            // Act
            List<UUID> children = mapping.getChildren(parentId);

            // Assert
            assertThat(children).containsExactlyInAnyOrder(child1, child2, child3);
        }

        @Test
        @DisplayName("Should return empty list for unknown parent")
        void shouldReturnEmptyListForUnknownParent() {
            // Arrange
            UUID unknownParentId = UUID.randomUUID();

            // Act
            List<UUID> children = mapping.getChildren(unknownParentId);

            // Assert
            assertThat(children).isEmpty();
        }

        @Test
        @DisplayName("Should not include children of other parents")
        void shouldNotIncludeChildrenOfOtherParents() {
            // Arrange
            UUID parent1 = UUID.randomUUID();
            UUID parent2 = UUID.randomUUID();
            UUID childOfParent1 = UUID.randomUUID();
            UUID childOfParent2 = UUID.randomUUID();
            mapping.put(childOfParent1, parent1);
            mapping.put(childOfParent2, parent2);

            // Act
            List<UUID> children = mapping.getChildren(parent1);

            // Assert
            assertThat(children).containsExactly(childOfParent1);
            assertThat(children).doesNotContain(childOfParent2);
        }
    }

    @Nested
    @DisplayName("remove()")
    class RemoveTests {

        @Test
        @DisplayName("Should remove mapping so getParent returns null")
        void shouldRemoveMappingSoGetParentReturnsNull() {
            // Arrange
            UUID childId = UUID.randomUUID();
            UUID parentId = UUID.randomUUID();
            mapping.put(childId, parentId);

            // Act
            mapping.remove(childId);

            // Assert
            assertThat(mapping.getParent(childId)).isNull();
        }

        @Test
        @DisplayName("Should remove child from parent's children list")
        void shouldRemoveChildFromParentsChildrenList() {
            // Arrange
            UUID parentId = UUID.randomUUID();
            UUID child1 = UUID.randomUUID();
            UUID child2 = UUID.randomUUID();
            mapping.put(child1, parentId);
            mapping.put(child2, parentId);

            // Act
            mapping.remove(child1);

            // Assert
            List<UUID> children = mapping.getChildren(parentId);
            assertThat(children).containsExactly(child2);
        }
    }
}
