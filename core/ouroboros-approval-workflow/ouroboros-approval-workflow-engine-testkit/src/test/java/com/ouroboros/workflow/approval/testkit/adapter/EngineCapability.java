package com.ouroboros.workflow.approval.testkit.adapter;

public interface EngineCapability {
  enum CapabilityLevel {
    STABLE,
    COMPATIBLE,
    DEFERRED;

    public boolean isSupported() {
      return this != DEFERRED;
    }
  }

  CapabilityLevel semanticRoundTripLevel();

  CapabilityLevel runtimeSmokeLevel();

  CapabilityLevel timerInMemoryLevel();

  CapabilityLevel subFlowInMemoryLevel();

  CapabilityLevel singleApprovalRuntimeLevel();

  CapabilityLevel multipleApprovalRuntimeLevel();

  CapabilityLevel taskServiceRuntimeLevel();

  CapabilityLevel instanceServiceRuntimeLevel();

  CapabilityLevel taskEventsLevel();

  CapabilityLevel instanceEventsLevel();

  CapabilityLevel taskTransferLevel();

  CapabilityLevel addSignLevel();

  CapabilityLevel configurableRejectTargetLevel();

  CapabilityLevel freeRejectionLevel();

  CapabilityLevel freeRecoveryLevel();

  CapabilityLevel freeApprovalLevel();

  CapabilityLevel childSubflowParentMetadataLevel();

  CapabilityLevel taskTimeoutPolicyLevel();

  default boolean supportsSemanticRoundTrip() {
    return semanticRoundTripLevel().isSupported();
  }

  default boolean supportsRuntimeSmoke() {
    return runtimeSmokeLevel().isSupported();
  }

  default boolean supportsTimerInMemory() {
    return timerInMemoryLevel().isSupported();
  }

  default boolean supportsSubFlowInMemory() {
    return subFlowInMemoryLevel().isSupported();
  }

  default boolean supportsSingleApprovalRuntime() {
    return singleApprovalRuntimeLevel().isSupported();
  }

  default boolean supportsMultipleApprovalRuntime() {
    return multipleApprovalRuntimeLevel().isSupported();
  }

  default boolean supportsTaskServiceRuntime() {
    return taskServiceRuntimeLevel().isSupported();
  }

  default boolean supportsInstanceServiceRuntime() {
    return instanceServiceRuntimeLevel().isSupported();
  }

  default boolean supportsTaskEvents() {
    return taskEventsLevel().isSupported();
  }

  default boolean supportsInstanceEvents() {
    return instanceEventsLevel().isSupported();
  }

  default boolean supportsTaskTransfer() {
    return taskTransferLevel().isSupported();
  }

  default boolean supportsAddSign() {
    return addSignLevel().isSupported();
  }

  default boolean supportsConfigurableRejectTarget() {
    return configurableRejectTargetLevel().isSupported();
  }

  default boolean supportsFreeRejection() {
    return freeRejectionLevel().isSupported();
  }

  default boolean supportsFreeRecovery() {
    return freeRecoveryLevel().isSupported();
  }

  default boolean supportsFreeApproval() {
    return freeApprovalLevel().isSupported();
  }

  default boolean supportsChildSubflowParentMetadata() {
    return childSubflowParentMetadataLevel().isSupported();
  }

  default boolean supportsTaskTimeoutPolicy() {
    return taskTimeoutPolicyLevel().isSupported();
  }

  static EngineCapability allEnabled() {
    return new DefaultEngineCapability(
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE,
        CapabilityLevel.STABLE
    );
  }

  static EngineCapability noneEnabled() {
    return new DefaultEngineCapability(
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED,
        CapabilityLevel.DEFERRED
    );
  }

  final class DefaultEngineCapability implements EngineCapability {
    private final CapabilityLevel semanticRoundTrip;
    private final CapabilityLevel runtimeSmoke;
    private final CapabilityLevel timerInMemory;
    private final CapabilityLevel subFlowInMemory;
    private final CapabilityLevel singleApprovalRuntime;
    private final CapabilityLevel multipleApprovalRuntime;
    private final CapabilityLevel taskServiceRuntime;
    private final CapabilityLevel instanceServiceRuntime;
    private final CapabilityLevel taskEvents;
    private final CapabilityLevel instanceEvents;
    private final CapabilityLevel taskTransfer;
    private final CapabilityLevel addSign;
    private final CapabilityLevel configurableRejectTarget;
    private final CapabilityLevel freeRejection;
    private final CapabilityLevel freeRecovery;
    private final CapabilityLevel freeApproval;
    private final CapabilityLevel childSubflowParentMetadata;
    private final CapabilityLevel taskTimeoutPolicy;

