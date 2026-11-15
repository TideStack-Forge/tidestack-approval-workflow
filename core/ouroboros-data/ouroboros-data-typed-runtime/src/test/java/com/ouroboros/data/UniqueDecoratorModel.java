package com.ouroboros.data;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.Unique;
import com.ouroboros.data.annotation.UniqueConstraint;
import com.ouroboros.data.model.UniquenessScope;

@Model(fullName = "UniqueDecoratorModel")
@UniqueConstraint(
    name = "project_code",
    fields = {"projectId", "code"},
    scope = UniquenessScope.ACTIVE_RECORDS
)
@UniqueConstraint(name = "tenant_code", fields = {"tenantId", "code"})
public class UniqueDecoratorModel {
  @Field
  private Long projectId;

  @Field
  private Long tenantId;

  @Field
  @Unique(scope = UniquenessScope.ALL_RECORDS)
  private String code;

  @Field
  @Unique
  private String name;
}
