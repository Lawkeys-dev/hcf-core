package com.lawkeys.hcfcore.schedule;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Which tip comes next.
 *
 * <p>In order, or at random - and at random never the same one twice running, which
 * is what "random" means to anybody reading chat: with three tips, a plain random
 * pick repeats itself a third of the time.
 */
public final class TipRotation {

    private final List<String> tips;
    private final boolean random;
    private final RandomGenerator generator;
    private int next;
    private int last = -1;

    public TipRotation(List<String> tips, boolean random, RandomGenerator generator) {
        this.tips = List.copyOf(Objects.requireNonNull(tips, "tips"));
        this.random = random;
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    /** @return the next tip, or empty when there are none */
    public Optional<String> next() {
        if (tips.isEmpty()) {
            return Optional.empty();
        }
        int index;
        if (!random) {
            index = next;
            next = (next + 1) % tips.size();
        } else if (tips.size() == 1) {
            index = 0;
        } else if (last < 0) {
            index = generator.nextInt(tips.size());
        } else {
            // One fewer choice, shifted past the last one: uniform over the others.
            index = generator.nextInt(tips.size() - 1);
            if (index >= last) {
                index++;
            }
        }
        last = index;
        return Optional.of(tips.get(index));
    }
}
