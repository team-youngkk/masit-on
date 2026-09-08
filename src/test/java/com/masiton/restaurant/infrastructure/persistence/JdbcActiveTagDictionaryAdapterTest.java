package com.masiton.restaurant.infrastructure.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.masiton.restaurant.application.port.out.ActiveTagDictionaryPort.ActiveTagDictionarySnapshot;
import com.masiton.restaurant.application.port.out.ActiveTagDictionaryUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JDBC 활성 태그 사전 cache")
class JdbcActiveTagDictionaryAdapterTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-09-08T00:00:00Z");

    @Test
    @DisplayName("최초 DB 적재 실패는 unavailable 예외를 반환하고 cache하지 않는다")
    void getActiveTagDictionary_최초적재실패_unavailable예외와재시도를제공한다() {
        // given
        AtomicInteger loadCount = new AtomicInteger();
        JdbcActiveTagDictionaryAdapter adapter = new JdbcActiveTagDictionaryAdapter(
                () -> {
                    loadCount.incrementAndGet();
                    throw new DataAccessResourceFailureException("database unavailable");
                }, Clock.fixed(INITIAL_TIME, ZoneOffset.UTC), Duration.ofSeconds(30));

        // when & then
        assertThatThrownBy(adapter::getActiveTagDictionary)
                .isInstanceOf(ActiveTagDictionaryUnavailableException.class);
        assertThatThrownBy(adapter::getActiveTagDictionary)
                .isInstanceOf(ActiveTagDictionaryUnavailableException.class);
        assertThat(loadCount).hasValue(2);
    }

    @Test
    @DisplayName("TTL 동안 같은 불변 snapshot을 반환한다")
    void getActiveTagDictionary_TTL이내_같은불변Snapshot을반환한다() {
        // given
        AtomicInteger loadCount = new AtomicInteger();
        MutableClock clock = new MutableClock(INITIAL_TIME);
        JdbcActiveTagDictionaryAdapter adapter = new JdbcActiveTagDictionaryAdapter(
                () -> {
                    loadCount.incrementAndGet();
                    return List.of(
                            new JdbcActiveTagDictionaryAdapter.TagTermRow("TAG_A", "가"),
                            new JdbcActiveTagDictionaryAdapter.TagTermRow("TAG_A", "나"));
                }, clock, Duration.ofSeconds(30));

        // when
        ActiveTagDictionarySnapshot first = adapter.getActiveTagDictionary();
        clock.advance(Duration.ofSeconds(29));
        ActiveTagDictionarySnapshot second = adapter.getActiveTagDictionary();

        // then
        assertThat(second).isSameAs(first);
        assertThat(loadCount).hasValue(1);
        assertThatThrownBy(() -> first.definitions().add(first.definitions().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.definitions().getFirst().terms().add("다"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("만료 갱신 실패 시 stale snapshot을 반환하지 않고 다음 요청이 다시 갱신한다")
    void getActiveTagDictionary_만료갱신실패_stale없이다음요청이재시도한다() {
        // given
        AtomicInteger loadCount = new AtomicInteger();
        AtomicReference<List<JdbcActiveTagDictionaryAdapter.TagTermRow>> rows = new AtomicReference<>(
                List.of(new JdbcActiveTagDictionaryAdapter.TagTermRow("TAG_A", "가")));
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        MutableClock clock = new MutableClock(INITIAL_TIME);
        JdbcActiveTagDictionaryAdapter adapter = new JdbcActiveTagDictionaryAdapter(
                () -> {
                    loadCount.incrementAndGet();
                    if (failure.get() != null) {
                        throw failure.get();
                    }
                    return rows.get();
                }, clock, Duration.ofSeconds(30));
        adapter.getActiveTagDictionary();
        clock.advance(Duration.ofSeconds(30));
        failure.set(new DataAccessResourceFailureException("database unavailable"));

        // when & then
        assertThatThrownBy(adapter::getActiveTagDictionary)
                .isInstanceOf(ActiveTagDictionaryUnavailableException.class);
        failure.set(null);
        rows.set(List.of(new JdbcActiveTagDictionaryAdapter.TagTermRow("TAG_B", "나")));
        ActiveTagDictionarySnapshot refreshed = adapter.getActiveTagDictionary();

        assertThat(loadCount).hasValue(3);
        assertThat(refreshed.definitions()).extracting(definition -> definition.code())
                .containsExactly("TAG_B");
    }

    @Test
    @DisplayName("동시 최초 요청은 DB 갱신 한 번과 같은 snapshot을 공유한다")
    void getActiveTagDictionary_동시최초요청_DB갱신한번만수행한다() throws Exception {
        // given
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        JdbcActiveTagDictionaryAdapter adapter = new JdbcActiveTagDictionaryAdapter(
                () -> {
                    loadCount.incrementAndGet();
                    loaderEntered.countDown();
                    await(releaseLoader);
                    return List.of(new JdbcActiveTagDictionaryAdapter.TagTermRow("TAG_A", "가"));
                }, Clock.fixed(INITIAL_TIME, ZoneOffset.UTC), Duration.ofSeconds(30));
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            // when
            List<Future<ActiveTagDictionarySnapshot>> futures = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(adapter::getActiveTagDictionary))
                    .toList();
            assertThat(loaderEntered.await(5, TimeUnit.SECONDS)).isTrue();
            releaseLoader.countDown();
            ActiveTagDictionarySnapshot first = futures.getFirst().get(5, TimeUnit.SECONDS);

            // then
            for (Future<ActiveTagDictionarySnapshot> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isSameAs(first);
            }
            assertThat(loadCount).hasValue(1);
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("TTL 만료 뒤 동시 요청은 DB를 한 번 갱신하고 같은 불변 snapshot을 공유한다")
    void getActiveTagDictionary_TTL만료후동시요청_DB갱신한번과같은Snapshot을제공한다() throws Exception {
        // given
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch refreshEntered = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        MutableClock clock = new MutableClock(INITIAL_TIME);
        JdbcActiveTagDictionaryAdapter adapter = new JdbcActiveTagDictionaryAdapter(
                () -> {
                    int invocation = loadCount.incrementAndGet();
                    if (invocation == 2) {
                        refreshEntered.countDown();
                        await(releaseRefresh);
                    }
                    String code = invocation == 1 ? "TAG_OLD" : "TAG_NEW";
                    return List.of(new JdbcActiveTagDictionaryAdapter.TagTermRow(code, "용어"));
                }, clock, Duration.ofSeconds(30));
        ActiveTagDictionarySnapshot expired = adapter.getActiveTagDictionary();
        clock.advance(Duration.ofSeconds(30));
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            // when
            List<Future<ActiveTagDictionarySnapshot>> futures = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(adapter::getActiveTagDictionary))
                    .toList();
            assertThat(refreshEntered.await(5, TimeUnit.SECONDS)).isTrue();
            releaseRefresh.countDown();
            ActiveTagDictionarySnapshot refreshed = futures.getFirst().get(5, TimeUnit.SECONDS);

            // then
            assertThat(refreshed).isNotSameAs(expired);
            assertThat(refreshed.definitions()).extracting(definition -> definition.code())
                    .containsExactly("TAG_NEW");
            for (Future<ActiveTagDictionarySnapshot> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isSameAs(refreshed);
            }
            assertThatThrownBy(() -> refreshed.definitions().add(refreshed.definitions().getFirst()))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(loadCount).hasValue(2);
        } finally {
            releaseRefresh.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", exception);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
