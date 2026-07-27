import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  reactStrictMode: true,

  typescript: {
    /*
     * ADR-WEB-001이 고정한 Next.js 16.2.11과 TypeScript 7.0.2 조합에서
     * `next build`의 내장 TypeScript 단계가 동작하지 않는다.
     * TypeScript 7 패키지는 `main` 없이 `exports`만 노출하고 API가 재편되어
     * Next이 설치된 TypeScript를 탐지하지 못하고 재설치를 시도하다 중단된다.
     *   The "id" argument must be of type string. Received undefined
     *
     * 두 버전 모두 ADR 고정값이므로 버전을 바꾸는 대신 내장 단계만 끄고,
     * 타입 검사는 `npm run typecheck`(tsc --noEmit)로 수행한다.
     * TypeScript 7.0.2에서 tsc 자체는 정상 동작한다.
     *
     * ADR이 정한 Node 24.18.0과 npm 11.16.0에서도 같은 오류를 재현했으므로
     * 런타임 버전 문제가 아니다. Next이 TypeScript 7을 지원하면 이 우회를 제거한다.
     */
    ignoreBuildErrors: true,
  },
}

export default nextConfig
