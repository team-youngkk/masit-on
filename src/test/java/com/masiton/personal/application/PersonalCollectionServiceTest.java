package com.masiton.personal.application;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.masiton.common.idempotency.application.IdempotencyExecutionResult;
import com.masiton.common.idempotency.application.port.in.IdempotentCreationUseCase;
import com.masiton.common.web.BusinessException;
import com.masiton.member.application.port.in.LockActiveMemberUseCase;
import com.masiton.personal.application.port.in.CollectionOption;
import com.masiton.personal.application.port.in.CollectionOption.AdditionStatus;
import com.masiton.personal.application.port.in.PersonalCollectionUseCase.CollectionSummary;
import com.masiton.personal.application.port.out.PersonalCollectionQueryPort;
import com.masiton.personal.application.port.out.PersonalCollectionStore;
import com.masiton.restaurant.application.port.in.FindRestaurantReferenceUseCase;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("개인 컬렉션 서비스")
class PersonalCollectionServiceTest {

    private final PersonalCollectionStore store = mock(PersonalCollectionStore.class);
    private final PersonalCollectionQueryPort queries = mock(PersonalCollectionQueryPort.class);
    private final LockActiveMemberUseCase activeMembers = mock(LockActiveMemberUseCase.class);
    private final FindRestaurantReferenceUseCase references = mock(FindRestaurantReferenceUseCase.class);
    private final IdempotentCreationUseCase idempotentCreation = mock(IdempotentCreationUseCase.class);
    private final PersonalCollectionService service = new PersonalCollectionService(
            store, queries, activeMembers, references, idempotentCreation, new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneOffset.UTC));

    @Test
    @DisplayName("멱등 생성 액션은 활성 회원을 잠근 뒤 컬렉션을 저장한다")
    void create_신규생성_활성회원잠금후저장한다() {
        UUID memberId = UUID.randomUUID();
        CollectionSummary created = new CollectionSummary(
                UUID.randomUUID(), "가족과 갈 곳", 0,
                java.time.OffsetDateTime.parse("2026-08-03T10:00:00Z"),
                java.time.OffsetDateTime.parse("2026-08-03T10:00:00Z"));
        when(store.create(eq(memberId), any(), eq("가족과 갈 곳"), any())).thenReturn(created);
        when(idempotentCreation.execute(any(), any())).thenAnswer(invocation -> {
            IdempotentCreationUseCase.CreationAction action = invocation.getArgument(1);
            return IdempotencyExecutionResult.created(action.create());
        });

        service.create(memberId, "collection-key", "가족과 갈 곳");

        var ordered = inOrder(activeMembers, store);
        ordered.verify(activeMembers).lockActiveMember(memberId);
        ordered.verify(store).create(eq(memberId), any(), eq("가족과 갈 곳"), any());
    }

    @Test
    @DisplayName("이름은 앞뒤 공백을 제거한 뒤 저장소에 전달한다")
    void rename_공백이있는이름_trim후변경한다() {
        UUID memberId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        CollectionSummary summary = mock(CollectionSummary.class);
        when(store.rename(eq(memberId), eq(collectionId), eq("가족과 갈 곳"), any()))
                .thenReturn(true);
        when(queries.findSummary(memberId, collectionId)).thenReturn(Optional.of(summary));

        service.rename(memberId, collectionId, "  가족과 갈 곳  ");

        verify(store).rename(eq(memberId), eq(collectionId), eq("가족과 갈 곳"), any());
        verify(queries).findSummary(memberId, collectionId);
    }

    @Test
    @DisplayName("50자를 넘는 이름은 저장 전에 거부한다")
    void rename_51자이름_INVALID_FIELD_VALUE를반환한다() {
        assertThatThrownBy(() -> service.rename(UUID.randomUUID(), UUID.randomUUID(), "가".repeat(51)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_FIELD_VALUE"));
        verify(store, never()).rename(any(), any(), any(), any());
    }

    @Test
    @DisplayName("비공개 맛집은 컬렉션에 추가하지 않는다")
    void addRestaurant_비공개맛집_RESTAURANT_NOT_FOUND를반환한다() {
        UUID restaurantId = UUID.randomUUID();
        when(references.findRestaurantReference(restaurantId)).thenReturn(Optional.of(
                new FindRestaurantReferenceUseCase.RestaurantReference(restaurantId, false)));

        assertThatThrownBy(() -> service.addRestaurant(
                UUID.randomUUID(), UUID.randomUUID(), restaurantId))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("RESTAURANT_NOT_FOUND"));
        verify(store, never()).addRestaurant(any(), any(), any(), any());
    }

    @Test
    @DisplayName("공개·활성 맛집의 컬렉션 추가 옵션을 조회한다")
    void getCollectionOptions_공개활성맛집_저장소조회결과를반환한다() {
        UUID memberId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        CollectionOption option = new CollectionOption(
                UUID.randomUUID(), "가고 싶은 곳", 3, AdditionStatus.AVAILABLE);
        when(references.findRestaurantReference(restaurantId)).thenReturn(Optional.of(
                new FindRestaurantReferenceUseCase.RestaurantReference(restaurantId, true)));
        when(queries.findOptions(memberId, restaurantId)).thenReturn(List.of(option));

        List<CollectionOption> result = service.getCollectionOptions(memberId, restaurantId);

        assertThat(result).containsExactly(option);
        verify(queries).findOptions(memberId, restaurantId);
    }

    @Test
    @DisplayName("비공개 맛집은 컬렉션 추가 옵션을 조회할 수 없다")
    void getCollectionOptions_비공개맛집_RESTAURANT_NOT_FOUND를반환한다() {
        UUID memberId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        when(references.findRestaurantReference(restaurantId)).thenReturn(Optional.of(
                new FindRestaurantReferenceUseCase.RestaurantReference(restaurantId, false)));

        assertThatThrownBy(() -> service.getCollectionOptions(memberId, restaurantId))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("RESTAURANT_NOT_FOUND"));
        verify(queries, never()).findOptions(any(), any());
    }

    @Test
    @DisplayName("컬렉션 추가 옵션 조회는 읽기 전용 트랜잭션이다")
    void getCollectionOptions_조회메서드_readOnly트랜잭션이다() throws NoSuchMethodException {
        Method method = PersonalCollectionService.class.getMethod(
                "getCollectionOptions", UUID.class, UUID.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
