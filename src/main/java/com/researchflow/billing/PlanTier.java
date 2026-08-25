package com.researchflow.billing;

public enum PlanTier {
    FREE(10, 1, 20),
    TEAM(300, 20, 2_000),
    ENTERPRISE(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final int monthlyReports;
    private final int subscriptions;
    private final int documents;

    PlanTier(int monthlyReports, int subscriptions, int documents) {
        this.monthlyReports = monthlyReports;
        this.subscriptions = subscriptions;
        this.documents = documents;
    }

    public int monthlyReports() { return monthlyReports; }
    public int subscriptions() { return subscriptions; }
    public int documents() { return documents; }
}
