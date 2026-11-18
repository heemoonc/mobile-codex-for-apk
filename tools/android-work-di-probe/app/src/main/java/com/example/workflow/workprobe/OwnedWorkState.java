package com.example.workflow.workprobe;
import java.util.*;
/** Only explicitly owned probe work may update diagnostic state. */
public final class OwnedWorkState {
 private final Map<UUID,Integer> counts=new HashMap<>();
 private final Set<UUID> stopped=new HashSet<>();
 public synchronized void own(UUID id) {counts.putIfAbsent(id,0);}
 public synchronized void entered(UUID id) {if(counts.containsKey(id))counts.put(id,count(id)+1);}
 public synchronized int count(UUID id) {return counts.getOrDefault(id,0);}
 public synchronized void markStopped(UUID id) {if(counts.containsKey(id))stopped.add(id);}
 public synchronized boolean isStopped(UUID id) {return stopped.contains(id);}
 public synchronized void forget(Collection<UUID> ids) {for(UUID id:ids){counts.remove(id);stopped.remove(id);}}
}
