package com.masiton.common.idempotency.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import com.masiton.common.idempotency.application.port.in.IdempotentCreationUseCase.CreationAction;
import com.masiton.common.idempotency.application.port.out.IdempotencyRecordStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("멱등 생성 서비스")
class IdempotentCreationServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-04T10:00:00Z");

    private final IdempotencyRecordStore store = mock(IdempotencyRecordStore.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final IdempotentCreationService service = new IdempotentCreationService(
            store, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC), transactionManager);

    @BeforeEach
    void setUpTransactionManager() {
        when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
    }

    @Test
    @DisplayName("최초 성공은 자원 생성과 24시간 멱등 기록 저장을 같은 트랜잭션에 요청한다")
    void 최초요청_생성성공_24시간기록을저장한다() {
        // given
        IdempotencyRequest request = request(IdempotencyActorType.MEMBER, UUID.randomUUID(),
                IdempotencyApiScope.MEMBER_COLLECTIONS, hash(1));
        IdempotencyResponse response = response();
        CreationAction action = mock(CreationAction.class);
        when(store.find(request)).thenReturn(Optional.empty());
        when(action.create()).thenReturn(response);

        // when
        IdempotencyExecutionResult result = service.execute(request, action);

        // then
        assertThat(result.replayed()).isFalse();
        assertThat(result.response()).isEqualTo(response);
        ArgumentCaptor<IdempotencyRecord> record = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(store).save(record.capture());
        assertThat(record.getValue().createdAt()).isEqualTo(NOW);
        assertThat(record.getValue().expiresAt()).isEqualTo(NOW.plusHours(24));
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    @DisplayName("같은 주체와 scope의 같은 키·같은 요청은 최초 응답을 재생한다")
    void 같은키_같은요청_최초응답을재생한다() {
        // given
        IdempotencyRequest request = request(IdempotencyActorType.MEMBER, UUID.randomUUID(),
                IdempotencyApiScope.MEMBER_COLLECTIONS, hash(2));
        IdempotencyRecord existing = record(request, response(), NOW.plusHours(1));
        CreationAction action = mock(CreationAction.class);
        when(store.find(request)).thenReturn(Optional.of(existing));

        // when
        IdempotencyExecutionResult result = service.execute(request, action);

        // then
        assertThat(result.replayed()).isTrue();
        assertThat(result.response()).isEqualTo(existing.response());
        verify(action, never()).create();
        verify(store, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("같은 키를 다른 요청 본문에 재사용하면 충돌로 거부한다")
    void 같은키_다른요청_충돌로거부한다() {
        // given
        IdempotencyRequest request = request(IdempotencyActorType.MEMBER, UUID.randomUUID(),
                IdempotencyApiScope.MEMBER_COLLECTIONS, hash(3));
        IdempotencyRecord existing = record(request, response(), NOW.plusHours(1), hash(4));
        when(store.find(request)).thenReturn(Optional.of(existing));

        // when & then
        assertThatThrownBy(() -> service.execute(request, () -> response()))
                .isInstanceOfSatisfying(IdempotencyKeyReusedException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                });
    }

    @Test
    @DisplayName("정확히 만료 시각인 기록은 삭제하고 새 성공 기록으로 교체한다")
    void 만료기록_경계시각_삭제후새요청으로처리한다() {
        // given
        IdempotencyRequest request = request(IdempotencyActorType.ADMIN, UUID.randomUUID(),
                IdempotencyApiScope.ADMIN_CURATIONS, hash(5));
        IdempotencyRecord expired = record(request, response(), NOW);
        IdempotencyResponse replacement = response();
        when(store.find(request)).thenReturn(Optional.of(expired));

        // when
        IdempotencyExecutionResult result = service.execute(request, () -> replacement);

        // then
        assertThat(result.replayed()).isFalse();
        verify(store).deleteIfExpired(request, NOW);
        verify(store).save(org.mockito.ArgumentMatchers.argThat(
                record -> record.response().equals(replacement)));
    }

    @Test
    @DisplayName("동시 최초 요청의 고유 키 패자는 롤백 뒤 승자 기록을 다시 읽어 재생한다")
    void 동시최초요청_고유키충돌_승자기록을재조회한다() {
        // given
        IdempotencyRequest request = request(IdempotencyActorType.MEMBER, UUID.randomUUID(),
                IdempotencyApiScope.MEMBER_REPORTS, hash(6));
        IdempotencyResponse response = response();
        IdempotencyRecord winner = record(request, response, NOW.plusHours(24));
        when(store.find(request)).thenReturn(Optional.empty(), Optional.of(winner));
        org.mockito.Mockito.doThrow(new IdempotencyRecordAlreadyExistsException(
                new IllegalStateException("unique conflict")))
                .when(store).save(org.mockito.ArgumentMatchers.any());

        // when
        IdempotencyExecutionResult result = service.execute(request, () -> response);

        // then
        assertThat(result.replayed()).isTrue();
        assertThat(result.response()).isEqualTo(response);
        verify(store, times(2)).find(request);
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    @DisplayName("생성 동작이 자연 키 경합에서 실패해도 같은 멱등 키 승자가 있으면 응답을 재생한다")
    void 동시최초요청_생성동작경합실패_승자기록을재생한다() {
        // given
        IdempotencyRequest request = request(IdempotencyActorType.MEMBER, UUID.randomUUID(),
                IdempotencyApiScope.MEMBER_SUBMISSIONS, hash(8));
        IdempotencyResponse response = response();
        IdempotencyRecord winner = record(request, response, NOW.plusHours(24));
        when(store.find(request)).thenReturn(Optional.empty(), Optional.of(winner));

        // when
        IdempotencyExecutionResult result = service.execute(request, () -> {
            throw new IllegalStateException("natural key conflict");
        });

        // then
        assertThat(result.replayed()).isTrue();
        assertThat(result.response()).isEqualTo(response);
        verify(store, times(2)).find(request);
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    @DisplayName("주체 또는 API scope가 다르면 같은 키도 별도 범위로 전달한다")
    void 격리키_주체또는Scope다름_별도요청으로처리한다() {
        // given
        UUID actorId = UUID.randomUUID();
        IdempotencyRequest memberCollection = request(IdempotencyActorType.MEMBER, actorId,
                IdempotencyApiScope.MEMBER_COLLECTIONS, hash(7));
        IdempotencyRequest memberReport = request(IdempotencyActorType.MEMBER, actorId,
                IdempotencyApiScope.MEMBER_REPORTS, hash(7));
        when(store.find(memberCollection)).thenReturn(Optional.empty());
        when(store.find(memberReport)).thenReturn(Optional.empty());

        // when
        service.execute(memberCollection, this::response);
        service.execute(memberReport, this::response);

        // then
        verify(store).find(memberCollection);
        verify(store).find(memberReport);
        verify(store, times(2)).save(org.mockito.ArgumentMatchers.any());
    }

    private IdempotencyRequest request(
            IdempotencyActorType actorType,
            UUID actorId,
            IdempotencyApiScope scope,
            byte[] requestHash
    ) {
        return IdempotencyRequest.of(actorType, actorId, scope, "opaque-key", requestHash);
    }

    private IdempotencyRecord record(
            IdempotencyRequest request,
            IdempotencyResponse response,
            OffsetDateTime expiresAt
    ) {
        return record(request, response, expiresAt, request.requestHash());
    }

    private IdempotencyRecord record(
            IdempotencyRequest request,
            IdempotencyResponse response,
            OffsetDateTime expiresAt,
            byte[] requestHash
    ) {
        return new IdempotencyRecord(
                UUID.randomUUID(), request.actorType(), request.actorId(), request.apiScope(),
                request.keyHash(), requestHash, response, NOW.minusHours(1), expiresAt);
    }

    private IdempotencyResponse response() {
        UUID resourceId = UUID.randomUUID();
        return new IdempotencyResponse(201, "{\"id\":\"" + resourceId + "\"}", resourceId);
    }

    private byte[] hash(int value) {
        byte[] hash = new byte[32];
        hash[0] = (byte) value;
        return hash;
    }
}
