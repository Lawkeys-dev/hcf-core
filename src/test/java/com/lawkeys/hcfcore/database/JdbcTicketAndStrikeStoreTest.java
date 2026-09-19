package com.lawkeys.hcfcore.database;

import com.lawkeys.hcfcore.database.dao.JdbcStaffStashStore;
import com.lawkeys.hcfcore.database.dao.JdbcStrikeStore;
import com.lawkeys.hcfcore.database.dao.JdbcTicketStore;
import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.staff.StaffSchema;
import com.lawkeys.hcfcore.staff.strike.Strike;
import com.lawkeys.hcfcore.staff.strike.StrikeManager;
import com.lawkeys.hcfcore.staff.ticket.Ticket;
import com.lawkeys.hcfcore.staff.ticket.TicketManager;
import com.lawkeys.hcfcore.staff.ticket.TicketStatus;
import com.lawkeys.hcfcore.staff.ticket.TicketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reports, requests and team strikes against a real SQLite database. */
class JdbcTicketAndStrikeStoreTest {

    @TempDir
    Path tempDir;

    private SQLiteDataSource sqlite;
    private final List<String> log = new ArrayList<>();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath());
    }

    @Test
    void aTicketSurvivesTheRoundTripIncludingItsEmptyFields() throws Exception {
        JdbcTicketStore store = new JdbcTicketStore(sqlite, log::add);
        store.initSchema();
        store.save(new Ticket(3L, TicketType.REQUEST, alice, "Alice", null, null,
                "I am stuck", 1_700L, TicketStatus.OPEN, null));

        Ticket loaded = List.copyOf(store.loadOpen()).get(0);
        assertEquals(TicketType.REQUEST, loaded.type());
        assertEquals(alice, loaded.openedBy());
        assertNull(loaded.target(), "a request names nobody, and must come back naming nobody");
        assertNull(loaded.handledBy());
        assertEquals("I am stuck", loaded.message());
        assertEquals(1_700L, loaded.openedAt());
    }

    /**
     * The queue survives a restart, a closed ticket stays in the table but not in
     * the queue, and the numbering carries on past it rather than reusing its id.
     */
    @Test
    void theQueueOutlivesTheServerAndClosedTicketsStayHistory() throws Exception {
        TicketManager before = new TicketManager(new JdbcTicketStore(sqlite, log::add), () -> 0L);
        before.loadAll();
        long report = before.open(TicketType.REPORT, alice, "Alice", bob, "Bob", "flying").orElseThrow().id();
        long request = before.open(TicketType.REQUEST, bob, "Bob", null, null, "help").orElseThrow().id();
        before.claim(report, "Staff");
        before.close(request, "Staff");
        before.flush();

        TicketManager after = new TicketManager(new JdbcTicketStore(sqlite, log::add), () -> 0L);
        after.loadAll();
        assertEquals(1, after.size(), "the closed request is history, not queue");
        Ticket kept = after.get(report).orElseThrow();
        assertEquals(TicketStatus.CLAIMED, kept.status());
        assertEquals("Staff", kept.handledBy());
        assertEquals(bob, kept.target());

        long next = after.open(TicketType.REQUEST, alice, "Alice", null, null, "again").orElseThrow().id();
        assertTrue(next > request, "a new ticket must not take the closed one's number");
    }

    @Test
    void strikesOutliveTheServerAndAPardonIsGone() throws Exception {
        StrikeManager before = new StrikeManager(new JdbcStrikeStore(sqlite, log::add));
        before.loadAll();
        long first = before.issue(alice, "Wizards", "Steve", "cheating", "aimbot, banned", "Staff", 0L).id();
        before.issue(alice, "Wizards", "", "other", "insults", "Staff", 3_600L);
        before.flush();

        StrikeManager after = new StrikeManager(new JdbcStrikeStore(sqlite, log::add));
        after.loadAll();
        assertEquals(2, after.activeCount(alice));
        Strike kept = after.get(first).orElseThrow();
        assertEquals("cheating", kept.offence());
        assertEquals("aimbot, banned", kept.reason());
        assertEquals("Wizards", kept.teamName());
        assertEquals("Steve", kept.subject());
        assertEquals(Strike.NEVER, kept.expiresAt(), "for ever must survive the round trip as for ever");

        assertTrue(after.pardon(first));
        after.flush();

        StrikeManager later = new StrikeManager(new JdbcStrikeStore(sqlite, log::add));
        later.loadAll();
        assertEquals(1, later.activeCount(alice));
        assertTrue(later.get(first).isEmpty());
    }

    /**
     * A server that ran the previous version has the staff tables up to migration
     * 3. Starting this one must add the ticket and strike tables without touching
     * what is already there - notably a held inventory.
     */
    @Test
    void aDatabaseAtVersionThreeGainsTheNewTables() throws Exception {
        new SchemaMigrator(sqlite, log::add).migrate(StaffSchema.migrations().subList(0, 3));
        JdbcStaffStashStore stashes = new JdbcStaffStashStore(sqlite, log::add);
        stashes.save(alice, new byte[] {1, 2, 3});

        JdbcTicketStore tickets = new JdbcTicketStore(sqlite, log::add);
        tickets.initSchema();
        tickets.save(new Ticket(1L, TicketType.REPORT, alice, "Alice", bob, "Bob", "x",
                1L, TicketStatus.OPEN, null));
        assertEquals(1, tickets.loadOpen().size());

        JdbcStrikeStore strikes = new JdbcStrikeStore(sqlite, log::add);
        strikes.initSchema();
        strikes.save(new Strike(1L, bob, "Knights", "", "other", "r", "Staff", 1L, Strike.NEVER));
        assertEquals(1, strikes.loadAll().size());

        assertArrayEquals(new byte[] {1, 2, 3}, stashes.loadAll().get(alice),
                "the held inventory must survive the upgrade");
    }

    /**
     * A database that ran the player strikes of version 5 - never released - loses that
     * table and gains the team one.
     */
    @Test
    void theStrikesOfVersionFiveMakeWayForTeamStrikes() throws Exception {
        new SchemaMigrator(sqlite, log::add).migrate(StaffSchema.migrations().subList(0, 5));
        try (var connection = sqlite.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO hcf_staff_strikes (id, player_uuid, player_name, reason, "
                    + "issued_by, issued_at, expires_at) VALUES (1, 'x', 'Bob', 'r', 'Staff', 1, 2)");
        }

        JdbcStrikeStore strikes = new JdbcStrikeStore(sqlite, log::add);
        strikes.initSchema();
        assertTrue(strikes.loadAll().isEmpty(), "player strikes are not team strikes");
        strikes.save(new Strike(1L, bob, "Knights", "Bob", "other", "r", "Staff", 1L, Strike.NEVER));
        assertEquals(1, strikes.loadAll().size());
        try (var connection = sqlite.getConnection();
             var tables = connection.getMetaData().getTables(null, null, "hcf_staff_strikes", null)) {
            assertFalse(tables.next(), "the old table is gone");
        }
    }

    @Test
    void theSchemaCanBeAppliedTwice() throws Exception {
        JdbcTicketStore tickets = new JdbcTicketStore(sqlite, log::add);
        tickets.initSchema();
        tickets.initSchema();
        assertTrue(tickets.loadOpen().isEmpty());
        assertEquals(0L, tickets.highestId());
    }
}
