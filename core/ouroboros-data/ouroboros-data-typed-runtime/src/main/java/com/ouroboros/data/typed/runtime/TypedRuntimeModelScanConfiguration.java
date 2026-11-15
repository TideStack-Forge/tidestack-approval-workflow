package com.ouroboros.data.typed.runtime;

import org.springframework.context.annotation.Configuration;

import com.ouroboros.data.annotation.DataModelScan;

@Configuration(proxyBeanMethods = false)
@DataModelScan("com.ouroboros.data.pkgenerator.model")
public class TypedRuntimeModelScanConfiguration {
}
