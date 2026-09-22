package one.nexus.p3.dump;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Intentional fixture: P3 dump surface with no SurfaceGuard token. */
@RestController
@RequestMapping("/api/v1/p3/dumps")
public class RawDumpController {
    @PostMapping("/export")
    public String dump() {
        return "unguarded-physical-dump";
    }
}
