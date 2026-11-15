package com.ouroboros.lifecycle.event;

public class OuroborosBootstrapProgressEvent extends OuroborosEvent {
  private final String phase;
  private final String module;
  private final String message;

  public static OuroborosBootstrapProgressEvent phaseSwitch(Object source, String phase, String message) {
    return new OuroborosBootstrapProgressEvent(source, phase, null, message);
  }

  public static OuroborosBootstrapProgressEvent moduleProgress(Object source, String module, String message) {
    return new OuroborosBootstrapProgressEvent(source, null, module, message);
  }

  public OuroborosBootstrapProgressEvent(Object source, String phase, String module, String message) {
    super(source);
    this.phase = phase;
    this.module = module;
    this.message = message;
  }

  public String getPhase() {
    return phase;
  }

  public String getModule() {
    return module;
  }

  public String getMessage() {
    return message;
  }
}
