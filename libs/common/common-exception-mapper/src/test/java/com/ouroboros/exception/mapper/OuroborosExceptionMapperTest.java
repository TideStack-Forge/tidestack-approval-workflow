package com.ouroboros.exception.mapper;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.exception.InvalidStatementException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.PlatformException;

import static org.junit.jupiter.api.Assertions.*;

class OuroborosExceptionMapperTest {

  @Test
  void testToOuroborosExceptionReturnsSameWhenAlreadyOuroborosException() {
    var platformException = new PlatformException("平台异常");

    var result = OuroborosExceptionMapper.toOuroborosException(platformException);

    assertSame(platformException, result);
  }

  @Test
  void testToOuroborosExceptionWithSupplierUsesMapperWhenSupported() {
    var dataException = new InvalidStatementException("无效查询");

    var result = OuroborosExceptionMapper.toOuroborosException(
        dataException,
        () -> new InternalException("fallback"));

    assertTrue(result instanceof PlatformException);
    assertEquals("无效查询", result.getMessage());
    assertSame(dataException, result.getCause());
  }

  @Test
  void testToOuroborosExceptionWithSupplierUsesFallbackWhenUnsupported() {
    var runtimeException = new RuntimeException("runtime");

    var result = OuroborosExceptionMapper.toOuroborosException(
        runtimeException,
        () -> new InternalException("fallback"));

    assertTrue(result instanceof InternalException);
    assertEquals("fallback", result.getMessage());
  }

  @Test
  void testToOuroborosExceptionWithFunctionUsesFallbackWhenUnsupported() {
    var runtimeException = new RuntimeException("runtime");

    var result = OuroborosExceptionMapper.toOuroborosException(
        runtimeException,
        t -> new InternalException("mapped: " + t.getMessage()));

    assertTrue(result instanceof InternalException);
    assertEquals("mapped: runtime", result.getMessage());
  }
}
