package com.ouroboros.workflow.approval.form;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.fieldtype.DefaultFieldTypeCatalog;
import com.ouroboros.fieldtype.DefaultFieldTypeCompiler;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.JarFieldTypeDefinitionProvider;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.util.Json;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionDocument;

class CustomFlowFormProviderTest {
  private final FieldTypeCatalog types = new DefaultFieldTypeCatalog(
      List.of(new JarFieldTypeDefinitionProvider(new FieldTypeDefinitionReader())), new DefaultFieldTypeCompiler());

  @Test
  void readsTwoFormsWithOneProviderAndProjectsTheRealFieldTypeContract() {
    var provider = provider(new AtomicReference<>(List.of(description("purchase"), description("expense"))));
    assertEquals(2, provider.search(null).size());
    assertEquals("自定义表单", provider.getTitle());
    assertEquals(1, provider.search("purchase", 1, 1)._1);
    assertEquals(1, provider.search(null, 2, 1)._2.size());
    assertTrue(provider.search(null, Integer.MAX_VALUE, Integer.MAX_VALUE)._2.isEmpty());
    assertThrows(BusinessException.class, () -> provider.search(null, 0, 1));
    assertTrue(provider.get("missing").isEmpty());
    var form = provider.get("purchase").orElseThrow();
    var source = new FlowFormSourceAssembler().build("custom", "purchase", form);
    var fields = (List<?>) source.get("fields");
    var amount = (Map<?, ?>) fields.get(0);
    assertEquals("Decimal", amount.get("valueType"));
    assertEquals(List.of("permission", "condition"), amount.get("usages"));
    assertEquals("采购金额", amount.get("label"));
    assertEquals("金额说明", amount.get("description"));
    var options = (List<?>) ((Map<?, ?>) fields.get(1)).get("options");
    assertEquals("equipment", ((Map<?, ?>) options.get(0)).get("value"));
  }

  @Test
  void refreshesSameSizeLabelAndOptionChangesWithoutStaleSnapshots() {
    var descriptions = new AtomicReference<>(List.of(description("purchase")));
    var provider = provider(descriptions);
    var assembler = new FlowFormSourceAssembler();
    var before = assembler.build("custom", "purchase", provider.get("purchase").orElseThrow());
    descriptions.set(List.of(ApprovalFormDefinition.read(json("purchase").replace("采购金额", "含税金额").replace("equipment", "service"))));
    var after = assembler.build("custom", "purchase", provider.get("purchase").orElseThrow());
    assertNotEquals(before.get("revisionToken"), after.get("revisionToken"));
    assertEquals("含税金额", provider.get("purchase").orElseThrow().getFields().get(0).getLabel());
  }

  @Test
  void keepsAnEmptyDirectoryDistinctFromInvalidResourcesAndCompilesBeforeReturningResults() {
    var values = new AtomicReference<>(List.of(ApprovalFormDefinition.read("""
        {"formatVersion":1,"name":"empty","title":"空表单","fields":[]}
        """)));
    var provider = provider(values);
    assertTrue(provider.get("empty").orElseThrow().getFields().isEmpty());
    assertTrue(provider.get("empty").orElseThrow().getExpressionVariables().isEmpty());
    values.set(List.of(ApprovalFormDefinition.read(json("broken").replace("定点数字", "不存在的类型")), description("purchase")));
    assertThrows(BusinessException.class, () -> provider.get("purchase"));
  }

  @Test
  void rejectsInvalidDescriptionStructureAndUnsafeIdentities() {
    for (String invalid : List.of(json("purchase").replace("\"formatVersion\":1", "\"formatVersion\":2"),
        json("../purchase"), json("bad name"), json("bad\\name"),
        json("purchase").replace("\"fields\":[", "\"unknown\":true,\"fields\":["),
        json("purchase").replace("\"amount\"", "\"__amount\""),
        json("purchase").replace("\"purchaseType\"", "\"amount\""),
        json("purchase").replace("\"params\":{\"size\":12,\"precision\":2}", "\"params\":null"),
        "[]", "{", "{}")) {
      assertThrows(BusinessException.class, () -> ApprovalFormDefinition.read(invalid), invalid);
    }
  }

