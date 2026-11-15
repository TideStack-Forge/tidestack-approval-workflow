package com.ouroboros.data.typed.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

import com.ouroboros.data.TypedDataModelAutoConfiguration;
import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.data.model.*;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.pkgenerator.DBCodingSequencer;
import com.ouroboros.data.pkgenerator.model.CodingSequence;

class TypedDataModelSpringInjectionTest {

  @AfterEach
  void cleanupRegistry() throws Exception {
    DataModelScanRegistry.getDataModelScanAnnotationClasses().clear();
    TypedDataModelCenter.clearDeferredModelRequestRegistry();
    publishDataModels(null);
  }

  @Test
  void dataModelScanRegistersTypedModelBeansAndSharesRequestRegistry() throws Exception {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(TypedDataModelAutoConfiguration.class, ScanOnlyConfig.class, ConsumerConfig.class);
      context.refresh();

      assertEquals(1, context.getBeanNamesForType(DeferredModelRequestRegistry.class).length);
      assertEquals(1, context.getBeanNamesForType(typedModelType(String.class, AutowiredStringModel.class)).length);
      assertEquals(1, context.getBeanNamesForType(typedModelType(Long.class, AutowiredLongModel.class)).length);
      assertEquals(1, context.getBeanNamesForType(typedModelType(String.class, CodingSequence.class)).length);
      assertEquals(0, context.getBeanNamesForType(typedModelType(String.class, NoPrimaryKeyModel.class)).length);
      assertEquals(0, context.getBeanNamesForType(typedModelType(String.class, CompositePrimaryKeyModel.class)).length);

      var consumer = context.getBean(InjectedModels.class);
      assertNotNull(consumer.stringModel());
      assertNotNull(consumer.longModel());
      assertSame(
          context.getBeanProvider(typedModelType(String.class, CodingSequence.class)).getObject(),
          context.getBean(DBCodingSequencer.class).getDataModel()
      );

      var registry = context.getBean(DeferredModelRequestRegistry.class);
      assertTrue(registry.getPendingRequestCount() >= 2);

      publishDataModels(Collections.emptyMap());
      context.getBean(ModelRefreshedListener.class).onModelRefreshed();

      assertEquals(0, registry.getPendingRequestCount());
      var exception = assertThrows(IllegalStateException.class, () -> consumer.stringModel().getDataModel());
      assertTrue(exception.getMessage().contains("AutowiredStringModel"));
    }
  }

  @Test
  void dataModelScanOnlyContextDoesNotImportRuntimeRegistry() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(ScanOnlyConfig.class);

      assertDoesNotThrow(context::refresh);
      assertEquals(0, context.getBeanNamesForType(DeferredModelRequestRegistry.class).length);
      assertEquals(0, context.getBeanNamesForType(typedModelType(String.class, AutowiredStringModel.class)).length);
    }
  }

  @Test
  void autowiredFieldInjectionSucceedsBeforeRefreshThenCompletesToReadyProxy() throws Exception {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(TypedDataModelAutoConfiguration.class, ScanOnlyConfig.class, AutowiredFieldConsumer.class);
      context.refresh();

      var consumer = context.getBean(AutowiredFieldConsumer.class);
      assertNotNull(consumer.stringModel());

      var notReady = assertThrows(IllegalStateException.class, () -> consumer.stringModel().getDataModel());
      assertTrue(notReady.getMessage().contains("Model is not ready"));

      var dataModel = mock(DataModel.class);
      publishDataModels(Map.of("AutowiredStringModel", dataModel));
      context.getBean(ModelRefreshedListener.class).onModelRefreshed();

      assertSame(dataModel, consumer.stringModel().getDataModel());
      assertEquals(0, context.getBean(DeferredModelRequestRegistry.class).getPendingRequestCount());
    }
  }

  @Test
  void autowiredFieldInjectionSucceedsBeforeRefreshThenCompletesToFailedProxyWhenModelMissing() throws Exception {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(TypedDataModelAutoConfiguration.class, ScanOnlyConfig.class, AutowiredFieldConsumer.class);
      context.refresh();

      var consumer = context.getBean(AutowiredFieldConsumer.class);
      assertNotNull(consumer.stringModel());

      var notReady = assertThrows(IllegalStateException.class, () -> consumer.stringModel().getDataModel());
      assertTrue(notReady.getMessage().contains("Model is not ready"));

      publishDataModels(Collections.emptyMap());
      context.getBean(ModelRefreshedListener.class).onModelRefreshed();

      var missing = assertThrows(IllegalStateException.class, () -> consumer.stringModel().getDataModel());
      assertTrue(missing.getMessage().contains("AutowiredStringModel"));
      assertEquals(0, context.getBean(DeferredModelRequestRegistry.class).getPendingRequestCount());
    }
  }

  @Test
  void autowiredFieldInjectionFailsWhenModelDoesNotHaveRegisteredTypedBean() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(TypedDataModelAutoConfiguration.class, NoPrimaryKeyAutowiredConsumer.class);

      assertThrows(UnsatisfiedDependencyException.class, context::refresh);
    }
  }

  @Test
  void autowiredFieldInjectionFailsWithoutDeferredModelRequestRegistryBean() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(ScanOnlyConfig.class, AutowiredFieldConsumer.class);

      assertThrows(UnsatisfiedDependencyException.class, context::refresh);
    }
  }

  @Test
  void requestRegistryResolvesLatePresentAndLateMissingWithoutPermanentPending() {
    var registry = new DeferredModelRequestRegistry();
    var dataModel = mock(DataModel.class);

    var immediate = registry.resolveOrDefer(
        "AutowiredStringModel",
        String.class,
        AutowiredStringModel.class,
        name -> Optional.of(dataModel)
    );

    assertSame(dataModel, immediate.getDataModel());
    assertEquals(0, registry.getPendingRequestCount());

    registry.completePending(name -> Optional.empty());
    var missing = registry.resolveOrDefer(
        "MissingModel",
        String.class,
        AutowiredStringModel.class,
        name -> Optional.empty()
    );

    var exception = assertThrows(IllegalStateException.class, missing::getDataModel);
    assertTrue(exception.getMessage().contains("MissingModel"));
    assertEquals(0, registry.getPendingRequestCount());
  }

  @Test
  void factoryCreatesDeferredProxyWithoutTypedDataModelCenterStaticBridge() {
    TypedDataModelCenter.clearDeferredModelRequestRegistry();
    var registry = new DeferredModelRequestRegistry();
    var factory = new TypedDataModelFactoryBean<>(
        registry,
        "AutowiredStringModel",
        String.class,
        AutowiredStringModel.class
    );

    var typedDataModel = assertDoesNotThrow(factory::getObject);

    assertNotNull(typedDataModel);
    assertEquals(1, registry.getPendingRequestCount());
  }

  @Test
  void concurrentResolveAndCompleteDoesNotLeavePermanentPendingRequest() throws Exception {
    var registry = new DeferredModelRequestRegistry();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var typedModelFuture = executor.submit(() -> {
        ready.countDown();
        start.await();
        return registry.resolveOrDefer(
            "MissingModel",
            String.class,
            AutowiredStringModel.class,
            name -> Optional.empty()
        );
      });
      var completionFuture = executor.submit(() -> {
        ready.countDown();
        start.await();
        registry.completePending(name -> Optional.empty());
        return null;
      });

      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      TypedDataModel<String, AutowiredStringModel> typedModel = typedModelFuture.get();
      completionFuture.get();

      assertEquals(0, registry.getPendingRequestCount());
      var exception = assertThrows(IllegalStateException.class, typedModel::getDataModel);
      assertTrue(exception.getMessage().contains("MissingModel"));
    }
  }

  private static void publishDataModels(Map<String, DataModel> dataModels) throws Exception {
    java.lang.reflect.Field field = DataModelCenter.class.getDeclaredField("dataModelMap");
    field.setAccessible(true);
    if (dataModels == null) {
      field.set(null, null);
      return;
    }
    var snapshot = new TreeMap<String, DataModel>(String::compareToIgnoreCase);
    snapshot.putAll(dataModels);
    field.set(null, snapshot);
  }

  private ResolvableType typedModelType(Class<?> pkClass, Class<?> modelClass) {
    return ResolvableType.forClassWithGenerics(TypedDataModel.class, pkClass, modelClass);
  }

  @Configuration(proxyBeanMethods = false)
  static class ConsumerConfig {
    @Bean
    InjectedModels injectedModels(
        TypedDataModel<String, AutowiredStringModel> stringModel,
        TypedDataModel<Long, AutowiredLongModel> longModel
    ) {
      return new InjectedModels(stringModel, longModel);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @DataModelScan("com.ouroboros.data.typed.runtime")
  static class ScanOnlyConfig {
  }

  static class AutowiredFieldConsumer {
    @Autowired
    private TypedDataModel<String, AutowiredStringModel> stringModel;

    TypedDataModel<String, AutowiredStringModel> stringModel() {
      return stringModel;
    }
  }

  static class NoPrimaryKeyAutowiredConsumer {
    @Autowired
    private TypedDataModel<String, NoPrimaryKeyModel> noPrimaryKeyModel;

    TypedDataModel<String, NoPrimaryKeyModel> noPrimaryKeyModel() {
      return noPrimaryKeyModel;
    }
  }

  record InjectedModels(
      TypedDataModel<String, AutowiredStringModel> stringModel,
      TypedDataModel<Long, AutowiredLongModel> longModel
  ) {
  }
}

@Model
class AutowiredStringModel {
  @Field
  @PrimaryKey
  private String id;
}

@Model
class AutowiredLongModel {
  @Field
  @PrimaryKey
  private Long id;
}

@Model
class NoPrimaryKeyModel {
  @Field
  private String id;
}

@Model
class CompositePrimaryKeyModel {
  @Field
  @PrimaryKey
  private String tenantId;
  @Field
  @PrimaryKey
  private String id;
}
