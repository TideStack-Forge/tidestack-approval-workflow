package com.ouroboros.fieldtype;

import java.util.*;
import java.util.regex.Pattern;

import io.vavr.control.Either;

import com.ouroboros.expression.EL;

public class FieldTypeTemplateResolver {
  private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\$\\{[^}]+}");

  public Either<FieldTypeCompileException, Object> resolve(Object template,
                                                           Map<String, Object> params,
                                                           FieldTypeDefinition definition) {
    if (template instanceof String stringTemplate) {
      return resolveString(stringTemplate, params, definition);
    }
    if (template instanceof Map<?, ?> mapTemplate) {
      return resolveMap(mapTemplate, params, definition).map(map -> (Object) map);
    }
    if (template instanceof List<?> listTemplate) {
      return resolveList(listTemplate, params, definition).map(list -> (Object) list);
    }
    return Either.right(template);
  }

  public Either<FieldTypeCompileException, Map<String, Object>> resolveMap(Map<?, ?> template,
                                                                           Map<String, Object> params,
                                                                           FieldTypeDefinition definition) {
    return resolveMap(template, params, definition, false);
  }

  public Either<FieldTypeCompileException, Map<String, Object>> resolveMap(Map<?, ?> template,
                                                                           Map<String, Object> params,
                                                                           FieldTypeDefinition definition,
                                                                           boolean skipNullValue) {
    var result = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> entry : template.entrySet()) {
      var key = String.valueOf(entry.getKey());
      var valueResult = resolve(entry.getValue(), params, definition, skipNullValue);
      if (valueResult.isLeft()) {
        return Either.left(valueResult.getLeft());
      }
      if (skipNullValue && valueResult.get() == null) {
        continue;
      }
      result.put(key, valueResult.get());
    }
    return Either.right(result);
  }

  public Either<FieldTypeCompileException, List<Object>> resolveList(List<?> template,
                                                                     Map<String, Object> params,
                                                                     FieldTypeDefinition definition) {
    var result = new ArrayList<Object>();
    for (Object item : template) {
      var itemResult = resolve(item, params, definition);
      if (itemResult.isLeft()) {
        return Either.left(itemResult.getLeft());
      }
      result.add(itemResult.get());
    }
    return Either.right(result);
  }

  private Either<FieldTypeCompileException, Object> resolveString(String template,
                                                                  Map<String, Object> params,
                                                                  FieldTypeDefinition definition) {
    return resolveString(template, params, definition, false);
  }

  private Either<FieldTypeCompileException, Object> resolve(Object template,
                                                            Map<String, Object> params,
                                                            FieldTypeDefinition definition,
                                                            boolean allowNullExpression) {
    if (template instanceof String stringTemplate) {
      return resolveString(stringTemplate, params, definition, allowNullExpression);
    }
    if (template instanceof Map<?, ?> mapTemplate) {
      return resolveMap(mapTemplate, params, definition, allowNullExpression).map(map -> (Object) map);
    }
    if (template instanceof List<?> listTemplate) {
      return resolveList(listTemplate, params, definition).map(list -> (Object) list);
    }
    return Either.right(template);
  }

  private Either<FieldTypeCompileException, Object> resolveString(String template,
                                                                  Map<String, Object> params,
                                                                  FieldTypeDefinition definition,
                                                                  boolean allowNullExpression) {
    if (template == null || !template.contains("${")) {
      return Either.right(template);
    }

    var matcher = EXPRESSION_PATTERN.matcher(template);
    var matches = new ArrayList<String>();
    while (matcher.find()) {
      matches.add(matcher.group());
    }
    if (matches.isEmpty()) {
      return Either.right(template);
    }

    if (matches.size() == 1 && matches.get(0).equals(template)) {
      return evalExpression(matches.get(0), params, definition, allowNullExpression);
    }

    var resolved = template;
    for (String expression : matches) {
      var valueResult = evalExpression(expression, params, definition);
      if (valueResult.isLeft()) {
        return Either.left(valueResult.getLeft());
      }
      resolved = resolved.replace(expression, String.valueOf(valueResult.get()));
    }
    return Either.right(resolved);
  }

  private Either<FieldTypeCompileException, Object> evalExpression(String expression,
                                                                   Map<String, Object> params,
                                                                   FieldTypeDefinition definition) {
    return evalExpression(expression, params, definition, false);
  }

  private Either<FieldTypeCompileException, Object> evalExpression(String expression,
                                                                   Map<String, Object> params,
                                                                   FieldTypeDefinition definition,
                                                                   boolean allowNullExpression) {
    return EL.eval(expression, params)
        .mapLeft(error -> new FieldTypeCompileException(
            "字段模板表达式解析失败: " + expression,
            error,
            definition.getName(),
            definition.getSource(),
            definition.getSourceLocation(),
            "compile"))
        .flatMap(value -> value != null
            ? Either.right(value)
            : allowNullExpression
                ? Either.right(null)
            : Either.left(new FieldTypeCompileException(
                "字段模板表达式结果不能为空: " + expression,
                definition.getName(),
                definition.getSource(),
                definition.getSourceLocation(),
                "compile")));
  }
}
