package com.researchflow.externaltrace;

import com.researchflow.model.TraceView;

public record ExternalTraceView(ExternalTraceSummary summary, TraceView trace) {
}
