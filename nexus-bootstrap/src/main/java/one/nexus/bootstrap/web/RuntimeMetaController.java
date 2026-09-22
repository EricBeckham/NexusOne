package one.nexus.bootstrap.web;

import java.util.LinkedHashMap;
import java.util.Map;
import one.nexus.common.trace.TraceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta")
public class RuntimeMetaController {
    private static final Logger log = LoggerFactory.getLogger(RuntimeMetaController.class);
    private final JdbcTemplate jdbcTemplate;
    private final String springBootVersion;

    public RuntimeMetaController(
            JdbcTemplate jdbcTemplate,
            @Value("${info.app.spring-boot}") String springBootVersion) {
        this.jdbcTemplate = jdbcTemplate;
        this.springBootVersion = springBootVersion;
    }

    @GetMapping("/runtime")
    public Map<String, Object> runtime() {
        String schemaManager = jdbcTemplate.queryForObject(
                "select meta_value from platform.runtime_meta where meta_key = 'schema_manager'", String.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("product", "NexusOne");
        body.put("work_package", "M0-01");
        body.put("spring_boot", springBootVersion);
        body.put("java", Runtime.version().feature());
        body.put("schema_manager", schemaManager);
        body.put("trace_id", TraceIds.currentOrUnknown());
        log.info("runtime meta requested");
        return body;
    }
}
