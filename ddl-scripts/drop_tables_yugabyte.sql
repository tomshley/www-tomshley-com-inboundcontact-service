
DROP TABLE IF EXISTS customer_contact_requests;

DROP INDEX event_journal_slice_idx;
DROP INDEX snapshot_slice_idx;
DROP INDEX durable_state_slice_idx;

DROP TABLE IF EXISTS event_journal;
DROP TABLE IF EXISTS snapshot;
DROP TABLE IF EXISTS durable_state;
DROP TABLE IF EXISTS projection_offset_store;
DROP TABLE IF EXISTS projection_timestamp_offset_store;
DROP TABLE IF EXISTS projection_management;
