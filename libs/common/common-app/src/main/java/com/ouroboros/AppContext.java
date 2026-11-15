package com.ouroboros;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import io.vavr.control.Try;

import com.ouroboros.metadata.resource.ClasspathMetadataFiles;
import com.ouroboros.model.AppInfo;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
public interface AppContext {

  /**
   * 获取应用路径
   *
   * @return
   */
  String getHomePath();

  default Optional<AppInfo> getAppInfo() {
    final var APP_INFO_FILE = "app.metadata.json";
    var appHome = getHomePath();
    var appInfoFile = Paths.get(appHome, APP_INFO_FILE);
    Map<String, Object> mergedInfo = new LinkedHashMap<>();
    new ClasspathMetadataFiles().legacyThenMetadataContentStreamForOverride(APP_INFO_FILE)
        .filter(StringUtils::isNotBlank)
        .map(Json::toMap)
        .forEach(content -> mergeMap(mergedInfo, content));
    Try.of(() -> FileUtils.readFileToString(appInfoFile.toFile(), "UTF-8"))
        .filter(StringUtils::isNotBlank)
        .map(Json::toMap)
        .forEach(content -> mergeMap(mergedInfo, content));
    if (mergedInfo.isEmpty()) {
      return Optional.empty();
    }
    return Json.tryToBean(AppInfo.class, Json.toJsonString(mergedInfo)).toJavaOptional();
  }

  private static void mergeMap(Map<String, Object> target, Map<String, Object> source) {
    source.forEach((key, value) -> {
      Object current = target.get(key);
      if (current instanceof Map<?, ?> currentMap && value instanceof Map<?, ?> sourceMap) {
        Map<String, Object> mergedChild = new LinkedHashMap<>();
        copyMap(mergedChild, currentMap);
        mergeUntypedMap(mergedChild, sourceMap);
        target.put(key, mergedChild);
        return;
      }
      target.put(key, value);
    });
  }

  private static void mergeUntypedMap(Map<String, Object> target, Map<?, ?> source) {
    source.forEach((key, value) -> {
      if (key instanceof String stringKey) {
        Object current = target.get(stringKey);
        if (current instanceof Map<?, ?> currentMap && value instanceof Map<?, ?> sourceMap) {
          Map<String, Object> mergedChild = new LinkedHashMap<>();
          copyMap(mergedChild, currentMap);
          mergeUntypedMap(mergedChild, sourceMap);
          target.put(stringKey, mergedChild);
          return;
        }
        target.put(stringKey, value);
      }
    });
  }

  private static void copyMap(Map<String, Object> target, Map<?, ?> source) {
    source.forEach((key, value) -> {
      if (key instanceof String stringKey) {
        target.put(stringKey, value);
      }
    });
  }

  default Boolean isDebug() {
    return false;
  }
}
