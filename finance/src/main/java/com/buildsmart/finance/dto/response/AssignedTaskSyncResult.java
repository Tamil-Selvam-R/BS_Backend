package com.buildsmart.finance.dto.response;

import java.util.List;

public record AssignedTaskSyncResult(
        int newTasksSynced,
        int alreadyExisted,
        List<AssignedTaskResponse> newTasks
) {}
