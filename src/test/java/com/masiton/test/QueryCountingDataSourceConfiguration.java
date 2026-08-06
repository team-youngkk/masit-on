package com.masiton.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * TST-E2-PERF-001이 요구하는 N+1 회귀 검증을 여러 테스트가 공유하도록 추출한 fixture다.
 * 원문은 {@code RestaurantDetailApiTest}의 private nested class였다({@code QueryCountingDataSourceConfiguration}
 * + {@code CountingDataSourceInvocationHandler}). 그 파일은 다른 담당자 영역이라 손대지 않고,
 * 동일한 JDK 동적 {@link Proxy} 패턴만 재사용 가능한 공유 위치로 옮겼다(중복은 감수한다).
 *
 * <p>운영 {@code DataSource} Bean을 JDK 동적 Proxy로 감싸 {@code Connection.prepareStatement}/
 * {@code prepareCall} 호출 횟수를 센다. Mockito·AOP 프레임워크나 새 라이브러리 의존 없이
 * JDK 표준 {@link Proxy}만 사용한다.
 *
 * <p>사용법: 테스트 클래스에 {@code @Import(QueryCountingDataSourceConfiguration.class)}를 붙이고,
 * 각 측정 전에 {@link #reset()}을 호출한 뒤 {@link #preparedStatementCount()}로 호출 수를 읽는다.
 * 카운터는 static이므로 같은 프로세스에서 실행되는 여러 테스트 메서드가 순서에 의존하지 않도록
 * 반드시 측정 직전에 {@link #reset()}을 호출해야 한다.
 */
@TestConfiguration
public class QueryCountingDataSourceConfiguration {

    private static final AtomicInteger PREPARED_STATEMENT_COUNT = new AtomicInteger();

    public static void reset() {
        PREPARED_STATEMENT_COUNT.set(0);
    }

    public static int preparedStatementCount() {
        return PREPARED_STATEMENT_COUNT.get();
    }

    @Bean
    static BeanPostProcessor queryCountingDataSourceBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !Proxy.isProxyClass(bean.getClass())) {
                    return Proxy.newProxyInstance(
                            DataSource.class.getClassLoader(),
                            new Class<?>[] {DataSource.class},
                            new CountingDataSourceInvocationHandler(dataSource));
                }
                return bean;
            }
        };
    }

    private record CountingDataSourceInvocationHandler(DataSource delegate) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = invokeDelegate(delegate, method, args);
            if ("getConnection".equals(method.getName()) && result instanceof Connection connection) {
                return Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        new CountingConnectionInvocationHandler(connection));
            }
            return result;
        }
    }

    private record CountingConnectionInvocationHandler(Connection delegate) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("prepareStatement".equals(methodName) || "prepareCall".equals(methodName)) {
                PREPARED_STATEMENT_COUNT.incrementAndGet();
            }
            return invokeDelegate(delegate, method, args);
        }
    }

    private static Object invokeDelegate(Object delegate, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
