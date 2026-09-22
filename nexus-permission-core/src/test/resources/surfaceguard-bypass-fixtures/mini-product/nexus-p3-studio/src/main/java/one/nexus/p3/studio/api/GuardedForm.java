package one.nexus.p3.studio.api;

import one.nexus.p3.infra.permission.FormSurfaceGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mini-product form list goes through FormSurfaceGuard. */
@RestController
@RequestMapping("/api/v1/p3/forms")
public class GuardedForm {
    private final FormSurfaceGuard surfaces;

    public GuardedForm(FormSurfaceGuard surfaces) {
        this.surfaces = surfaces;
    }

    @GetMapping
    public String list() {
        surfaces.requireRead();
        return "forms";
    }
}
