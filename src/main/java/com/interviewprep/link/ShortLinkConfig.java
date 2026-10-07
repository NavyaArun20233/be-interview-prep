package com.interviewprep.link;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ShortLinkProperties.class)
class ShortLinkConfig {}
