package com.ouroboros;

import java.util.Map;

public interface WebAppInfoModifier {
  void modify(Map<String, Object> webAppInfo);
}
