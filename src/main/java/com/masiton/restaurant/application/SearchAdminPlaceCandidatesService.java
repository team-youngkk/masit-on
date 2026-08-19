package com.masiton.restaurant.application;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.masiton.common.web.BusinessException;
import com.masiton.common.web.ErrorCode;
import com.masiton.restaurant.application.port.in.SearchAdminPlaceCandidatesUseCase;
import com.masiton.restaurant.application.port.out.PlaceSearchCandidate;
import com.masiton.restaurant.application.port.out.PlaceSearchPort;

/**
 * 검색 결과 정렬과 자치구 파생을 담당한다. 외부 HTTP 호출만 있으므로 트랜잭션을 열지 않는다.
 */
@Service
class SearchAdminPlaceCandidatesService implements SearchAdminPlaceCandidatesUseCase {

    private static final int MAX_ROAD_ADDRESS_LENGTH = 255;

    private final PlaceSearchPort placeSearchPort;

    SearchAdminPlaceCandidatesService(PlaceSearchPort placeSearchPort) {
        this.placeSearchPort = placeSearchPort;
    }

    @Override
    public List<PlaceCandidateResult> search(SearchAdminPlaceCandidatesCommand command) {
        String name = validateName(command == null ? null : command.name());
        String normalizedHint = normalizeHint(command == null ? null : command.roadAddressHint());

        List<PlaceSearchCandidate> candidates;
        try {
            candidates = placeSearchPort.search(name);
        } catch (PlaceSearchFailedException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        return candidates.stream()
                .sorted(Comparator.comparingInt(
                        (PlaceSearchCandidate candidate) -> relevance(candidate.roadAddress(), normalizedHint))
                        .reversed())
                .map(this::toResult)
                .toList();
    }

    /** 동점이면 정렬 전 순서(카카오 응답 순서)를 유지한다. Stream.sorted는 안정 정렬이다. */
    private int relevance(String roadAddress, String normalizedHint) {
        if (normalizedHint == null || roadAddress == null) {
            return 0;
        }
        String[] hintTokens = normalizedHint.split("\\s+");
        String[] addressTokens = roadAddress.trim().split("\\s+");
        int score = 0;
        int limit = Math.min(hintTokens.length, addressTokens.length);
        for (int i = 0; i < limit; i++) {
            if (!hintTokens[i].equals(addressTokens[i])) {
                break;
            }
            score++;
        }
        return score;
    }

    private PlaceCandidateResult toResult(PlaceSearchCandidate candidate) {
        return new PlaceCandidateResult(
                candidate.placeName(),
                candidate.kakaoPlaceUrl(),
                candidate.roadAddress(),
                candidate.phoneNumber(),
                districtOf(candidate.roadAddress()));
    }

    /** 서울 자치구를 뽑을 수 없으면 null로 두고 항목 자체는 남긴다. */
    private String districtOf(String roadAddress) {
        return SeoulRoadAddressNormalizer.extractDistrict(roadAddress).orElse(null);
    }

    private String normalizeHint(String roadAddressHint) {
        if (roadAddressHint == null) {
            return null;
        }
        String normalized = SeoulRoadAddressNormalizer.normalize(roadAddressHint);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_ROAD_ADDRESS_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "roadAddressHint is invalid.");
        }
        return normalized;
    }

    private String validateName(String name) {
        if (name == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "name is required.");
        }
        String normalized = name.strip();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_VALUE, "name is invalid.");
        }
        return normalized;
    }
}
