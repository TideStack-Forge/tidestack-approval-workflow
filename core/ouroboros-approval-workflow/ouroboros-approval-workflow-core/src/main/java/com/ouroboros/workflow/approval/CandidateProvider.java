package com.ouroboros.workflow.approval;

import java.beans.Transient;
import java.util.Collections;
import java.util.List;

import com.ouroboros.uischema.schema.SchemaNode;

// TODO: 考虑是否要加入流程类型等条件，以实现更复杂的共存关系？
public interface CandidateProvider {
  String getName();

  String getLabel();

  default SchemaNode getSchema() {
    return null;
  }

  ConfigMode getConfigMode();

  default List<String> getDisabledIn() {
    return Collections.emptyList();
  }

  String getValueKey();

  default String getCategoryLabel() {
    return null;
  }

  default Integer getMax() {
    return 1;
  }

  default Integer getMin() {
    return 1;
  }


  @Transient
  List<CandidateItem> getItems();

  @Transient
  List<CandidateItem> search(String category, String keyword);

  @Transient
  List<CandidateItem> search(List<String> valueList);

  @Transient
  default List<CandidateCategory> getCategories() {
    return Collections.emptyList();
  }

  enum ConfigMode {
    // 选中
    Check,
    // 枚举
    Enum,
    // 单选
    SingleSelect,
    // 多选
    MultiSelect,
    // 拾取器
    Pick,
  }

  class CandidateItem {
    Object value;
    String label;

    public Object getValue() {
      return value;
    }

    public void setValue(Object value) {
      this.value = value;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }
  }

  class CandidateCategory {
    Object value;
    String label;
    List<CandidateCategory> children;

    public Object getValue() {
      return value;
    }

    public void setValue(Object value) {
      this.value = value;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public List<CandidateCategory> getChildren() {
      return children;
    }

    public void setChildren(List<CandidateCategory> children) {
      this.children = children;
    }
  }
}
