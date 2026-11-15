package com.ouroboros.lifecycle.event;

public class OuroborosBootstrapFailedEvent extends OuroborosEvent {
  private final String phase;
  private final String module;
  private final Throwable throwable;
  private final boolean fallback;

  public static OuroborosBootstrapFailedEvent fallback(Object source, String phase, String module, Throwable throwable) {
    return new OuroborosBootstrapFailedEvent(source, phase, module, throwable, true);
  }

  public OuroborosBootstrapFailedEvent(Object source, String phase, String module, Throwable throwable) {
    this(source, phase, module, throwable, false);
  }

  private OuroborosBootstrapFailedEvent(Object source,
                                        String phase,
                                        String module,
                                        Throwable throwable,
                                        boolean fallback) {
    super(source);
    this.phase = phase;
    this.module = module;
    this.throwable = throwable;
    this.fallback = fallback;
  }

  public String getPhase() {
    return phase;
  }

  public String getModule() {
    return module;
  }

  public Throwable getThrowable() {
    return throwable;
  }

  public boolean isFallback() {
    return fallback;
  }
}
