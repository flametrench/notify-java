// Copyright 2026 NDC Digital, LLC
// SPDX-License-Identifier: Apache-2.0

package dev.flametrench.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flametrench.ids.Id;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Flametrench v0.4 conformance suite — Java / JUnit 5 harness for the
 * notifications capability (ADR 0022).
 *
 * <p>Superset matching (result ⊇ expected): every key in the expected object
 * must appear and match in the actual result. Fields not listed in expected
 * (e.g. {@code created_at}, {@code state_changed_at}) are not checked.
 */
class ConformanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern VAR_PATTERN = Pattern.compile("^\\{([a-z_][a-z0-9_]*)\\}$");
    private static final DateTimeFormatter MILLIS_Z =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static JsonNode loadFixture(String relativePath) throws IOException {
        String resource = "/conformance/fixtures/" + relativePath;
        try (InputStream in = ConformanceTest.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Fixture not found: " + resource);
            return MAPPER.readTree(in);
        }
    }

    private static Object resolveVars(Object value, Map<String, Object> variables) {
        if (value instanceof String s) {
            Matcher m = VAR_PATTERN.matcher(s);
            if (m.matches()) {
                String name = m.group(1);
                if (!variables.containsKey(name))
                    throw new IllegalStateException("Unknown variable: {" + name + "}");
                return variables.get(name);
            }
            return s;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) out.add(resolveVars(item, variables));
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet())
                out.put((String) e.getKey(), resolveVars(e.getValue(), variables));
            return out;
        }
        return value;
    }

    private static Object jsonToPlain(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isInt() || node.isLong()) return node.asLong();
        if (node.isDouble() || node.isFloat()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isArray()) {
            List<Object> out = new ArrayList<>();
            for (JsonNode child : node) out.add(jsonToPlain(child));
            return out;
        }
        if (node.isObject()) {
            Map<String, Object> out = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                out.put(e.getKey(), jsonToPlain(e.getValue()));
            }
            return out;
        }
        throw new IllegalStateException("Unhandled JSON type: " + node.getNodeType());
    }

    @SuppressWarnings("unchecked")
    private static NotificationSubject buildSubject(Object raw) {
        Map<String, Object> m = (Map<String, Object>) raw;
        return new NotificationSubject((String) m.get("kind"), (String) m.get("id"));
    }

    private static Map<String, Object> notificationToMap(Notification n) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", n.id());
        map.put("scope", n.scope());
        map.put("recipient_usr_id", n.recipientUsrId());
        map.put("type", n.type());
        map.put("subject", Map.of("kind", n.subject().kind(), "id", n.subject().id()));
        map.put("data", n.data());
        map.put("state", n.state().name());
        map.put("created_at", MILLIS_Z.format(n.createdAt()));
        map.put("state_changed_at", MILLIS_Z.format(n.stateChangedAt()));
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Object invokeOp(
            InMemoryNotifyStore store, String op, Map<String, Object> args
    ) {
        return switch (op) {
            case "create_notification" -> {
                NotificationInput input = new NotificationInput(
                        (String) args.get("scope"),
                        (String) args.get("recipient_usr_id"),
                        (String) args.get("type"),
                        buildSubject(args.get("subject")),
                        (Map<String, Object>) args.get("data")
                );
                String id = store.createNotification(input);
                yield Map.of("id", id);
            }
            case "get_notification" -> {
                Notification n = store.getNotification((String) args.get("id"));
                yield notificationToMap(n);
            }
            case "mark_read" -> {
                store.markRead((String) args.get("id"));
                yield null;
            }
            case "mark_unread" -> {
                store.markUnread((String) args.get("id"));
                yield null;
            }
            case "dismiss" -> {
                store.dismiss((String) args.get("id"));
                yield null;
            }
            default -> throw new IllegalStateException("Unknown fixture op: " + op);
        };
    }

    @SuppressWarnings("unchecked")
    private static void assertSuperset(Object actual, Object expected, String path) {
        if (expected instanceof Map<?, ?> expectedMap) {
            assertInstanceOf(Map.class, actual, "Expected Map at " + path);
            Map<String, Object> actualMap = (Map<String, Object>) actual;
            for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                String key = (String) entry.getKey();
                assertTrue(actualMap.containsKey(key),
                        "Missing key '" + key + "' at " + path);
                assertSuperset(actualMap.get(key), entry.getValue(), path + "." + key);
            }
        } else {
            assertEquals(expected, actual, "Mismatch at " + path);
        }
    }

    @SuppressWarnings("unchecked")
    private static void runTest(JsonNode test) {
        InMemoryNotifyStore store = new InMemoryNotifyStore();
        Map<String, Object> variables = new HashMap<>();

        if (test.has("users")) {
            for (JsonNode userName : test.get("users"))
                variables.put(userName.asText(), Id.generate("usr"));
        }

        for (JsonNode step : test.get("steps")) {
            String op = step.get("op").asText();
            Map<String, Object> resolvedInput = (Map<String, Object>) resolveVars(
                    jsonToPlain(step.get("input")), variables);

            JsonNode expected = step.get("expected");
            if (expected != null && expected.has("error")) {
                fail("Unexpected expected error in fixture: " + expected.get("error").asText());
            }

            Object result = invokeOp(store, op, resolvedInput);

            if (expected != null && expected.has("result")) {
                Object expectedResult = resolveVars(jsonToPlain(expected.get("result")), variables);
                assertSuperset(result, expectedResult, "result");
            }

            JsonNode captures = step.get("captures");
            if (captures != null) {
                Iterator<Map.Entry<String, JsonNode>> it = captures.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    String capturePath = e.getValue().asText();
                    Object captured = result instanceof Map<?, ?> map
                            ? ((Map<?, ?>) map).get(capturePath)
                            : result;
                    variables.put(e.getKey(), captured);
                }
            }
        }

        assertTrue(true);
    }

    private List<DynamicTest> conformanceTests(String fixturePath) throws IOException {
        JsonNode fixture = loadFixture(fixturePath);
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode t : fixture.get("tests")) {
            String id = t.get("id").asText();
            String desc = t.get("description").asText();
            tests.add(DynamicTest.dynamicTest("[" + id + "] " + desc, () -> runTest(t)));
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> lifecycleShape() throws IOException {
        return conformanceTests("notifications/lifecycle-shape.json");
    }
}
