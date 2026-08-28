# 베이스 이미지는 명시 태그와 digest를 함께 고정한다 (ADR-RUNTIME-001 13절).
# 태그만 두면 같은 태그가 다른 이미지를 가리킬 수 있어 재현 빌드가 깨진다.
# Node 버전은 기술 정책 3절이 24.18.0으로 고정했고 frontend/.nvmrc와 같은 값이다.
FROM node:24.18.0-alpine@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd AS deps
WORKDIR /app

# package-lock.json을 함께 복사해 npm ci가 잠금 버전을 그대로 설치하게 한다.
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24.18.0-alpine@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Kakao Maps JavaScript 키는 `NEXT_PUBLIC_` 접두사라 `next build`가 클라이언트 번들에
# 값을 박아 넣는다. 런타임 환경 변수로는 바뀌지 않으므로 빌드 시점에 받아야 한다.
# 값이 없으면 지도 컴포넌트가 SDK 오류 상태로 떨어진다.
#
# 이 키는 브라우저에 노출되는 식별자이며 비밀키가 아니다. 허용 도메인 제한을 Kakao
# 콘솔에서 적용한다(ADR-MAP-001 6.5). 그래서 `--build-arg`로 받는 것을 허용한다.
# **서버용 Kakao Local REST API 키는 같은 ADR이 비밀정보로 분류하므로 이 경로로
# 넘기지 않는다.** build arg는 이미지 히스토리에 남는다.
ARG NEXT_PUBLIC_KAKAO_MAPS_JS_KEY
ENV NEXT_PUBLIC_KAKAO_MAPS_JS_KEY=${NEXT_PUBLIC_KAKAO_MAPS_JS_KEY}

# SEO 사이트 URL과 GA4 측정 ID도 `NEXT_PUBLIC_` 접두사라 `next build` 시점에
# 클라이언트 번들에 들어간다. 두 값은 선택 사항이므로 비어 있어도 빌드를 실패시키지 않는다.
# 둘 다 브라우저에 노출되는 공개값이며 비밀값이 아니므로 Secret으로 전달하지 않는다.
ARG NEXT_PUBLIC_SITE_URL
ARG NEXT_PUBLIC_GA_MEASUREMENT_ID
ENV NEXT_PUBLIC_SITE_URL=${NEXT_PUBLIC_SITE_URL}
ENV NEXT_PUBLIC_GA_MEASUREMENT_ID=${NEXT_PUBLIC_GA_MEASUREMENT_ID}

# build arg가 비어 있으면 지도 컴포넌트가 SDK 오류 상태로 빌드된다.
# CI 워크플로뿐 아니라 Dockerfile 자체에서도 누락을 차단해 직접 docker build 시에도
# 키 없는 이미지가 만들어지지 않도록 한다.
RUN test -n "$NEXT_PUBLIC_KAKAO_MAPS_JS_KEY" \
    || (echo "ERROR: NEXT_PUBLIC_KAKAO_MAPS_JS_KEY build arg is required" >&2 && exit 1)

# package.json의 build는 `tsc --noEmit && next build`다. next.config.ts가
# Next 내장 TypeScript 단계를 끄고 있으므로 타입 검사는 이 tsc가 담당한다.
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:24.18.0-alpine@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd AS runtime
# 고정된 Node 이미지가 Alpine OpenSSL 보안 업데이트보다 먼저 만들어졌으므로,
# 최종 런타임 이미지에 수정된 패키지를 명시적으로 설치한다.
RUN apk add --no-cache libcrypto3=3.5.8-r0 libssl3=3.5.8-r0
WORKDIR /app

# robots.txt와 sitemap.xml은 standalone 서버 실행 시에도 사이트 URL을 읽는다.
# build stage에서만 선언하면 최종 이미지의 런타임 환경에는 값이 남지 않으므로
# 공개값인 두 ARG를 이 stage에도 선언해 빌드 시 전달된 설정을 기본값으로 보존한다.
# docker run의 환경 변수로 덮어쓸 수 있고, 값이 없으면 SEO는 fail-closed로 동작한다.
ARG NEXT_PUBLIC_SITE_URL
ARG NEXT_PUBLIC_GA_MEASUREMENT_ID
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV NEXT_PUBLIC_SITE_URL=${NEXT_PUBLIC_SITE_URL}
ENV NEXT_PUBLIC_GA_MEASUREMENT_ID=${NEXT_PUBLIC_GA_MEASUREMENT_ID}

# uid·gid를 고정해야 베이스 이미지가 바뀌어도 실행 사용자가 동일하다.
# 백엔드 이미지와 같은 1001을 사용해 두 컨테이너의 실행 사용자를 일치시킨다.
RUN addgroup -S -g 1001 masiton && adduser -S -u 1001 -G masiton masiton

# 런타임은 `node server.js`로 실행하므로 npm이 필요 없다.
# 베이스 이미지에 포함된 npm CLI가 자체 의존성(tar, undici, brace-expansion)을
# 함께 들여와 취약점 검사에 걸리고 실행에 쓰이지 않는 공격 표면을 만든다.
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx

# standalone 출력에는 server.js와 추려낸 의존성만 들어 있다.
# 정적 파일은 별도로 복사해야 한다.
COPY --from=build --chown=masiton:masiton /app/.next/standalone ./
COPY --from=build --chown=masiton:masiton /app/.next/static ./.next/static
COPY --from=build --chown=masiton:masiton /app/public ./public

USER masiton

# server.js는 PORT와 HOSTNAME 환경 변수를 읽는다.
# 0.0.0.0으로 바인딩해야 컨테이너 밖에서 접근할 수 있다.
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
EXPOSE 3000

ENTRYPOINT ["node", "server.js"]