    public DefaultEngineCapability(
        boolean semanticRoundTrip,
        boolean runtimeSmoke,
        boolean timerInMemory,
        boolean subFlowInMemory,
        boolean singleApprovalRuntime,
        boolean multipleApprovalRuntime,
        boolean taskServiceRuntime,
        boolean instanceServiceRuntime,
        boolean taskEvents,
        boolean instanceEvents,
        boolean childSubflowParentMetadata) {
      this(
          toLevel(semanticRoundTrip),
          toLevel(runtimeSmoke),
          toLevel(timerInMemory),
          toLevel(subFlowInMemory),
          toLevel(singleApprovalRuntime),
          toLevel(multipleApprovalRuntime),
          toLevel(taskServiceRuntime),
          toLevel(instanceServiceRuntime),
          toLevel(taskEvents),
          toLevel(instanceEvents),
          CapabilityLevel.DEFERRED,
          CapabilityLevel.DEFERRED,
          CapabilityLevel.DEFERRED,
          CapabilityLevel.DEFERRED,
          CapabilityLevel.DEFERRED,
          CapabilityLevel.DEFERRED,
          toLevel(childSubflowParentMetadata),
          CapabilityLevel.DEFERRED
      );
    }

    public DefaultEngineCapability(
        boolean semanticRoundTrip,
        boolean runtimeSmoke,
        boolean timerInMemory,
        boolean subFlowInMemory,
        boolean singleApprovalRuntime,
        boolean multipleApprovalRuntime,
        boolean taskServiceRuntime,
        boolean instanceServiceRuntime,
        boolean taskEvents,
        boolean instanceEvents,
        boolean taskTransfer,
        boolean addSign,
        boolean configurableRejectTarget,
        boolean freeRejection,
        boolean freeRecovery,
        boolean freeApproval,
        boolean childSubflowParentMetadata,
        boolean taskTimeoutPolicy) {
      this(
          toLevel(semanticRoundTrip),
          toLevel(runtimeSmoke),
          toLevel(timerInMemory),
          toLevel(subFlowInMemory),
          toLevel(singleApprovalRuntime),
          toLevel(multipleApprovalRuntime),
          toLevel(taskServiceRuntime),
          toLevel(instanceServiceRuntime),
          toLevel(taskEvents),
          toLevel(instanceEvents),
          toLevel(taskTransfer),
          toLevel(addSign),
          toLevel(configurableRejectTarget),
          toLevel(freeRejection),
          toLevel(freeRecovery),
          toLevel(freeApproval),
          toLevel(childSubflowParentMetadata),
          toLevel(taskTimeoutPolicy)
      );
    }

    public DefaultEngineCapability(
        CapabilityLevel semanticRoundTrip,
        CapabilityLevel runtimeSmoke,
        CapabilityLevel timerInMemory,
        CapabilityLevel subFlowInMemory,
        CapabilityLevel singleApprovalRuntime,
        CapabilityLevel multipleApprovalRuntime,
        CapabilityLevel taskServiceRuntime,
        CapabilityLevel instanceServiceRuntime,
        CapabilityLevel taskEvents,
        CapabilityLevel instanceEvents,
        CapabilityLevel childSubflowParentMetadata) {
      this(
          semanticRoundTrip,
          runtimeSmoke,
          timerInMemory,
          subFlowInMemory,
          singleApprovalRuntime,
          multipleApprovalRuntime,
          taskServiceRuntime,
          instanceServiceRuntime,
          taskEvents,
          instanceEvents,
          CapabilityLevel.DEFERRED,
          CapabilityLevel.DEFERRED,
          CapabilityLevel.DEFERRED,
          CapabilityLevel.DEFERRED,
          CapabilityLevel.DEFERRED,
          CapabilityLevel.DEFERRED,
          childSubflowParentMetadata,
          CapabilityLevel.DEFERRED
      );
    }

