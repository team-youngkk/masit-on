package com.masiton.visit.domain.model;

/** Visit 생성에 필요한 실제 방문 근거 확인이 누락됐을 때 발생한다. */
public class VisitEvidenceRequiredException extends RuntimeException {

    public VisitEvidenceRequiredException() {
        super("Visit registration requires confirmed visit evidence.");
    }
}
