   
                      
   
package com.haoran.music.service;

   
                                      
   
public class PaymentCompletionRecoverySummary {

    private int candidates;
    private int claimed;
    private int completed;
    private int deferred;
    private int deadLettered;
    private int lostClaims;

    public void setCandidates(int candidates) {
        this.candidates = candidates;
    }

    public void recordClaimed() {
        claimed++;
    }

    public void recordCompleted() {
        completed++;
    }

    public void recordDeferred() {
        deferred++;
    }

    public void recordDeadLettered() {
        deadLettered++;
    }

    public void recordLostClaim() {
        lostClaims++;
    }

    public int getCandidates() {
        return candidates;
    }

    public int getClaimed() {
        return claimed;
    }

    public int getCompleted() {
        return completed;
    }

    public int getDeferred() {
        return deferred;
    }

    public int getDeadLettered() {
        return deadLettered;
    }

    public int getLostClaims() {
        return lostClaims;
    }
}