  @Test
  void usesTypedCatalogForWholeResourceApplicationOverrideAndDuplicateErrors() {
    MetadataSource jars = spec -> List.of(new MetadataResource("jar/approval-form/purchase.approval-form.json", json("purchase")));
    MetadataSource app = spec -> List.of(new MetadataResource("approval-form/purchase.approval-form.json", json("purchase").replace("采购申请", "应用采购")));
    assertEquals("应用采购", ApprovalFormMetadataCatalog.load(jars, app).get(0).title());
    assertThrows(RuntimeException.class, () -> ApprovalFormMetadataCatalog.load(jars, spec -> List.of(
        new MetadataResource("purchase.approval-form.json", json("purchase")),
        new MetadataResource("duplicate/purchase.approval-form.json", json("purchase")))));
    assertThrows(RuntimeException.class, () -> ApprovalFormMetadataCatalog.load(jars,
        spec -> List.of(new MetadataResource("wrong.approval-form.json", json("purchase")))));
    assertEquals(1, ApprovalFormMetadataCatalog.load(jars, spec -> List.of()).size());
  }

  @Test
  void validatesDeploymentReferencesWhilePreservingEditableDraftsAndOldSourceTypes() {
    var provider = provider(new AtomicReference<>(List.of(description("purchase"))));
    String definition = """
        {"formatVersion":1,"name":"purchase","title":"采购审批","version":1,"enabled":true,
         "form":{"formType":"custom","formId":"purchase"},
         "nodes":[{"id":"s","type":"StartNode","name":"开始","permission":[{"name":"amount","visible":true}]},
                  {"id":"e","type":"EndNode","name":"结束"}],
         "edges":[{"id":"edge","type":"SEQUENCE","sourceId":"s","targetId":"e"}]}
        """;
    var document = ApprovalWorkflowDefinitionDocument.decode(definition).get();
    CustomFormBindingValidator.validate(document.metadata(), List.of(provider));
    var draft = ApprovalWorkflowDefinitionDocument.decode(definition.replace("\"name\":\"amount\"", "\"name\":\"removed\"")).get();
    assertTrue(draft.toStaticJson().get().contains("removed"));
    assertThrows(BusinessException.class, () -> CustomFormBindingValidator.validate(draft.metadata(), List.of(provider)));
    document.metadata().getForm().setFormId("missing");
    assertThrows(BusinessException.class, () -> CustomFormBindingValidator.validate(document.metadata(), List.of(provider)));
    for (String type : List.of("default", "business-model", "third-party")) {
      document.metadata().getForm().setFormType(type);
      CustomFormBindingValidator.validate(document.metadata(), List.of());
    }
  }

  @Test
  void deployValidatesConditionsAgainstTheCompiledFieldAndActualOptionValues() {
    var provider = provider(new AtomicReference<>(List.of(description("purchase"))));
    var metadata = ApprovalWorkflowDefinitionDocument.decode("""
        {"formatVersion":1,"name":"purchase","title":"采购审批","version":1,"enabled":true,
         "form":{"formType":"custom","formId":"purchase"},
         "nodes":[{"id":"s","type":"StartNode","name":"开始"},{"id":"e","type":"EndNode","name":"结束"}],
         "edges":[{"id":"edge","type":"SEQUENCE","sourceId":"s","targetId":"e"}]}
        """).get().metadata();
    var condition = new com.ouroboros.workflow.approval.definition.condition.CompareCondition()
        .setLeft(new com.ouroboros.workflow.approval.definition.condition.CompareCondition.Left().setType("field").setField("amount"))
        .setOp("greater").setRight(50000);
    metadata.getEdges().get(0).setCondition(condition);
    CustomFormBindingValidator.validate(metadata, List.of(provider));
    condition.setRight("50000");
    assertThrows(BusinessException.class, () -> CustomFormBindingValidator.validate(metadata, List.of(provider)));
    condition.setRight(List.of(5, 10));
    assertThrows(BusinessException.class, () -> CustomFormBindingValidator.validate(metadata, List.of(provider)));
    condition.setOp("between");
    CustomFormBindingValidator.validate(metadata, List.of(provider));
    condition.setOp("is_empty").setRight(null);
    CustomFormBindingValidator.validate(metadata, List.of(provider));
    condition.setLeft(new com.ouroboros.workflow.approval.definition.condition.CompareCondition.Left().setType("field").setField("purchaseType"))
        .setOp("select_equals").setRight("equipment");
    CustomFormBindingValidator.validate(metadata, List.of(provider));
    condition.setRight("设备");
    assertThrows(BusinessException.class, () -> CustomFormBindingValidator.validate(metadata, List.of(provider)));
    condition.setRight("equipment").setOp("greater");
    assertThrows(BusinessException.class, () -> CustomFormBindingValidator.validate(metadata, List.of(provider)));
    condition.setLeft(new com.ouroboros.workflow.approval.definition.condition.CompareCondition.Left().setType("field").setField("removed"));
    assertThrows(BusinessException.class, () -> CustomFormBindingValidator.validate(metadata, List.of(provider)));
    assertThrows(BusinessException.class, () -> CustomFormBindingValidator.validate(metadata, List.of()));
  }

