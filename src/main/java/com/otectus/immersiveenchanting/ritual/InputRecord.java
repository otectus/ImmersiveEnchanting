package com.otectus.immersiveenchanting.ritual;

/**
 * One binding-key transition recorded by the client, in milliseconds from the start of the ritual timeline.
 * Inputs are the only gameplay data a client sends; the server replays them through {@link ScoreEngine}.
 */
public record InputRecord(int timeMs, int lane, boolean pressed) {
}
