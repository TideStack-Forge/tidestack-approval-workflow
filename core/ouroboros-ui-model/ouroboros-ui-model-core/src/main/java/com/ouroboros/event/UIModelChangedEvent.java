package com.ouroboros.event;

import java.util.EventObject;

public class UIModelChangedEvent extends EventObject {
  /**
   * Constructs a prototypical Event.
   *
   * @param source the object on which the Event initially occurred
   * @throws IllegalArgumentException if source is null
   */
  public UIModelChangedEvent(Object source) {
    super(source);
  }
}
