package one.nexus.bootstrap.permission;

import one.nexus.permission.PermissionModule;
import one.nexus.permission.surface.AuthorizedSubmit;
import one.nexus.task.TaskSubmitGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Product Job / Task submit calls {@link AuthorizedSubmit} before isolation
 * enqueue. IsolatedExport stays isolation-only; callers use AuthorizedExport.
 */
@Configuration
@ConditionalOnBean(PermissionModule.class)
public class PermissionSurfaceConfiguration {
    @Bean
    TaskSubmitGuard permissionTaskSubmitGuard(PermissionModule permission) {
        return new TaskSubmitGuard() {
            @Override
            public void assertTask(String taskType) {
                AuthorizedSubmit.task(permission.service(), taskType);
            }

            @Override
            public void assertJob(String payloadType) {
                AuthorizedSubmit.job(permission.service(), payloadType);
            }
        };
    }
}
