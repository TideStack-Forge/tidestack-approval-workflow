package com.ouroboros.workflow.approval.flowable.action.vo;

import java.util.List;

public class FreeApprovalTaskOptionsVO {
  List<String> consultUserIds;

  public List<String> getConsultUserIds() {
    return consultUserIds;
  }

  public void setConsultUserIds(List<String> consultUserIds) {
    this.consultUserIds = consultUserIds;
  }
}
