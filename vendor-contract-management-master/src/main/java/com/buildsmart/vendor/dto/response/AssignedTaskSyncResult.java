package com.buildsmart.vendor.dto.response;

import java.util.List;

public record AssignedTaskSyncResult(
        int newTasksSynced,
        int alreadyExisted,
        List<AssignedTaskResponse> newTasks
) {}
