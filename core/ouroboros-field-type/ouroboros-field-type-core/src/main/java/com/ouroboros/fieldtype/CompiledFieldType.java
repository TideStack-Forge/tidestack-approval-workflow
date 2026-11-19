package com.ouroboros.fieldtype;

import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.uischema.schema.FormItemNode;

public interface CompiledFieldType {
  DataModelFieldMeta toDataFieldMeta();

  FormItemNode toInputFormItem();

  FormItemNode toDisplayFormItem();

  String getPrimaryKeyGenerator();
}
