package com.ouroboros.data.typed.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.vavr.control.Try;

import com.ouroboros.data.TypedDataModelAutoConfiguration;
import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.data.model.*;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;

class TypedModelRuntimeCoverageTest {

  @AfterEach
  void cleanupRegistry() throws Exception {
    DataModelScanRegistry.getDataModelScanAnnotationClasses().clear();
    TypedDataModelCenter.clearDeferredModelRequestRegistry();
    publishDataModels(null);
  }

  @Test
  void baseTypedDataModelDelegatesAndConverts() {
    var dataModel = mock(DataModel.class);
    var typed = new BaseTypedDataModel<>(dataModel, SampleModel.class);

    var input = new SampleModel();
    input.setId("1");
    input.setName("A");
    input.setAge(18);

    var record = Record.of(new HashMap<String, Object>() {{
      put("id", "1");
      put("name", "A");
      put("age", 18);
    }});
    var recordList = RecordList.of(Collections.singletonList(record));

    when(dataModel.insert(any())).thenReturn(Try.success(record));
    when(dataModel.batchInsert(any())).thenReturn(Try.success(recordList));
    when(dataModel.delete(eq("1"))).thenReturn(Try.success(1L));
    when(dataModel.delete(any(List.class))).thenReturn(Try.success(2L));
    when(dataModel.delete(eq(Collections.singletonMap("x", 1)))).thenReturn(Try.success(3L));
    when(dataModel.update(eq("1"), any())).thenReturn(Try.success(1L));
    when(dataModel.update(any(List.class), any())).thenReturn(Try.success(2L));
    when(dataModel.update(eq(Collections.singletonMap("x", 1)), any())).thenReturn(Try.success(3L));
    when(dataModel.count(any(Map.class))).thenReturn(Try.success(10L));
    when(dataModel.get(eq("1"))).thenReturn(Try.success(record));
    when(dataModel.get(eq("1"), any())).thenReturn(Try.success(record));
    when(dataModel.query(any(List.class))).thenReturn(Try.success(recordList));
    when(dataModel.query(any(Map.class))).thenReturn(Try.success(recordList));
    when(dataModel.query(any(List.class), any(Map.class))).thenReturn(Try.success(recordList));
    when(dataModel.withPlugins(any(Collection.class))).thenReturn(dataModel);
    when(dataModel.withoutPlugins()).thenReturn(dataModel);
    when(dataModel.withoutPlugins(any(Collection.class))).thenReturn(dataModel);
    when(dataModel.hasPlugin("p")).thenReturn(true);

    assertEquals("1", typed.insert(input).get().getId());
    assertEquals(1, typed.batchInsert(Collections.singletonList(input)).get().size());
    assertEquals(1L, typed.delete("1").get());
    assertEquals(2L, typed.delete(Collections.singletonList("1")).get());
    assertEquals(3L, typed.delete(Collections.singletonMap("x", 1)).get());
    assertEquals(1L, typed.update("1", input).get());
    assertEquals(1L, typed.update("1", Collections.singletonMap("name", "B")).get());
    assertEquals(2L, typed.update(Collections.singletonList("1"), Collections.singletonMap("name", "B")).get());
    assertEquals(3L, typed.update(Collections.singletonMap("x", 1), Collections.singletonMap("name", "B")).get());
    assertEquals(10L, typed.count(Collections.emptyMap()).get());
    assertEquals("1", typed.get("1").get().getId());
    assertEquals("1", typed.get("1", Collections.emptyMap()).get().getId());
    assertEquals(1, typed.query(Collections.singletonList("1")).get().size());
    assertEquals(1, typed.query(Collections.emptyMap()).get().size());
    assertEquals(1, typed.query(Collections.singletonList("id"), Collections.emptyMap()).get().size());
    assertEquals(1, typed.query(Collections.singletonList("id"), Collections.emptyMap(), "id DESC").get().size());
    assertEquals(1, typed.query(Collections.singletonList("id"), Collections.emptyMap(), "id DESC", 0, 10).get().size());
    assertTrue(typed.hasPlugin("p"));
    assertNotNull(typed.withPlugins(new PluginDescriptor("p")));
    assertNotNull(typed.withoutPlugins());
    assertNotNull(typed.withoutPlugins(Collections.singletonList("p")));

    when(dataModel.get("2")).thenReturn(Try.success(null));
    when(dataModel.query(Collections.singletonMap("empty", true))).thenReturn(Try.success(null));
    assertNull(typed.get("2").get());
    assertTrue(typed.query(Collections.singletonMap("empty", true)).get().isEmpty());
  }

