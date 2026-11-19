package com.ouroboros.workflow.approval.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.workflow.approval.FlowFormProvider;
import com.ouroboros.workflow.approval.model.ExpressionVariable;
import com.ouroboros.workflow.approval.model.FlowForm;
import com.ouroboros.workflow.approval.model.FlowFormField;

import io.vavr.Tuple;
import io.vavr.Tuple2;

public final class CustomFlowFormProvider implements FlowFormProvider {
  public static final String TYPE = "custom";
  private final Supplier<List<ApprovalFormDefinition>> descriptions;
  private final ApprovalFormFieldProjector projector;

  public CustomFlowFormProvider(Supplier<List<ApprovalFormDefinition>> descriptions,
      FieldTypeCatalog catalog, FieldTypeCatalogContext context) {
    this.descriptions = descriptions;
    this.projector = new ApprovalFormFieldProjector(catalog, context);
  }

  @Override
  public String getType() { return TYPE; }

  @Override
  public String getTitle() { return "自定义表单"; }

  @Override
  public List<FlowForm> search(String keyword) {
    String query = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
    // Resolve the whole layer before exposing any result, including parameter compilation failures.
    return descriptions.get().stream().map(this::project).toList().stream()
        .filter(form -> form.getName().toLowerCase(Locale.ROOT).contains(query)
            || form.getTitle().toLowerCase(Locale.ROOT).contains(query)).toList();
  }

  @Override
  public Tuple2<Long, List<FlowForm>> search(String keyword, Integer page, Integer pageSize) {
    int number = page == null ? 1 : page;
    int size = pageSize == null ? 20 : pageSize;
    if (number < 1 || size < 1) {
      throw new BusinessException("表单查询页码与每页条数必须为正数");
    }
    var forms = search(keyword);
    return Tuple.of((long) forms.size(), forms.stream().skip((long) (number - 1) * size).limit(size).toList());
  }

  @Override
  public Optional<FlowForm> get(String key) {
    return search(null).stream().filter(form -> form.getName().equals(key)).findFirst();
  }

  private FlowForm project(ApprovalFormDefinition definition) {
    FlowForm form = new FlowForm();
    form.setName(definition.name());
    form.setTitle(definition.title());
    List<FlowFormField> fields = new ArrayList<>();
    List<ExpressionVariable> conditions = new ArrayList<>();
    for (var field : definition.fields()) {
      try {
        var compiled = projector.compile(field.type(), field.params());
        var condition = projector.condition(field.name(), field.label(), field.type(), compiled);
        FlowFormField projected = new FlowFormField();
        projected.setName(field.name());
        projected.setLabel(field.label());
        projected.setType(condition.map(ExpressionVariable::getType).orElse(field.type()));
        projected.setExtraProp("description", field.description());
        projected.setExtraProp("fieldType", field.type());
        projected.setExtraProp("params", field.params());
        fields.add(projected);
        condition.ifPresent(conditions::add);
      } catch (BusinessException e) {
        throw new BusinessException("自定义表单 " + definition.name() + " 字段 " + field.name() + ": " + e.getMessage(), e);
      }
    }
    form.setFields(fields);
    form.setExpressionVariables(conditions);
    return form;
  }
}
