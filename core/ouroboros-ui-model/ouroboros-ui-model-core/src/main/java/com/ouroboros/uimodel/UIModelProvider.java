package com.ouroboros.uimodel;

import java.util.Map;
import java.util.Optional;

@SuppressWarnings("unused")
public interface UIModelProvider {
  Optional<UIModel> getUIModel(String path);

  Map<String, UIModel> getUIModelMap();
}
