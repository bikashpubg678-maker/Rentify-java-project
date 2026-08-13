package carrental.service;

import carrental.model.ActivityLog;
import carrental.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Activity log writer/reader.
 */
@Service
public class ActivityLogService {

    private final ActivityLogRepository repo;

    public ActivityLogService(ActivityLogRepository repo) {
        this.repo = repo;
    }

    public void log(ActivityLog.Action action, String description) {
        repo.save(new ActivityLog(action, description));
    }

    public List<ActivityLog> recent() {
        return repo.findAllByOrderByTimestampDesc();
    }
}
