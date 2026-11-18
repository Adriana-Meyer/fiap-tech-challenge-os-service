-- State of the orchestrated saga of each service order, and the ids of the
-- saga messages already handled (so a redelivery changes nothing).

CREATE TABLE saga_instance (
    id                    CHAR(36)     NOT NULL,
    service_order_id      CHAR(36)     NOT NULL,
    step                  VARCHAR(30)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    payment_method        VARCHAR(50),
    pending_compensations VARCHAR(100),
    failure_reason        VARCHAR(255),
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    CONSTRAINT pk_saga_instance PRIMARY KEY (id),
    CONSTRAINT fk_saga_service_order FOREIGN KEY (service_order_id) REFERENCES service_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_saga_service_order ON saga_instance (service_order_id, created_at);

CREATE TABLE processed_message (
    message_id   CHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_processed_message PRIMARY KEY (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
