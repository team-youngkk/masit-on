package com.masiton.restaurant.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.masiton.restaurant.application.port.in.SearchPlacesByNameUseCase;
import com.masiton.restaurant.application.port.out.PlaceSearchCandidate;
import com.masiton.restaurant.application.port.out.PlaceSearchPort;

/**
 * {@link SearchPlacesByNameUseCase}의 구현체다. {@code PlaceSearchPort}로 위임만 하는 얇은
 * 경계이며, 이 domain 밖에서는 {@code port.out}이 아니라 이 유스케이스를 통해 호출한다.
 */
@Service
class SearchPlacesByNameService implements SearchPlacesByNameUseCase {

    private final PlaceSearchPort placeSearchPort;

    SearchPlacesByNameService(PlaceSearchPort placeSearchPort) {
        this.placeSearchPort = placeSearchPort;
    }

    @Override
    public List<PlaceSearchCandidate> search(String name) {
        return placeSearchPort.search(name);
    }
}
