package com.otectus.immersiveenchanting.session;

/**
 * CREATED -> COUNTDOWN -> PLAYING -> RESOLVING -> RESOLVED, or ABORTED from any active state.
 * COUNTDOWN and PLAYING are told apart by elapsed time: cancelling is free until the first rune is due.
 */
public enum RitualState {
    CREATED,
    COUNTDOWN,
    PLAYING,
    RESOLVING,
    RESOLVED,
    ABORTED;

    public boolean isTerminal() {
        return this == RESOLVED || this == ABORTED;
    }
}
