-- Primitive offset types are stored in this table.
-- If only timestamp based offsets are used this table is optional.
-- Configure pekko.projection.r2dbc.offset-store.offset-table="" if the table is not created.
CREATE TABLE IF NOT EXISTS projection_offset_store (
                                                       projection_name VARCHAR(255) NOT NULL,
                                                       projection_key VARCHAR(255) NOT NULL,
                                                       current_offset VARCHAR(255) NOT NULL,
                                                       manifest VARCHAR(32) NOT NULL,
                                                       mergeable BOOLEAN NOT NULL,
                                                       last_updated BIGINT NOT NULL,
                                                       PRIMARY KEY(projection_name, projection_key)
);

-- Timestamp based offsets are stored in this table.

CREATE TABLE IF NOT EXISTS projection_timestamp_offset_store (
                                                                 projection_name VARCHAR(255) NOT NULL,
                                                                 projection_key VARCHAR(255) NOT NULL,
                                                                 slice INT NOT NULL,
                                                                 persistence_id VARCHAR(255) NOT NULL,
                                                                 seq_nr BIGINT NOT NULL,
    -- timestamp_offset is the db_timestamp of the original event
                                                                 timestamp_offset timestamp with time zone NOT NULL,
    -- timestamp_consumed is when the offset was stored
    -- the consumer lag is timestamp_consumed - timestamp_offset
                                                                 timestamp_consumed timestamp with time zone NOT NULL,
                                                                 PRIMARY KEY(slice ASC, projection_name ASC, timestamp_offset ASC, persistence_id ASC, seq_nr ASC)
) SPLIT AT VALUES ((127), (255), (383), (511), (639), (767), (895));

CREATE TABLE IF NOT EXISTS projection_management (
                                                     projection_name VARCHAR(255) NOT NULL,
                                                     projection_key VARCHAR(255) NOT NULL,
                                                     paused BOOLEAN NOT NULL,
                                                     last_updated BIGINT NOT NULL,
                                                     PRIMARY KEY(projection_name, projection_key)
);