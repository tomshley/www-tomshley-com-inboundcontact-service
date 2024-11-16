CREATE EXTENSION CITEXT;
CREATE TABLE IF NOT EXISTS public.customer_contact_requests (
                                                                contact_id VARCHAR(255) NOT NULL,
                                                                name VARCHAR(50) NOT NULL,
                                                                phone VARCHAR(15) NOT NULL,
                                                                email CITEXT NOT NULL,
                                                                message TEXT NOT NULL,
                                                                inbound_time_epochmilli BIGINT NOT NULL,
                                                                PRIMARY KEY (contact_id));
