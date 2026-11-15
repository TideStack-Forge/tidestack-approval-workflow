package com.ouroboros.data.runtime.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.expression.DataExpressionEvaluator;

class CommonExpressionDataExpressionEvaluatorTest {
  @Test
  void evaluatesWithCommonExpressionSemantics() {
    var evaluator = new CommonExpressionDataExpressionEvaluator();
    var result = evaluator.evaluate("${ 'Hello ' + name }", Map.of("name", "Ada"));

    assertTrue(result.isRight());
    assertEquals("Hello Ada", result.get());
  }

  @Test
  void isAvailableThroughDataExpressionEvaluatorService() {
    var result = DataExpressionEvaluator.eval("${ name }", Map.of("name", "Ada"));

    assertTrue(result.isRight());
    assertEquals("Ada", result.get());
  }
}
