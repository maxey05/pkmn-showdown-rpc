package com.showdownrpc.presence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.showdownrpc.showdown.BattleRoom;

public class StateTracker 
{
    public StateSnapshot compute(List<String> searching, Collection<BattleRoom> battles)
    {
        Optional<BattleRoom> active = battles.stream().filter(b -> !b.finished()).max((a, b) -> Long.compare(a.lastActivityMillis(), b.lastActivityMillis()));

        if(active.isPresent())
        {
            BattleRoom b = active.get();
            return new StateSnapshot(
                b.isPlaying() ? AppState.IN_BATTLE : AppState.SPECTATING,
                b.tier(),
                b.isPlaying() ? b.opponent() : b.title(),
                b.turn(),
                b.startedAtEpochSeconds(),
                List.of()
            );
        }

        if(!searching.isEmpty())
        {
            return new StateSnapshot(AppState.SEARCHING, null, null, 0, 0, searching);
        }

        return StateSnapshot.IDLE;
    }
}
