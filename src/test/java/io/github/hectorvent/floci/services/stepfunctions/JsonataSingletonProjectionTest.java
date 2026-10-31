package io.github.hectorvent.floci.services.stepfunctions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A path whose first step is an array constructor with a predicate. Every expected value is what
 * jsonata-js 2.0.6, the library AWS evaluates with, returns for the same expression and input.
 */
class JsonataSingletonProjectionTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonataEvaluator evaluator = new JsonataEvaluator(mapper);

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void aStepAfterAnIndexedObjectProjectionReturnsNothing(int count) throws Exception {
        JsonNode states = itemsOfEmptyObjects(count);

        assertEquals(count, evaluator.evaluate("{% [$states.input.Items.{'ok':true}] %}", states).size());
        assertTrue(evaluator.evaluate("{% [$states.input.Items.{'ok':true}][0].ok %}", states).isMissingNode());
        assertTrue(evaluator.evaluate("{% [$states.input.Items.{'ok':true}][-1].ok %}", states).isMissingNode());
        assertEquals(BooleanNode.FALSE,
                evaluator.evaluate("{% $exists([$states.input.Items.{'ok':true}][0].ok) %}", states));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void aParenthesizedProjectionIsIndexedAsAnArray(int count) throws Exception {
        JsonNode states = itemsOfEmptyObjects(count);

        JsonNode first = evaluator.evaluate("{% ([$states.input.Items.{'ok':true}])[0].ok %}", states);
        if (count == 0) {
            assertTrue(first.isMissingNode());
        } else {
            assertEquals(BooleanNode.TRUE, first);
        }
        assertEquals(BooleanNode.valueOf(count > 0),
                evaluator.evaluate("{% $exists(([$states.input.Items.{'ok':true}])[0].ok) %}", states));
    }

    static Stream<Arguments> indexedConstructedArrays() {
        return Stream.of(
                Arguments.of("[$states.input.Items][0].a", null),
                Arguments.of("[$states.input.Items][-1].a", null),
                Arguments.of("[$states.input.Items][a=1].a", null),
                Arguments.of("[$states.input.Items][0].a.b", null),
                Arguments.of("[5][0].$string()", null),
                Arguments.of("[$states.input.Items][[0,1]].a", "[1,2]"),
                Arguments.of("$exists([$states.input.Items][0].a)", "false"),
                Arguments.of("{'x':[$states.input.Items][0].a}", "{}"),
                Arguments.of("[$states.input.Items][0]", "{\"a\":1}"),
                Arguments.of("[{'a':1}].a", "1"),
                Arguments.of("[[1,2],[3]][0].$string()", "[\"1\",\"2\"]"),
                Arguments.of("[$states.input.Items][0][].a", "[1]"));
    }

    @ParameterizedTest
    @MethodSource("indexedConstructedArrays")
    void indexedConstructedArrayMatchesJsonataJs(String expression, String expected) throws Exception {
        JsonNode states = mapper.readTree("{\"input\":{\"Items\":[{\"a\":1},{\"a\":2}]}}");

        JsonNode result = evaluator.evaluate("{% " + expression + " %}", states);

        if (expected == null) {
            assertTrue(result.isMissingNode(), result::toString);
        } else {
            assertEquals(expected, result.toString());
        }
    }

    @Test
    void aFieldWhoseIndexedProjectionReturnsNothingFailsAsUndefinedRatherThanACast() throws Exception {
        JsonNode states = itemsOfEmptyObjects(1);

        AslExecutor.FailStateException failure = assertThrows(AslExecutor.FailStateException.class,
                () -> evaluator.evaluateField("{% [$states.input.Items.{'ok':true}][0].ok %}", "Output",
                        states, null));

        assertEquals("States.QueryEvaluationError", failure.error);
        assertEquals("The JSONata expression '[$states.input.Items.{'ok':true}][0].ok' specified for the field "
                + "'Output' returned nothing (undefined).", failure.cause);
        assertFalse(failure.cause.contains("cannot be cast"), failure.cause);
    }

    private JsonNode itemsOfEmptyObjects(int count) throws Exception {
        return mapper.readTree("{\"input\":{\"Items\":[" + "{},".repeat(count).replaceFirst(",$", "") + "]}}");
    }
}