  @Test
  void resolvesPackagedJarAndFileSystemThroughTheOfficialSources(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    var jar = directory.resolve("forms.jar");
    try (var archive = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(jar))) {
      for (String path : List.of("META-INF/", "META-INF/ouroboros/", "META-INF/ouroboros/metadata/", "META-INF/ouroboros/metadata/approval-form/")) {
        archive.putNextEntry(new java.util.jar.JarEntry(path));
        archive.closeEntry();
      }
      archive.putNextEntry(new java.util.jar.JarEntry("META-INF/ouroboros/metadata/approval-form/purchase.approval-form.json"));
      archive.write(json("purchase").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      archive.closeEntry();
    }
    var application = java.nio.file.Files.createDirectory(directory.resolve("approval-form"));
    java.nio.file.Files.writeString(application.resolve("purchase.approval-form.json"), json("purchase").replace("采购申请", "目录覆盖"));
    try (var loader = new java.net.URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
      var packaged = new com.ouroboros.metadata.resource.ClasspathMetadataSource(loader);
      assertEquals("采购申请", ApprovalFormMetadataCatalog.load(packaged, spec -> List.of()).get(0).title());
      assertEquals("目录覆盖", ApprovalFormMetadataCatalog.load(packaged,
          new com.ouroboros.metadata.resource.FileSystemMetadataSource(application)).get(0).title());
    }
  }

  @Test
  void publishedSkillExamplesUseTheFormalDescriptionAndWorkflowContracts() throws Exception {
    var root = java.nio.file.Path.of("").toAbsolutePath();
    while (root != null && !java.nio.file.Files.isDirectory(root.resolve("skills/tidestack-approval-integration/examples"))) {
      root = root.getParent();
    }
    assertNotNull(root, "public Skill examples must be available in the repository checkout");
    var examples = root.resolve("skills/tidestack-approval-integration/examples");
    var description = ApprovalFormDefinition.read(java.nio.file.Files.readString(examples.resolve("purchase.approval-form.json")));
    var provider = provider(new AtomicReference<>(List.of(description)));
    var document = ApprovalWorkflowDefinitionDocument.decode(java.nio.file.Files.readString(examples.resolve("purchase.workflow.json"))).get();
    CustomFormBindingValidator.validate(document.metadata(), List.of(provider));
    assertEquals("purchase", document.metadata().getForm().getFormId());
    assertTrue(document.toRuntimeJson().isSuccess());
    assertFalse(document.metadata().getNodes().get(1).getCandidates().isEmpty());
  }

  @Test
  void twoWorkflowsShareOneDescriptionWithoutSharingNodePermissions() {
    var provider = provider(new AtomicReference<>(List.of(description("purchase"), description("expense"))));
    String json = """
        {"formatVersion":1,"name":"first","title":"采购审批","version":1,"enabled":true,
         "form":{"formType":"custom","formId":"purchase"},
         "nodes":[{"id":"s","type":"StartNode","name":"开始","permission":[
           {"name":"amount","visible":true,"writable":false,"required":false}]},
           {"id":"e","type":"EndNode","name":"结束"}],
         "edges":[{"id":"edge","type":"SEQUENCE","sourceId":"s","targetId":"e"}]}
        """;
    var first = ApprovalWorkflowDefinitionDocument.decode(json).get();
    var second = ApprovalWorkflowDefinitionDocument.decode(json.replace("\"first\"", "\"second\"")).get();
    second.metadata().getNodes().get(0).getPermission().get(0).setWritable(true);

    for (var document : List.of(first, second)) {
      CustomFormBindingValidator.validate(document.metadata(), List.of(provider));
      assertEquals("purchase", document.metadata().getForm().getFormId());
      assertTrue(document.toRuntimeJson().isSuccess());
    }
    assertFalse(first.metadata().getNodes().get(0).getPermission().get(0).isWritable());
    assertTrue(second.metadata().getNodes().get(0).getPermission().get(0).isWritable());
    assertEquals(2, provider.search(null).size());
  }

  private CustomFlowFormProvider provider(AtomicReference<List<ApprovalFormDefinition>> descriptions) {
    return new CustomFlowFormProvider(descriptions::get, types, FieldTypeCatalogContext.runtime());
  }

  private ApprovalFormDefinition description(String name) { return ApprovalFormDefinition.read(json(name)); }

  private String json(String name) {
    return """
        {"formatVersion":1,"name":"%s","title":"采购申请","fields":[
        {"name":"amount","label":"采购金额","description":"金额说明","type":"定点数字","params":{"size":12,"precision":2}},
        {"name":"purchaseType","label":"采购类型","type":"单选","params":{"dataType":"String","size":100,"options":[{"value":"equipment","label":"设备"}]}}]}
        """.formatted(name);
  }
}
