package com.lawkeys.hcfcore.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The console commands an event runs for its winner. */
class RewardCommandsTest {

    @Test
    void placeholdersAreFilledAndACommandThatFailsDoesNotStopTheOthers() {
        List<String> run = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        RewardCommands.run(
                List.of("give %team% diamond", "broken %event%", "say %team% won %event% (%unknown%)"),
                Map.of("team", "Wizards", "event", "KOTH"),
                command -> {
                    if (command.startsWith("broken")) {
                        throw new IllegalStateException("no such command");
                    }
                    run.add(command);
                },
                (command, error) -> failed.add(command));

        assertEquals(List.of("give Wizards diamond", "say Wizards won KOTH (%unknown%)"), run);
        assertEquals(List.of("broken KOTH"), failed);
    }
}
