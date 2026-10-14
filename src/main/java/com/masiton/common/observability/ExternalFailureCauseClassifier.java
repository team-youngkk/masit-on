package com.masiton.common.observability;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** 원인 체인의 메시지나 원문을 노출하지 않고 외부 시간 초과만 분류한다. */
public final class ExternalFailureCauseClassifier {

    private ExternalFailureCauseClassifier() {
    }

    public static boolean hasTimeoutCause(Throwable exception) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = exception; current != null && visited.add(current); current = current.getCause()) {
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
