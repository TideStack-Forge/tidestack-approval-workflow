package com.ouroboros.workflow.approval;

import java.util.List;
import java.util.Optional;

import io.vavr.Tuple2;

import com.ouroboros.workflow.approval.model.FlowForm;

public interface FlowFormProvider {
  String getType();

  default String getTitle() { return getType(); }

  List<FlowForm> search(String keyword);

  Tuple2<Long, List<FlowForm>> search(String keyword, Integer page, Integer pageSize);

  Optional<FlowForm> get(String key);
}
