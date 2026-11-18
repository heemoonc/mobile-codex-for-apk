package dev.mobilecodex.app.p1;

/** Single-use grant for a fixed synthetic fixture, not a pathname resolver. */
public final class FixtureGrantPolicy {
    private final String token;
    private final int limit;
    private int requests;
    private boolean granted,closed;
    public FixtureGrantPolicy(String runToken,int limit) {
        if(runToken==null||runToken.isEmpty()||limit<1||limit>16)throw new IllegalArgumentException("invalid_grant_config");
        this.token=runToken;this.limit=limit;
    }
    public synchronized boolean acquire(String runToken,String projectId,String entryId) {
        if(closed||++requests>limit||granted)return false;
        if(!token.equals(runToken)||!"project-a".equals(projectId)||!"sample-source".equals(entryId))return false;
        granted=true;return true;
    }
    public synchronized void close(){closed=true;}
}
