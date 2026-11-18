package com.example.workflow.libraryprobe;
/** Attempts every owned cleanup, retaining the original failure and suppressed errors. */
public final class CleanupActions {
 private CleanupActions() {}
 @FunctionalInterface public interface Action { void run() throws Exception; }
 @FunctionalInterface public interface Factory<T> { T create() throws Exception; }
 private static void rethrow(Throwable failure) throws Exception {
  if(failure instanceof Error) throw (Error)failure;
  throw (Exception)failure;
 }
 public static void runAll(Action... actions) throws Exception {
  Throwable failure=null;
  for(Action action:actions) {
   try {action.run();} catch(Throwable error) {
    if(failure==null) failure=error;
    else if(failure!=error) failure.addSuppressed(error);
   }
  }
  if(failure!=null) rethrow(failure);
 }
 public static <T> T initialize(Factory<T> factory, Action cleanup) throws Exception {
  try {return factory.create();} catch(Throwable failure) {
   try {cleanup.run();} catch(Throwable error) {if(failure!=error)failure.addSuppressed(error);}
   rethrow(failure);throw new AssertionError("unreachable");
  }
 }
}
