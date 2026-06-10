package com.edge.pulse.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "survey")
@Getter
@Setter
public class SurveyProperties {
    private int defaultAnonWindowMinutes = 60;
}
