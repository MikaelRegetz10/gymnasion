package com.gymnasion.tcc.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "strava")
@Getter
@Setter
public class StravaProperties {

    private String clientId;
    private String clientSecret;
    private String redirectUri = "http://localhost/strava/callback";
    private boolean mockEnabled = false;

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
