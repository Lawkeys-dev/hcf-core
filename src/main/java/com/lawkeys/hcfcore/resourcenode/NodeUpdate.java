package com.lawkeys.hcfcore.resourcenode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Something the rule layer decided, ready for the server layer to act on.
 *
 * <p>Same shape and same reason as {@code EventUpdate} in the capture module: the
 * engine has no server, so it returns descriptions instead of broadcasting or
 * placing anything.
 *
 * <p>The message key may be <strong>blank</strong>, which means "do this, but say
 * nothing". That is how a node with {@code announce-refill: false} still refills:
 * the decision of whether to speak belongs to the rule layer, where it is
 * testable, and not to the listener that renders the text.
 */
public record NodeUpdate(Type type,
                         String nodeId,
                         String messageKey,
                         Map<String, String> placeholders) {

    public enum Type {
        /** The server layer must refill this node now. */
        REFILL_DUE,
        /** A refill is coming up; nothing to do but tell players. */
        REFILL_SOON
    }

    public NodeUpdate {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(messageKey, "messageKey");
        placeholders = Map.copyOf(Objects.requireNonNull(placeholders, "placeholders"));
    }

    /** @return whether this update carries something to say */
    public boolean hasMessage() {
        return !messageKey.isBlank();
    }

    /** @param placeholders alternating key and value, as elsewhere in the codebase */
    public static NodeUpdate of(Type type, String nodeId, String messageKey,
                                String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders must be key/value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < placeholders.length; i += 2) {
            map.put(placeholders[i], placeholders[i + 1]);
        }
        return new NodeUpdate(type, nodeId, messageKey, map);
    }
}
