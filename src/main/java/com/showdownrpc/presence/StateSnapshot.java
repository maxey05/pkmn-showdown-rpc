package com.showdownrpc.presence;

import java.util.List;

public record StateSnapshot( 
    AppState state,
    String format,
    String opponent,
    int turn,
    long startedAtEpochSeconds,
    List<String> searching
)
{
    public static final StateSnapshot IDLE = new StateSnapshot(AppState.IDLE, null, null, 0, 0, List.of());
}
