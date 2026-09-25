package one.nexus.bootstrap.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DemoProfileConfiguration {
    private static final Logger log = LoggerFactory.getLogger(DemoProfileConfiguration.class);

    @Bean
    ApplicationRunner demoProfileGate(Environment env) {
        return args -> {
            boolean enabled = env.getProperty(DemoProfileGate.ENABLED, Boolean.class, false);
            String environment = env.getProperty(DemoProfileGate.ENVIRONMENT, "local");
            String datasourceUrl = env.getProperty("spring.datasource.url", "");
            DemoProfileGate.assertAllowed(enabled, environment, datasourceUrl);
            if (enabled) {
                log.warn("演示开关已打开。禁止连生产。演示种子仅允许写入本机数据库。");
            } else {
                log.info("演示开关默认关闭。");
            }
        };
    }
}
