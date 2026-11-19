package com.ouroboros.workflow.approval;

import com.ouroboros.exception.BusinessException;

/** A design-time form source does not necessarily provide a runtime business form. */
public final class FlowFormServiceUnavailableException extends BusinessException {
  public FlowFormServiceUnavailableException(String formType) {
    super("当前表单未接入通用审批页面，请通过业务页面操作（" + formType + "）");
  }
}
