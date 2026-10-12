package com.otectus.immersiveenchanting.api;

import java.util.Locale;

/**
 * Why a ritual did not start or did not resolve normally. Sent to the client as an id and localized there
 * ({@code immersive_enchanting.abort.<id>}).
 */
public enum AbortReason {
    DISABLED,
    NOT_SUPPORTED,
    INVALID_OFFER,
    NO_ENCHANTMENTS,
    CANNOT_AFFORD,
    ALREADY_ACTIVE,
    MENU_CHANGED,
    ITEM_CHANGED,
    OFFER_CHANGED,
    CANCELLED,
    EXPIRED,
    TIMING_INVALID,
    MALFORMED_INPUT,
    CANCELLED_BY_MOD,
    ADMIN,
    BYPASSED,
    VERSION_MISMATCH,
    INTERNAL_ERROR,
    /** A {@code RitualPlanEvent} listener refused the offer (since 1.1.0); the reason travels with the abort. */
    DENIED_BY_MOD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "immersive_enchanting.abort." + id();
    }

    public static AbortReason byOrdinal(int ordinal) {
        AbortReason[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : INTERNAL_ERROR;
    }
}
