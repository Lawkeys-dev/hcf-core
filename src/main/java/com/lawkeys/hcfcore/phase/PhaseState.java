package com.lawkeys.hcfcore.phase;

/**
 * Where the map stands, as it is persisted: five instants, epoch millis, 0 for
 * "never".
 *
 * <p>Instants rather than flags or countdowns, for the reason the DTR and the
 * deathbans already give: a SOTW that ends at 20:00 ends at 20:00, however many
 * times the server restarts before then, and nothing has to tick for it to.
 *
 * @param sotwEndsAt          when the current or last SOTW ends; it is running while
 *                            now is before this
 * @param eotwSince           when EOTW began, or 0 when it is not under way
 * @param sotwScheduleHandled the scheduled SOTW start already acted on, so it is
 *                            acted on once
 * @param eotwScheduleHandled the scheduled EOTW start already acted on
 * @param purgeEndsAt         when the current or last Purge ends; it is running while
 *                            now is before this
 */
public record PhaseState(long sotwEndsAt, long eotwSince, long sotwScheduleHandled, long eotwScheduleHandled,
                         long purgeEndsAt) {

    public static final PhaseState NONE = new PhaseState(0L, 0L, 0L, 0L, 0L);

    PhaseState withSotwEndsAt(long endsAt) {
        return new PhaseState(endsAt, eotwSince, sotwScheduleHandled, eotwScheduleHandled, purgeEndsAt);
    }

    PhaseState withEotwSince(long since) {
        return new PhaseState(sotwEndsAt, since, sotwScheduleHandled, eotwScheduleHandled, purgeEndsAt);
    }

    PhaseState withSotwScheduleHandled(long handled) {
        return new PhaseState(sotwEndsAt, eotwSince, handled, eotwScheduleHandled, purgeEndsAt);
    }

    PhaseState withEotwScheduleHandled(long handled) {
        return new PhaseState(sotwEndsAt, eotwSince, sotwScheduleHandled, handled, purgeEndsAt);
    }

    PhaseState withPurgeEndsAt(long endsAt) {
        return new PhaseState(sotwEndsAt, eotwSince, sotwScheduleHandled, eotwScheduleHandled, endsAt);
    }
}
