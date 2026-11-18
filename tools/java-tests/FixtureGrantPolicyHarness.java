package dev.mobilecodex.app.p1;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class FixtureGrantPolicyHarness {
    static int checks;
    public static void main(String[] args) throws Exception {
        String token="synthetic-run";
        for(String[] input:new String[][]{{"project-a","auth"},{"project-a","../synthetic-auth.txt"},{"project-a","/data/synthetic-auth.txt"},{"project-b","sample-source"},{"project-a","escape-link"},{null,"sample-source"},{"project-a",null}}) {
            FixtureGrantPolicy p=new FixtureGrantPolicy(token,16);
            check(!p.acquire(token,input[0],input[1]));
            check(p.acquire(token,"project-a","sample-source"));
        }
        FixtureGrantPolicy p=new FixtureGrantPolicy(token,16);
        check(!p.acquire("old-token","project-a","sample-source"));
        check(p.acquire(token,"project-a","sample-source"));
        check(!p.acquire(token,"project-a","sample-source"));
        FixtureGrantPolicy closed=new FixtureGrantPolicy(token,16);closed.close();check(!closed.acquire(token,"project-a","sample-source"));
        FixtureGrantPolicy limited=new FixtureGrantPolicy(token,2);
        check(!limited.acquire(token,"project-a","auth"));check(!limited.acquire(token,"project-a","auth"));check(!limited.acquire(token,"project-a","sample-source"));
        FixtureGrantPolicy concurrent=new FixtureGrantPolicy(token,16);
        ExecutorService pool=Executors.newFixedThreadPool(4);AtomicInteger grants=new AtomicInteger();
        try {
            java.util.List<Future<?>> futures=new java.util.ArrayList<>();
            for(int i=0;i<8;i++)futures.add(pool.submit(()->{if(concurrent.acquire(token,"project-a","sample-source"))grants.incrementAndGet();}));
            for(Future<?> task:futures)task.get(2,TimeUnit.SECONDS);
            check(grants.get()==1);
        }finally{pool.shutdownNow();}
        System.out.println("FixtureGrantPolicy: "+checks+" assertions passed");
    }
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
}
