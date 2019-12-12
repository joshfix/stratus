/* (c) Planet Labs Inc. - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package stratus.redis.catalog.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by joshfix on 8/19/16.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "stratus")
public class StratusCatalogConfigProps {

    private int minWaitForInitializerCheck;
    private int maxWaitForInitializerCheck;
    private int initializerTimeout;
    private String proxyBaseUrl = "";
    private Notifications notifications = new Notifications();

    @Data
    public static class Notifications {

        private List<String> listeners = Arrays.asList("CacheClearingListener");

    }
}
