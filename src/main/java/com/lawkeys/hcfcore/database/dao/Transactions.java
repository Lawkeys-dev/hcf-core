package com.lawkeys.hcfcore.database.dao;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs several statements as one transaction: all of them are written, or none.
 *
 * <p>For writes that touch more than one row or table. Data statements only: on
 * MySQL, a DDL statement commits whatever came before it.
 */
final class Transactions {

    /** The statements to run on the connection the transaction holds. */
    @FunctionalInterface
    interface Work {
        void run(Connection connection) throws SQLException;
    }

    private Transactions() {
    }

    /** Opens a connection, runs {@code work} on it, and commits - or rolls back and rethrows. */
    static void run(DataSource dataSource, Work work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            run(connection, work);
        }
    }

    /** Same, on a connection the caller already holds; its auto-commit setting is put back. */
    static void run(Connection connection, Work work) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            work.run(connection);
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}
