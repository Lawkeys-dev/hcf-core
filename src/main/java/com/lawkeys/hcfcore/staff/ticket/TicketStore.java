package com.lawkeys.hcfcore.staff.ticket;

import java.util.Collection;
import java.util.List;

/**
 * Persistence contract for reports and requests.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface TicketStore {

    void initSchema() throws Exception;

    /** @return tickets still open or claimed; closed ones stay in the table, not in memory */
    Collection<Ticket> loadOpen() throws Exception;

    /** @return the highest id ever used, so numbering continues past closed tickets */
    long highestId() throws Exception;

    void save(Ticket ticket) throws Exception;

    TicketStore NO_OP = new TicketStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Collection<Ticket> loadOpen() {
            return List.of();
        }

        @Override
        public long highestId() {
            return 0L;
        }

        @Override
        public void save(Ticket ticket) {
        }
    };
}
