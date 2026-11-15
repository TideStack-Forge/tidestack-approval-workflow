package com.ouroboros.workflow.approval;

import java.util.Objects;
import java.util.Optional;

import com.ouroboros.util.SPI;

public interface CurrentUserSupplier {
  String getIdentity();

  static Optional<String> getCurrentUserId() {
    return SPI.getCachedReversedServiceStream(CurrentUserSupplier.class)
        .map(CurrentUserSupplier::getIdentity)
        .filter(Objects::nonNull)
        .findFirst();
  }
}
