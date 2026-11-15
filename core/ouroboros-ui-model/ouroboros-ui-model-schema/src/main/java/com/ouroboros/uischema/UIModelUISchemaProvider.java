package com.ouroboros.uischema;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import com.ouroboros.uimodel.UIModelProvider;
import com.ouroboros.util.SPI;

@Priority(5)
public class UIModelUISchemaProvider implements UISchemaProvider {
  private final List<UIModelProvider> uiModelFactories;

  public UIModelUISchemaProvider() {
    this.uiModelFactories = SPI.getCachedReversedServiceStream(UIModelProvider.class)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<Object> getUISchema(String path) {
    return uiModelFactories.stream().map(f ->
        f.getUIModel(path)
    ).filter(Optional::isPresent).findFirst().flatMap(m -> m).flatMap(m ->
        "amis".equalsIgnoreCase(m.getType()) ? m.getExtraProp("schema") : Optional.empty()
    );
  }

  @Override
  public Map<String, Object> getUISchemaMap() {
    return uiModelFactories.stream().map(
        UIModelProvider::getUIModelMap
    ).flatMap(map ->
        map.entrySet().stream()
    ).filter(entry ->
        "amis".equalsIgnoreCase(entry.getValue().getType())
            && entry.getValue().getExtraProp("schema").isPresent()
    ).collect(
        () -> new TreeMap<>(String::compareToIgnoreCase),
        (map, entry) -> map.put(
            entry.getKey(),
            entry.getValue().getExtraProp("schema").get()
        ),
        Map::putAll
    );
  }
}