    public DefaultEngineCapability(
        CapabilityLevel semanticRoundTrip,
        CapabilityLevel runtimeSmoke,
        CapabilityLevel timerInMemory,
        CapabilityLevel subFlowInMemory,
        CapabilityLevel singleApprovalRuntime,
        CapabilityLevel multipleApprovalRuntime,
        CapabilityLevel taskServiceRuntime,
        CapabilityLevel instanceServiceRuntime,
        CapabilityLevel taskEvents,
        CapabilityLevel instanceEvents,
        CapabilityLevel taskTransfer,
        CapabilityLevel addSign,
        CapabilityLevel configurableRejectTarget,
        CapabilityLevel freeRejection,
        CapabilityLevel freeRecovery,
        CapabilityLevel freeApproval,
        CapabilityLevel childSubflowParentMetadata,
        CapabilityLevel taskTimeoutPolicy) {
      this.semanticRoundTrip = semanticRoundTrip;
      this.runtimeSmoke = runtimeSmoke;
      this.timerInMemory = timerInMemory;
      this.subFlowInMemory = subFlowInMemory;
      this.singleApprovalRuntime = singleApprovalRuntime;
      this.multipleApprovalRuntime = multipleApprovalRuntime;
      this.taskServiceRuntime = taskServiceRuntime;
      this.instanceServiceRuntime = instanceServiceRuntime;
      this.taskEvents = taskEvents;
      this.instanceEvents = instanceEvents;
      this.taskTransfer = taskTransfer;
      this.addSign = addSign;
      this.configurableRejectTarget = configurableRejectTarget;
      this.freeRejection = freeRejection;
      this.freeRecovery = freeRecovery;
      this.freeApproval = freeApproval;
      this.childSubflowParentMetadata = childSubflowParentMetadata;
      this.taskTimeoutPolicy = taskTimeoutPolicy;
    }

    @Override
    public CapabilityLevel semanticRoundTripLevel() {
      return semanticRoundTrip;
    }

    @Override
    public CapabilityLevel runtimeSmokeLevel() {
      return runtimeSmoke;
    }

    @Override
    public CapabilityLevel timerInMemoryLevel() {
      return timerInMemory;
    }

    @Override
    public CapabilityLevel subFlowInMemoryLevel() {
      return subFlowInMemory;
    }

    @Override
    public CapabilityLevel singleApprovalRuntimeLevel() {
      return singleApprovalRuntime;
    }

    @Override
    public CapabilityLevel multipleApprovalRuntimeLevel() {
      return multipleApprovalRuntime;
    }

    @Override
    public CapabilityLevel taskServiceRuntimeLevel() {
      return taskServiceRuntime;
    }

    @Override
    public CapabilityLevel instanceServiceRuntimeLevel() {
      return instanceServiceRuntime;
    }

    @Override
    public CapabilityLevel taskEventsLevel() {
      return taskEvents;
    }

    @Override
    public CapabilityLevel instanceEventsLevel() {
      return instanceEvents;
    }

    @Override
    public CapabilityLevel taskTransferLevel() {
      return taskTransfer;
    }

    @Override
    public CapabilityLevel addSignLevel() {
      return addSign;
    }

    @Override
    public CapabilityLevel configurableRejectTargetLevel() {
      return configurableRejectTarget;
    }

    @Override
    public CapabilityLevel freeRejectionLevel() {
      return freeRejection;
    }

    @Override
    public CapabilityLevel freeRecoveryLevel() {
      return freeRecovery;
    }

    @Override
    public CapabilityLevel freeApprovalLevel() {
      return freeApproval;
    }

    @Override
    public CapabilityLevel childSubflowParentMetadataLevel() {
      return childSubflowParentMetadata;
    }

    @Override
    public CapabilityLevel taskTimeoutPolicyLevel() {
      return taskTimeoutPolicy;
    }

    private static CapabilityLevel toLevel(boolean enabled) {
      return enabled ? CapabilityLevel.STABLE : CapabilityLevel.DEFERRED;
    }
  }
}
