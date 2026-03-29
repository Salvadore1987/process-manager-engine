package uz.salvadore.processengine.core.domain.model;

import org.junit.jupiter.api.Test;
import uz.salvadore.processengine.core.domain.enums.TokenState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenTest {

    @Test
    void shouldCreateActiveToken() {
        // Arrange & Act
        Token token = Token.create("node1");

        // Assert
        assertThat(token.getState()).isEqualTo(TokenState.ACTIVE);
        assertThat(token.getCurrentNodeId()).isEqualTo("node1");
        assertThat(token.getId()).isNotNull();
    }

    @Test
    void shouldMoveActiveTokenToNewNode() {
        // Arrange
        Token token = Token.create("node1");

        // Act
        Token moved = token.moveTo("node2");

        // Assert
        assertThat(moved.getCurrentNodeId()).isEqualTo("node2");
        assertThat(moved.getState()).isEqualTo(TokenState.ACTIVE);
        assertThat(moved.getId()).isEqualTo(token.getId());
    }

    @Test
    void shouldTransitionFromActiveToWaiting() {
        // Arrange
        Token token = Token.create("node1");

        // Act
        Token waiting = token.toWaiting();

        // Assert
        assertThat(waiting.getState()).isEqualTo(TokenState.WAITING);
    }

    @Test
    void shouldTransitionFromWaitingToActive() {
        // Arrange
        Token token = Token.create("node1").toWaiting();

        // Act
        Token active = token.toActive();

        // Assert
        assertThat(active.getState()).isEqualTo(TokenState.ACTIVE);
    }

    @Test
    void shouldTransitionFromActiveToCompleted() {
        // Arrange
        Token token = Token.create("node1");

        // Act
        Token completed = token.toCompleted();

        // Assert
        assertThat(completed.getState()).isEqualTo(TokenState.COMPLETED);
    }

    @Test
    void shouldTransitionFromWaitingToCompleted() {
        // Arrange
        Token token = Token.create("node1").toWaiting();

        // Act
        Token completed = token.toCompleted();

        // Assert
        assertThat(completed.getState()).isEqualTo(TokenState.COMPLETED);
    }

    @Test
    void shouldNotMoveWaitingToken() {
        // Arrange
        Token token = Token.create("node1").toWaiting();

        // Act & Assert
        assertThatThrownBy(() -> token.moveTo("node2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotSetCompletedTokenToWaiting() {
        // Arrange
        Token token = Token.create("node1").toCompleted();

        // Act & Assert
        assertThatThrownBy(token::toWaiting)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotCompleteAlreadyCompletedToken() {
        // Arrange
        Token token = Token.create("node1").toCompleted();

        // Act & Assert
        assertThatThrownBy(token::toCompleted)
                .isInstanceOf(IllegalStateException.class);
    }
}
