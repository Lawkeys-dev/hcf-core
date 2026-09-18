package com.lawkeys.hcfcore.claim;

/**
 * Whether teams may lock their claims right now.
 *
 * <p>The project owner's rule (12/09/2026): {@code /team lockclaim} exists so that
 * nobody can camp in a team's claim until the end of SOTW, and it works during SOTW
 * only. SOTW belongs to a module written later, so this module declares the
 * question and {@code phase/} answers it, in the manner of {@link ClaimingPolicy};
 * until then {@link #CLOSED} means no claim can ever be locked.
 */
@FunctionalInterface
public interface LockWindow {

    boolean isOpen();

    LockWindow CLOSED = () -> false;
}
