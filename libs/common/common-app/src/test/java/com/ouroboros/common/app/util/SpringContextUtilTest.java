package com.ouroboros.common.app.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

class SpringContextUtilTest {
  @Test
  void closeReleasesContextAndSequentialRestartResolvesNewBeans() {
    assertNull(SpringContextUtil.getApplicationContext());
    assertThrows(IllegalStateException.class, () -> SpringContextUtil.getBean(String.class));
    try (var first = context("first")) {
      assertSame(first, SpringContextUtil.getApplicationContext());
      assertEquals("first", SpringContextUtil.getBean(String.class));
      assertEquals("first", SpringContextUtil.getBean("value"));
      assertEquals("first", SpringContextUtil.getBean("value", String.class));
    }
    assertNull(SpringContextUtil.getApplicationContext());
    assertThrows(IllegalStateException.class, () -> SpringContextUtil.getBean(String.class));
    try (var second = context("second")) {
      assertSame(second, SpringContextUtil.getApplicationContext());
      assertEquals("second", SpringContextUtil.getBean(String.class));
    }
    assertNull(SpringContextUtil.getApplicationContext());
    assertThrows(IllegalStateException.class, () -> SpringContextUtil.getBean(String.class));
  }

  @Test
  void closingAnUnselectedContextDoesNotClearTheOwner() {
    try (var owner = context("owner")) {
      try (var other = context("other")) {
        assertSame(owner, SpringContextUtil.getApplicationContext());
      }
      assertSame(owner, SpringContextUtil.getApplicationContext());
    }
    assertNull(SpringContextUtil.getApplicationContext());
    assertThrows(IllegalStateException.class, () -> SpringContextUtil.getBean(String.class));
  }

  private GenericApplicationContext context(String value) {
    var context = new GenericApplicationContext();
    context.registerBean(SpringContextUtil.class);
    context.registerBean("value", String.class, () -> value);
    context.refresh();
    return context;
  }
}
