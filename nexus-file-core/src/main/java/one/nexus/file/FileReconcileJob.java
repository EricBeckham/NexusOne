package one.nexus.file;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * G10 file half Job entry. Tenant IsolatedQuartzJob / TaskQueue and the platform
 * runner both call {@link #run(boolean)}.
 */
public final class FileReconcileJob {
    public static final String TASK_TYPE = FileObjectReconciler.PLATFORM_PATH;
    public static final String PLATFORM_PATH = FileObjectReconciler.PLATFORM_PATH;

    private final FileObjectReconciler reconciler;
    private final AtomicReference<FileObjectReconciler.Report> last = new AtomicReference<>();

    public FileReconcileJob(FileObjectReconciler reconciler) {
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
    }

    public FileObjectReconciler.Report run(boolean apply) {
        FileObjectReconciler.Report report = reconciler.reconcile(apply);
        last.set(report);
        return report;
    }

    public FileObjectReconciler.Report run(boolean apply, UUID tenantId) {
        FileObjectReconciler.Report report = reconciler.reconcile(apply, tenantId);
        last.set(report);
        return report;
    }

    public FileObjectReconciler.Report lastResult() {
        return last.get();
    }
}
