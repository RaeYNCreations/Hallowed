package com.raeynd.hallowed.resurrection;

/**
 * All possible outcomes of a resurrection attempt.
 */
public enum ResurrectionResult {

    /** Resurrection succeeded. */
    SUCCESS,

    /** The target player is not in the Hallowed state. */
    NOT_HALLOWED,

    /** The resurrector is not standing near a lit Bonfire. */
    NOT_AT_BONFIRE,

    /** The resurrector does not have enough coins. */
    INSUFFICIENT_FUNDS,

    /** The required resurrection delay has not yet elapsed. */
    DELAY_NOT_MET,

    /** The target player is already alive (not Hallowed). */
    ALREADY_ALIVE,

    /** A concurrent resurrection attempt is already in progress. */
    CONCURRENT_ATTEMPT
}
