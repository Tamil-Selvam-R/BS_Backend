package com.buildsmart.siteops.dto;

import java.util.List;

public record AssignedTaskSyncResult(
        int newTasksSynced,
        int alreadyExisted,
        List<AssignedTaskResponse> newTasks
) {}
