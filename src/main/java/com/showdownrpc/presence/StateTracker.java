package com.showdownrpc.presence;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.showdownrpc.showdown.BattleRoom;

public class StateTracker 
{
    public StateSnapshot compute(List<String> searching, Collection<BattleRoom> battles)
    {
        // A battle you're playing always outranks one you're only watching; among
        // equals, the most recently active wins.
        Optional<BattleRoom> active = battles.stream()
            .filter(b -> !b.finished())
            .max(Comparator.comparing((BattleRoom b) -> b.isPlaying())
                           .thenComparingLong(BattleRoom::lastActivityMillis));

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
