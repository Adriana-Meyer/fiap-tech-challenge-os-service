package com.fiap.workshop.management.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * The body of every saga message, in both directions:
 * <pre>
 * { "messageId": "...", "sagaId": "...", "serviceOrderId": "...", "type": "GenerateBudget", "payload": { ... } }
 * </pre>
 * {@code type} names the command or event; {@code payload} holds only its own
 * fields, the three ids being in the envelope.
 */
public record MessageEnvelope(UUID messageId, UUID sagaId, UUID serviceOrderId, String type, JsonNode payload) {}
