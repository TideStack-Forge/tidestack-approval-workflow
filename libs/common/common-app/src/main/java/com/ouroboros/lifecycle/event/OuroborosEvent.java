package com.ouroboros.lifecycle.event;

import java.util.EventObject;

public abstract class OuroborosEvent extends EventObject {
  public OuroborosEvent(Object source) {
    super(source);
  }
}
