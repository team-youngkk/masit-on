package com.masiton.restaurant.infrastructure.persistence;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort.TagDefinition;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@com.masiton.test.TestProfile
@Transactional
@DisplayName("JDBC 활성 태그 사전 조회")
class JdbcActiveTagDictionaryAdapterIntegrationTest extends com.masiton.test.FullContextIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("ACTIVE 정의의 정규화 용어만 코드와 용어 순으로 반환한다")
    void getActiveTagDictionary_ACTIVE정의만결정적순서로반환한다() {
        // given
        jdbcTemplate.update(
                "UPDATE tag_definition SET status = 'DEPRECATED' WHERE tag_code = 'MENU_GUKBAP'");
        JdbcActiveTagDictionaryAdapter adapter = new JdbcActiveTagDictionaryAdapter(jdbcTemplate);

        // when
        List<TagDefinition> definitions = adapter.getActiveTagDictionary().definitions();

        // then
        assertThat(definitions).extracting(TagDefinition::code)
                .contains("MENU_NAENGMYEON")
                .doesNotContain("MENU_GUKBAP")
                .isSorted();
        definitions.forEach(definition -> assertThat(definition.terms())
                .isSortedAccordingTo(Comparator.naturalOrder()));
        assertThat(definitions.stream()
                .filter(definition -> definition.code().equals("MENU_NAENGMYEON"))
                .findFirst()
                .orElseThrow()
                .terms()).containsExactly("냉면", "물냉면", "비빔냉면");
    }
}
