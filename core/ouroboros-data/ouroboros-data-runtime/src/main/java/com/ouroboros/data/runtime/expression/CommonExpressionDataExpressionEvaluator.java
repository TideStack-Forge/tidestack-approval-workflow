package com.ouroboros.data.runtime.expression;

import java.util.Map;

import jakarta.annotation.Priority;

import io.vavr.control.Either;

import com.ouroboros.data.expression.DataExpressionEvaluator;
import com.ouroboros.expression.EL;

@Priority(1000)
public class CommonExpressionDataExpressionEvaluator implements DataExpressionEvaluator {
  @Override
  public Either<Throwable, Object> evaluate(String expression, Map<String, Object> context) {
    return EL.eval(expression, context);
  }

  @Override
  public String wrapExpression(String expression) {
    return EL.wrapExpression(expression);
  }
}
