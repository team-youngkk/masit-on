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

# package.json의 build는 `tsc --noEmit && next build`다. next.config.ts가
# Next 내장 TypeScript 단계를 끄고 있으므로 타입 검사는 이 tsc가 담당한다.
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:24.18.0-alpine@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd AS runtime
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

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

USER masiton

# server.js는 PORT와 HOSTNAME 환경 변수를 읽는다.
# 0.0.0.0으로 바인딩해야 컨테이너 밖에서 접근할 수 있다.
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
EXPOSE 3000

ENTRYPOINT ["node", "server.js"]
