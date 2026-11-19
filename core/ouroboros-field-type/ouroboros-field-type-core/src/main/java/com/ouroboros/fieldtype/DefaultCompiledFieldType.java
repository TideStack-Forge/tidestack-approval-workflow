package com.ouroboros.fieldtype;

import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.uischema.schema.FormItemNode;

public class DefaultCompiledFieldType implements CompiledFieldType {
  private final DataModelFieldMeta dataFieldMeta;
  private final FormItemNode inputFormItem;
  private final FormItemNode displayFormItem;
  private final String primaryKeyGenerator;

  public DefaultCompiledFieldType(DataModelFieldMeta dataFieldMeta,
                                  FormItemNode inputFormItem,
                                  FormItemNode displayFormItem,
                                  String primaryKeyGenerator) {
    this.dataFieldMeta = dataFieldMeta;
    this.inputFormItem = inputFormItem;
    this.displayFormItem = displayFormItem;
    this.primaryKeyGenerator = primaryKeyGenerator;
  }

  @Override
  public DataModelFieldMeta toDataFieldMeta() {
    return dataFieldMeta;
  }

  @Override
  public FormItemNode toInputFormItem() {
    return inputFormItem;
  }

  @Override
  public FormItemNode toDisplayFormItem() {
    return displayFormItem;
  }

  @Override
  public String getPrimaryKeyGenerator() {
    return primaryKeyGenerator;
  }
}
