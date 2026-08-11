package com.masiton.restaurant.application.port.out;

/**
 * 자연어 문장을 기존 맛집 검색 조건으로 변환하는 P1 해석기 Port다.
 * 입력 문장은 호출 범위에서만 사용하며 구현체가 저장하거나 로그에 남겨서는 안 된다.
 */
public interface NaturalLanguageParser {

    NaturalLanguageInterpretation parse(String sentence);
}
