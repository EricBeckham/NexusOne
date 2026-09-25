package one.nexus.p3.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/p3/product-applications")
@ConditionalOnProperty(name = "nexus.flow.engine")
public class ProductApplicationController {
    private final ProductApplicationService applications;

    public ProductApplicationController(ProductApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping("/installations/{installationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductApplicationService.ProductApplicationView create(
            @PathVariable UUID installationId, @RequestBody CreateProductApplicationRequest request) {
        return applications.create(installationId, request.values());
    }

    @ExceptionHandler(ProductApplicationService.InvalidProductApplicationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, Object> invalid(ProductApplicationService.InvalidProductApplicationException exception) {
        return Map.of("message", exception.getMessage(), "errors", exception.errors());
    }

    public record CreateProductApplicationRequest(JsonNode values) { }
}
