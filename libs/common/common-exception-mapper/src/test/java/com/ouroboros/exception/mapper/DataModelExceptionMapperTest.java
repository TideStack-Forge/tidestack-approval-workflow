package com.ouroboros.exception.mapper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.exception.*;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.exception.ValidationException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataModelExceptionMapper 测试类
 *
 * @author Song Mingxu
 */
class DataModelExceptionMapperTest {

  @Test
  void testStatementExceptionToPlatformException() {
    // 创建语句异常
    var statementException = new InvalidStatementException("无效的查询语句");

    // 转换为 OuroborosException
    var result = DataModelExceptionMapper.toOuroborosException(statementException);

    // 验证
    assertNotNull(result);
    assertTrue(result instanceof PlatformException);
    assertEquals("无效的查询语句", result.getMessage());
    assertSame(statementException, result.getCause());
  }

  @Test
  void testDataValidationExceptionToValidationException() {
    // 创建数据验证异常
    var validationException = new DataValidationException("数据验证失败");

    // 转换为 OuroborosException
    var result = DataModelExceptionMapper.toOuroborosException(validationException);

    // 验证
    assertNotNull(result);
    assertTrue(result instanceof ValidationException);
    assertEquals("数据验证失败", result.getMessage());
  }

  @Test
  void testRecordValidationExceptionWithFieldErrors() {
    // 创建带字段错误的记录验证异常
    Map<String, List<String>> fieldErrors = new HashMap<String, List<String>>();
    fieldErrors.put("name", Arrays.asList("名称不能为空", "名称长度不能超过50个字符"));
    fieldErrors.put("age", Arrays.asList("年龄必须大于0"));
    fieldErrors.put("email", Arrays.asList("邮箱格式不正确"));

    var recordValidationException = new RecordValidationException(fieldErrors);

    // 转换为 OuroborosException
    var result = DataModelExceptionMapper.toOuroborosException(recordValidationException);

    // 验证
    assertNotNull(result);
    assertTrue(result instanceof ValidationException);

    var validationException = (ValidationException) result;
    var errors = validationException.getErrors();

    // 验证字段错误信息已正确传递
    assertNotNull(errors);
    assertEquals(3, errors.size());
    assertTrue(errors.containsKey("name"));
    assertTrue(errors.containsKey("age"));
    assertTrue(errors.containsKey("email"));

    // 验证错误消息已合并
    assertEquals("名称不能为空,名称长度不能超过50个字符", errors.get("name"));
    assertEquals("年龄必须大于0", errors.get("age"));
    assertEquals("邮箱格式不正确", errors.get("email"));
  }

  @Test
  void testDataAccessExceptionToInternalException() {
    // 创建数据访问异常
    var dataAccessException = new DataAccessException("数据库连接失败");

    // 转换为 OuroborosException
    var result = DataModelExceptionMapper.toOuroborosException(dataAccessException);

    // 验证
    assertNotNull(result);
    assertTrue(result instanceof InternalException);
    assertEquals("数据库连接失败", result.getMessage());
    assertSame(dataAccessException, result.getCause());
  }

  @Test
  void testNullException() {
    // 测试 null 输入
    var result = DataModelExceptionMapper.toOuroborosException((DataModelException) null);
    assertNull(result);
  }

  @Test
  void testRecordValidationExceptionWithEmptyErrorsFallsBackToMessage() {
    var recordValidationException = new RecordValidationException(new HashMap<String, List<String>>());

    var result = DataModelExceptionMapper.toOuroborosException(recordValidationException);

    assertNotNull(result);
    assertTrue(result instanceof ValidationException);
    assertEquals(recordValidationException.getMessage(), result.getMessage());
  }

  @Test
  void testUnknownDataModelExceptionToInternalException() {
    var unknownException = new DataModelException("未知数据模型异常") {
    };

    var result = DataModelExceptionMapper.toOuroborosException(unknownException);

    assertNotNull(result);
    assertTrue(result instanceof InternalException);
    assertEquals("数据模型操作失败: 未知数据模型异常", result.getMessage());
    assertSame(unknownException, result.getCause());
  }

  @Test
  void testMapperInstanceMapAndSupports() {
    var mapper = new DataModelExceptionMapper();
    var statementException = new InvalidStatementException("无效语句");

    assertTrue(mapper.supports(statementException));
    assertFalse(mapper.supports(new RuntimeException("x")));

    var result = mapper.map(statementException);
    assertTrue(result instanceof PlatformException);
    assertEquals("无效语句", result.getMessage());
  }
}
