package com.ouroboros.metadata.resource;

import java.util.List;

public interface MetadataSource {
  List<MetadataResource> findResources(TypedMetadataSpec<?> spec);
}
