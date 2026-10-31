package com.franchiseads.validation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdCopyRules.class)
public class AdCopyRulesConfiguration {

}
