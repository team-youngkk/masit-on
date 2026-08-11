package com.masiton.ai.application.port.out;

import com.masiton.ai.application.port.out.dto.AiVideoExtractionRequest;
import com.masiton.ai.application.port.out.dto.AiVideoExtractionResult;

/**
 * AI provider boundary used by the asynchronous extraction worker.
 *
 * <p>The application layer only sees normalized results and failures; provider SDK and HTTP concerns
 * remain in infrastructure.</p>
 */
public interface AiVideoExtractionProvider {

    AiVideoExtractionResult extract(AiVideoExtractionRequest request);
}
