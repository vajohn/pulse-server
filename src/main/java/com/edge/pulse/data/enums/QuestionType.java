package com.edge.pulse.data.enums;

public enum QuestionType {
    // ── Survey / pulse engine types (unchanged) ──────────────────────────────
    TEXT, RATING, MULTI_RATING,

    // ── Psychometric — existing ───────────────────────────────────────────────
    SCALE,
    /** Renamed from CHOICE in V18 migration. Single correct answer (radio button). */
    CHOICE_SINGLE,

    // ── Psychometric — Phase 2 additions (V18 migration) ─────────────────────
    /** Select all that apply — may have partial credit scoring. */
    CHOICE_MULTIPLE,
    /** Tap adjectives that describe you — stored in JSONB. */
    ADJECTIVE_CHECKLIST,
    /** Pick which statement is "more like me" — preference-based, no correct answer. */
    FORCED_CHOICE,

    // ── Legacy value kept for backward compatibility during V18 migration ─────
    /** @deprecated Use {@link #CHOICE_SINGLE}. V18 renames all DB rows. */
    @Deprecated
    CHOICE
}