  @Test
  void baseTypedDataModelFiltersNullRecordsFromQueryResults() {
    var dataModel = mock(DataModel.class);
    var typed = new BaseTypedDataModel<>(dataModel, SampleModel.class);

    var record = Record.of(new HashMap<String, Object>() {{
      put("id", "1");
      put("name", "A");
    }});
    var mixedRecordList = RecordList.of(Arrays.asList(null, record));

    when(dataModel.query(Collections.singletonMap("mixed", true))).thenReturn(Try.success(mixedRecordList));

    var result = typed.query(Collections.singletonMap("mixed", true)).get();

    assertEquals(1, result.size());
    assertEquals("1", result.get(0).getId());
    assertFalse(result.contains(null));
  }

  @Test
  void baseTypedDataModelPropagatesRecordConversionFailures() {
    var dataModel = mock(DataModel.class);
    var typed = new BaseTypedDataModel<>(dataModel, SampleModel.class);

    var badRecord = Record.of(new HashMap<String, Object>() {{
      put("id", "1");
      put("age", Collections.singletonMap("unexpected", true));
    }});

    when(dataModel.query(Collections.singletonMap("bad", true)))
        .thenReturn(Try.success(RecordList.of(Collections.singletonList(badRecord))));

    var result = typed.query(Collections.singletonMap("bad", true));

    assertTrue(result.isFailure());
    assertNotNull(result.getCause());
  }

  @Test
  void deferredProxyHandlesSyncAndAsyncBranches() {
    var registry = new DeferredModelRequestRegistry();
    new DeferredModelRequestRegistryLifecycle(registry).registerAsDefault();
    var dataModel = mock(DataModel.class);

    var deferred = TypedDataModelCenter.getDeferredDataModel(String.class, SampleModel.class);
    assertThrows(IllegalStateException.class, () -> deferred.get("1"));
    assertEquals(1, registry.getPendingRequestCount());

    var asyncGet = deferred.getAsync("1");
    assertFalse(asyncGet.isDone());

    var record = Record.of(new HashMap<String, Object>() {{
      put("id", "1");
      put("name", "N");
    }});
    when(dataModel.get("1")).thenReturn(Try.success(record));
    when(dataModel.hasPlugin("x")).thenThrow(new IllegalArgumentException("bad plugin"));

    registry.completePending(modelName -> Optional.of(dataModel));

    assertEquals("1", asyncGet.join().get().getId());
    var completionException = assertThrows(CompletionException.class, () -> deferred.hasPluginAsync("x").join());
    assertTrue(completionException.getCause() instanceof IllegalArgumentException);
    assertEquals("bad plugin", completionException.getCause().getMessage());
  }

  @Test
  void modelRefreshedListenerCompletesSuccessAndFailure() throws Exception {
    var registry = new DeferredModelRequestRegistry();
    var listener = new ModelRefreshedListener(registry);
    var dataModel = mock(DataModel.class);

    TypedDataModel<String, SampleModel> success = registry.resolveOrDefer(
        "Good",
        String.class,
        SampleModel.class,
        modelName -> Optional.empty()
    );
    TypedDataModel<String, SampleModel> failed = registry.resolveOrDefer(
        "Bad",
        String.class,
        SampleModel.class,
        modelName -> Optional.empty()
    );

    publishDataModels(Map.of("Good", dataModel));
    listener.onModelRefreshed();

    assertSame(dataModel, success.getDataModel());
    var exception = assertThrows(IllegalStateException.class, failed::getDataModel);
    assertTrue(exception.getMessage().contains("Model not found after refresh"));
  }

