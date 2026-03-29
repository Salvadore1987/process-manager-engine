package uz.salvadore.processengine.core.domain.model;

import uz.salvadore.processengine.core.domain.enums.TokenState;
import uz.salvadore.processengine.core.util.UUIDv7;

import java.util.UUID;

public final class Token {

    private final UUID id;
    private final String currentNodeId;
    private final TokenState state;

    private Token(UUID id, String currentNodeId, TokenState state) {
        this.id = id;
        this.currentNodeId = currentNodeId;
        this.state = state;
    }

    public static Token create(String currentNodeId) {
        return new Token(UUIDv7.generate(), currentNodeId, TokenState.ACTIVE);
    }

    public static Token restore(UUID id, String currentNodeId, TokenState state) {
        return new Token(id, currentNodeId, state);
    }

    public Token moveTo(String newNodeId) {
        if (this.state != TokenState.ACTIVE) {
            throw new IllegalStateException("Cannot move token in state " + this.state);
        }
        return new Token(this.id, newNodeId, TokenState.ACTIVE);
    }

    public Token toWaiting() {
        if (this.state != TokenState.ACTIVE) {
            throw new IllegalStateException("Cannot set token to WAITING from state " + this.state);
        }
        return new Token(this.id, this.currentNodeId, TokenState.WAITING);
    }

    public Token toActive() {
        if (this.state != TokenState.WAITING) {
            throw new IllegalStateException("Cannot set token to ACTIVE from state " + this.state);
        }
        return new Token(this.id, this.currentNodeId, TokenState.ACTIVE);
    }

    public Token toCompleted() {
        if (this.state == TokenState.COMPLETED) {
            throw new IllegalStateException("Token is already COMPLETED");
        }
        return new Token(this.id, this.currentNodeId, TokenState.COMPLETED);
    }

    public UUID getId() {
        return id;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public TokenState getState() {
        return state;
    }
}
