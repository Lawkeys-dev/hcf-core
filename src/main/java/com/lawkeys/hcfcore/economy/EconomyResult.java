package com.lawkeys.hcfcore.economy;

/**
 * Outcome of a balance change.
 *
 * <p>An enum rather than an object: economy operations have a small, closed set
 * of outcomes, and the command layer supplies the placeholders it needs anyway.
 * That keeps money movements allocation-free and exhaustively switchable.
 */
public enum EconomyResult {

    OK(null),
    /** The payer does not have that much. */
    INSUFFICIENT_FUNDS(EconomyMessages.INSUFFICIENT_FUNDS),
    /** Zero, negative, or not a finite number. */
    INVALID_AMOUNT(EconomyMessages.INVALID_AMOUNT),
    /** The credit would push the account past the configured ceiling. */
    ABOVE_MAXIMUM(EconomyMessages.ABOVE_MAXIMUM),
    /** The whole module is switched off. */
    DISABLED(EconomyMessages.DISABLED),
    /** Paying yourself. */
    SAME_PLAYER(EconomyMessages.PAY_SELF),
    /** Player-to-player transfers are switched off. */
    PAY_DISABLED(EconomyMessages.PAY_DISABLED),
    /** Below the configured minimum transfer. */
    BELOW_MINIMUM(EconomyMessages.PAY_BELOW_MINIMUM);

    private final String messageKey;

    EconomyResult(String messageKey) {
        this.messageKey = messageKey;
    }

    public boolean isOk() {
        return this == OK;
    }

    /** @return the language key explaining the refusal, or {@code null} on success. */
    public String getMessageKey() {
        return messageKey;
    }
}