  @Test
  void springTypedModelMetaProviderRefreshesAndCachesByScanClassSnapshot() {
    DataModelScanRegistry.getDataModelScanAnnotationClasses().clear();
    DataModelScanRegistry.getDataModelScanAnnotationClasses().add(ScanWithExplicitPackage.class);

    var provider = new SpringTypedModelMetaProvider();
    var firstMap = provider.getDataModelMetaMap();
    assertFalse(firstMap.isEmpty());
    assertTrue(provider.getDataModelMeta("TestModel").isPresent());

    var secondMap = provider.getDataModelMetaMap();
    assertSame(firstMap, secondMap);

    DataModelScanRegistry.getDataModelScanAnnotationClasses().add(ScanWithDefaultPackage.class);
    var thirdMap = provider.getDataModelMetaMap();
    assertNotNull(thirdMap);

    DataModelScanRegistry.getDataModelScanAnnotationClasses().clear();
    DataModelScanRegistry.getDataModelScanAnnotationClasses().add(ScanWithEmptyPackage.class);
    assertTrue(provider.getDataModelMetaMap().isEmpty());
    assertTrue(provider.getDataModelMeta("TestModel").isEmpty());
  }

  private static void publishDataModels(Map<String, DataModel> dataModels) throws Exception {
    Field field = DataModelCenter.class.getDeclaredField("dataModelMap");
    field.setAccessible(true);
    if (dataModels == null) {
      field.set(null, null);
      return;
    }
    var snapshot = new TreeMap<String, DataModel>(String::compareToIgnoreCase);
    snapshot.putAll(dataModels);
    field.set(null, snapshot);
  }

  @Test
  void typedAutoConfigurationRegistersScanRegistry() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(TypedDataModelAutoConfiguration.class);
      context.refresh();

      assertEquals(1, context.getBeanNamesForType(DataModelScanRegistry.class).length);
      assertNotNull(context.getBeanProvider(DataModelScanRegistry.class).getIfUnique());
    }
  }

  @Test
  void dataModelScanRegistryDiscoversScanAnnotationsFromBeanDefinitions() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(TypedDataModelAutoConfiguration.class, ScanWithExplicitPackage.class);
      context.refresh();

      assertFalse(DataModelScanRegistry.getDataModelScanAnnotationClasses()
          .contains(TypedDataModelAutoConfiguration.class));
      assertTrue(DataModelScanRegistry.getDataModelScanAnnotationClasses()
          .contains(TypedRuntimeModelScanConfiguration.class));
      assertTrue(DataModelScanRegistry.getDataModelScanAnnotationClasses()
          .contains(ScanWithExplicitPackage.class));
    }
  }

  @Test
  void dataModelScanRegistryReplacesStaleScanAnnotationsWithCurrentContext() {
    DataModelScanRegistry.getDataModelScanAnnotationClasses().add(ScanWithExplicitPackage.class);

    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(TypedDataModelAutoConfiguration.class);
      context.refresh();

      assertFalse(DataModelScanRegistry.getDataModelScanAnnotationClasses()
          .contains(ScanWithExplicitPackage.class));
      assertTrue(DataModelScanRegistry.getDataModelScanAnnotationClasses()
          .contains(TypedRuntimeModelScanConfiguration.class));
    }
  }

  @Test
  void typedAutoConfigurationOwnsCodingSequenceModelScan() {
    DataModelScanRegistry.getDataModelScanAnnotationClasses().clear();
    DataModelScanRegistry.getDataModelScanAnnotationClasses().add(TypedRuntimeModelScanConfiguration.class);

    var provider = new SpringTypedModelMetaProvider();
    var codingSequenceMeta = provider.getDataModelMeta("CodingSequence");

    assertTrue(codingSequenceMeta.isPresent());
    assertEquals("sys_coding_sequence", codingSequenceMeta.get().getRawName());
  }

  @DataModelScan("com.ouroboros.data")
  static class ScanWithExplicitPackage {
  }

  @DataModelScan
  static class ScanWithDefaultPackage {
  }

  @DataModelScan("com.ouroboros.data.typed.runtime.empty")
  static class ScanWithEmptyPackage {
  }

  static class SampleModel {
    private String id;
    private String name;
    private Integer age;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Integer getAge() {
      return age;
    }

    public void setAge(Integer age) {
      this.age = age;
    }
  }
}
