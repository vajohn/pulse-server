package com.edge.pulse.repositories.psychometric;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the "import reports success but the scoring key never persists" bug
 * (branch {@code feat/psychometric-scoring-engine}).
 *
 * <p><strong>Root cause it guards against:</strong> {@code PsychometricAdminService.saveScoringKey}
 * queues the {@code ScoringKeyVersion} + {@code ScoringKeyItem} inserts into the shared persistence
 * context but does not flush them. The very next step in {@code AssessmentImporter.importPackage}
 * is {@code saveParametricNorms}, whose
 * {@link NormTableVersionRepository#deprecateValidatedNormsByTestId} is annotated
 * {@code @Modifying(clearAutomatically = true)}. With the default {@code flushAutomatically = false}
 * that bulk UPDATE runs on an unrelated table (so Hibernate's query-space auto-flush does NOT flush
 * the pending scoring-key inserts) and then calls {@code EntityManager.clear()}, silently discarding
 * the still-unflushed scoring-key inserts. The outer transaction then commits without them — the
 * import reports success while no {@code scoring_key_version}/{@code scoring_key_item} rows exist.
 *
 * <p>The correct, defensive pairing for a <em>clearing</em> bulk update is
 * {@code clearAutomatically = true, flushAutomatically = true}, so all pending work is flushed to
 * the database BEFORE the context is cleared. This test enforces that invariant on the two
 * psychometric "deprecate the active version then publish a new one" bulk updates that run inside
 * the import transaction. The same publish flow is also exercised by the UI save paths.
 *
 * <p>Before the fix this test FAILS (both deprecate queries clear without flushing); after the fix
 * it PASSES. It needs no database, so it keeps {@code ./gradlew test} green in CI.
 */
class ModifyingQueryFlushClearContractTest {

    /**
     * The bulk "deprecate the current active version" updates that immediately precede a
     * {@code save()} of a new version + child rows in the same transaction. Each MUST flush before
     * it clears, or the freshly-staged (unflushed) rows are discarded by the clear.
     */
    private record ClearingUpdate(Class<?> repository, String method) {}

    private static final List<ClearingUpdate> CLEARING_PUBLISH_UPDATES = List.of(
            new ClearingUpdate(ScoringKeyVersionRepository.class, "deprecateActiveKeysByTestId"),
            new ClearingUpdate(NormTableVersionRepository.class, "deprecateValidatedNormsByTestId"));

    @Test
    void clearingPublishUpdatesMustFlushBeforeClearing() throws Exception {
        for (ClearingUpdate u : CLEARING_PUBLISH_UPDATES) {
            Method method = findUniqueMethod(u.repository(), u.method());
            Modifying mod = method.getAnnotation(Modifying.class);

            assertThat(mod)
                    .as(u.repository().getSimpleName() + "#" + u.method()
                            + " must be a @Modifying bulk update")
                    .isNotNull();

            // Precondition of this guard: these are clearing updates. If that ever changes the
            // flush requirement no longer applies and this list should be revisited.
            assertThat(mod.clearAutomatically())
                    .as(u.repository().getSimpleName() + "#" + u.method()
                            + " is expected to clear the persistence context")
                    .isTrue();

            // The actual invariant: a clearing update that runs while sibling inserts are staged
            // in the same transaction MUST flush first, otherwise those inserts are silently lost.
            assertThat(mod.flushAutomatically())
                    .as(u.repository().getSimpleName() + "#" + u.method()
                            + " is @Modifying(clearAutomatically=true) but flushAutomatically=false"
                            + " — it clears the persistence context WITHOUT flushing pending inserts,"
                            + " silently discarding unflushed sibling rows (e.g. the scoring key"
                            + " staged by saveScoringKey just before saveParametricNorms runs).")
                    .isTrue();
        }
    }

    private static Method findUniqueMethod(Class<?> type, String name) {
        Method found = null;
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                if (found != null) {
                    throw new IllegalStateException("Ambiguous method " + name + " on " + type);
                }
                found = m;
            }
        }
        if (found == null) {
            throw new IllegalStateException("No method " + name + " on " + type
                    + " — has it been renamed? Update this regression guard.");
        }
        return found;
    }
}
