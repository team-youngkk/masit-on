import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  reactStrictMode: true,

  /*
   * 운영 이미지를 최소 구성으로 만들기 위해 standalone 출력을 사용한다.
   * `.next/standalone`에 server.js와 실제로 참조되는 의존성만 추려 담기므로
   * 런타임 스테이지에 node_modules 전체를 복사하지 않는다.
   * 정적 파일은 standalone에 포함되지 않아 Dockerfile이 `.next/static`을 따로 복사한다.
   */
  output: 'standalone',

  async rewrites() {
    const apiBaseUrl = process.env.API_BASE_URL ?? 'http://localhost:8080'

    return [
      {
        source: '/api/:path*',
        destination: `${apiBaseUrl}/api/:path*`,
      },
    ]
  },
}

export default nextConfig
