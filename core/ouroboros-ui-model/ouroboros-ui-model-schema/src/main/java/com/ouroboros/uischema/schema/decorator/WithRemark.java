package com.ouroboros.uischema.schema.decorator;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.RemarkNode;
import com.ouroboros.uischema.schema.Schema;
import com.ouroboros.uischema.schema.SchemaNode;

public interface WithRemark extends Schema {
  default RemarkNode getRemark() {
    return getProp(RemarkNode.class, "remark", null);
  }

  default void setRemark(RemarkNode remark) {
    setProp("remark", remark);
  }

  @JsonSetter
  default void setRemark(SchemaNode remark) {
    setProp("remark", new RemarkNode(remark));
  }
}
