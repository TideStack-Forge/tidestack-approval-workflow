package com.ouroboros.workflow.approval;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.definition.NodeFormFieldPermission;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.definition.nodes.StartNode;

import io.vavr.control.Try;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlowStartContractTest {
  @Test
  void commandUsesAnExplicitKeyThenAVariableKeyAndOtherwiseAllocatesARequestKey() {
    assertEquals("explicit", command("explicit", Map.of("idempotencyKey", "variable")).idempotencyKey());
    assertEquals("variable", command(null, Map.of("idempotencyKey", "variable")).idempotencyKey());
    assertNotEquals(command(null, null).idempotencyKey(), command(null, null).idempotencyKey());
  }

  @Test
  void customVariablesPreserveBusinessValuesAndLeaveThePlatformStartContextIntact() {
    Map<String, Object> context = new java.util.LinkedHashMap<>(Map.of(
        "callbackFlow", "sales.Order.callback", "businessRecordType", "sales.Order", "businessRecordId", "42",
        "approvalCaseId", "case-1", "submissionRound", 2, "idempotencyKey", "request-key", "region", "east"));
    context.put("optional", null);
    context.put("approvalUrl", "/sales/order/42/approval");
    context.put("editUrl", "/sales/order/edit?id=42");
    Map<String, Object> expected = new java.util.LinkedHashMap<>();
    expected.put("region", "east"); expected.put("optional", null);

    assertEquals(expected, FlowInstanceVariables.customVariables(context));
    assertEquals(10, context.size());
    assertEquals("sales.Order.callback", context.get("callbackFlow"));
    assertTrue(FlowInstanceVariables.customVariables(null).isEmpty());
  }

  @ParameterizedTest
  @EnumSource(FlowStartResult.Status.class)
  void compatibilityReferencePreservesPendingAndUnknownButRejectsFailedFacts(FlowStartResult.Status status) {
    var result = new FlowStartResult("execution", status, null, null, "fact", false);
    if (status == FlowStartResult.Status.FAILED) {
      assertThrows(BusinessException.class, result::acceptedExecutionId);
    } else {
      assertEquals("execution", result.acceptedExecutionId());
    }
  }

  @Test
  void existingBuiltInImplementationAcceptsTheCommonCommandAndPreservesCc() {
    var service = mock(FlowInstanceService.class, CALLS_REAL_METHODS);
    var cc = Map.<String, Object>of("userIds", List.of("copy"));
    doReturn(Try.success("instance")).when(service).start(eq("flow"), eq("user"), any(), any(), any(), eq(cc));
    var result = service.start(new FlowStartCommand("flow", "user", Map.of(), Map.of(), Map.of(), cc, "key"));
    assertEquals(FlowStartResult.started("instance"), result.get());
    doReturn(Try.success("plain")).when(service).start(eq("flow"), eq("user"), any(), any(), any(), any(CompletableFuture.class));
    assertEquals("plain", service.start(command("key", Map.of())).get().executionId());
  }

  @Test
  void initialFormRetainsStartPermissionsAndReportsMissingBindingsAndServices() {
    var service = mock(FlowFormService.class);
    when(service.getType()).thenReturn("default");
    var schema = new SchemaNode();
    var permission = new NodeFormFieldPermission();
    permission.setName("amount"); permission.setWritable(true);
    var start = new StartNode(); start.setId("start"); start.setPermission(List.of(permission));
    var definition = new StructuredFlowDefinition();
    definition.setNodes(List.of(start)); definition.setFormKey("order");
    when(service.getInitialFormSchema("order", List.of(permission))).thenReturn(Try.success(schema));
    var resolver = new FlowInitialFormResolver(List.of(service));
    assertSame(schema, resolver.resolve(definition).get());
    definition.setFormType("businessModel");
    assertTrue(resolver.resolve(definition).isFailure());
    when(service.getType()).thenReturn("businessModel");
    when(service.getInitialFormSchema("order", List.of())).thenReturn(Try.success(schema));
    assertSame(schema, resolver.resolve(definition).get());
    definition.setFormKey("");
    assertTrue(resolver.resolve(definition).isFailure());
  }

  private FlowStartCommand command(String key, Map<String, Object> variables) {
    return new FlowStartCommand("flow", "user", Map.of(), variables, Map.of(), null, key);
  }
}
