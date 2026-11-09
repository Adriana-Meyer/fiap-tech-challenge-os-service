package com.fiap.workshop.management.domain.repository;

import java.util.UUID;

/** Remembers which saga messages were already handled, so a redelivery is harmless. */
public interface ProcessedMessageRepository {

    /** @return true if the message is new and is now recorded; false if it was already processed */
    boolean markProcessed(UUID messageId);
}
