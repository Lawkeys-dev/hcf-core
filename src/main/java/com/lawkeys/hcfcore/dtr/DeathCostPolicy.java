package com.lawkeys.hcfcore.dtr;

/**
 * Whether a member's death costs their team DTR right now.
 *
 * <p>Always, except during a phase of the map that says otherwise - SOTW - which
 * belongs to a module written after this one. Asked through this seam
 * (ARCHITECTURE.md section 14); until it is installed, {@link #ALWAYS} keeps the
 * rules of {@code dtr.yml} exactly as they were.
 */
@FunctionalInterface
public interface DeathCostPolicy {

    boolean deathsCostDtr();

    DeathCostPolicy ALWAYS = () -> true;
}
