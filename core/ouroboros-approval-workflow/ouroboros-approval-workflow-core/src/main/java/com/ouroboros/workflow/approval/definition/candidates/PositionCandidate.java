package com.ouroboros.workflow.approval.definition.candidates;

import java.util.ArrayList;
import java.util.List;

import com.ouroboros.workflow.approval.definition.Candidate;

public class PositionCandidate extends Candidate {
  List<String> positions = new ArrayList<>();

  public PositionCandidate() {
    super();
  }

  @Override
  public String getType() {
    return "Position";
  }

  public List<String> getPositions() {
    return positions;
  }

  public void setPositions(List<String> positions) {
    this.positions = positions;
  }
}
