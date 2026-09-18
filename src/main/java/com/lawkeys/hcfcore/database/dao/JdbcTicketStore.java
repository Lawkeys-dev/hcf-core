package com.lawkeys.hcfcore.database.dao;

import com.lawkeys.hcfcore.database.migration.SchemaMigrator;
import com.lawkeys.hcfcore.staff.StaffSchema;
import com.lawkeys.hcfcore.staff.ticket.Ticket;
import com.lawkeys.hcfcore.staff.ticket.TicketStatus;
import com.lawkeys.hcfcore.staff.ticket.TicketStore;
import com.lawkeys.hcfcore.staff.ticket.TicketType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** JDBC implementation of {@link TicketStore}, valid on MySQL and SQLite alike. */
public final class JdbcTicketStore implements TicketStore {

    private final DataSource dataSource;
    private final Consumer<String> logger;

    public JdbcTicketStore(DataSource dataSource, Consumer<String> logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void initSchema() throws SQLException {
        new SchemaMigrator(dataSource, logger).migrate(StaffSchema.migrations());
    }

    @Override
    public Collection<Ticket> loadOpen() throws SQLException {
        Collection<Ticket> tickets = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, type, opened_by, opened_name, target_uuid, target_name, message, "
                             + "opened_at, status, handled_by FROM hcf_staff_tickets "
                             + "WHERE status <> 'CLOSED'");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String targetUuid = rs.getString("target_uuid");
                tickets.add(new Ticket(
                        rs.getLong("id"),
                        TicketType.valueOf(rs.getString("type")),
                        UUID.fromString(rs.getString("opened_by")),
                        rs.getString("opened_name"),
                        targetUuid == null ? null : UUID.fromString(targetUuid),
                        rs.getString("target_name"),
                        rs.getString("message"),
                        rs.getLong("opened_at"),
                        TicketStatus.valueOf(rs.getString("status")),
                        rs.getString("handled_by")));
            }
        }
        return tickets;
    }

    /** Counts closed tickets too, so a reused id can never collide with history. */
    @Override
    public long highestId() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT MAX(id) FROM hcf_staff_tickets");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    @Override
    public void save(Ticket ticket) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE hcf_staff_tickets SET type = ?, opened_by = ?, opened_name = ?, "
                            + "target_uuid = ?, target_name = ?, message = ?, opened_at = ?, "
                            + "status = ?, handled_by = ? WHERE id = ?")) {
                bind(update, ticket);
                update.setLong(10, ticket.id());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hcf_staff_tickets (type, opened_by, opened_name, target_uuid, "
                            + "target_name, message, opened_at, status, handled_by, id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                bind(insert, ticket);
                insert.setLong(10, ticket.id());
                insert.executeUpdate();
            }
        }
    }

    private static void bind(PreparedStatement statement, Ticket ticket) throws SQLException {
        statement.setString(1, ticket.type().name());
        statement.setString(2, ticket.openedBy().toString());
        statement.setString(3, ticket.openedName());
        statement.setString(4, ticket.target() == null ? null : ticket.target().toString());
        statement.setString(5, ticket.targetName());
        statement.setString(6, ticket.message());
        statement.setLong(7, ticket.openedAt());
        statement.setString(8, ticket.status().name());
        statement.setString(9, ticket.handledBy());
    }
}
