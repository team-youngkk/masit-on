const creatorListAssert = require('node:assert/strict')
const creatorListTest = require('node:test')
const {
  creatorListPageParamName,
  creatorListRequestPath,
  loadCreatorListPage,
  nextCreatorListSearch,
  withCreatorListPage,
} = require('./creator-list-navigation.ts')

const CREATOR_ID = '11111111-1111-4111-8111-111111111111'
const FALLBACK = '목록을 불러오지 못했습니다.'

type FakeResponse = {
  ok: boolean
  status: number
  json: () => Promise<unknown>
}

/* 호출된 경로를 순서대로 기록하는 fetch 대역. 호출 횟수 자체를 단정하기 위해 쓴다. */
function createFetchRecorder(handler: (path: string) => FakeResponse) {
  const paths: string[] = []
  const fetchImpl = (path: string) => {
    paths.push(path)
    return Promise.resolve(handler(path))
  }
  return { paths, fetchImpl }
}

function okResponse(body: unknown): FakeResponse {
  return { ok: true, status: 200, json: () => Promise.resolve(body) }
}

function pageBody(number: number) {
  return {
    items: [],
    page: { number, size: 20, totalElements: 30, totalPages: 2, hasNext: number < 2 },
  }
}

creatorListTest('방문 맛집 페이지 이동은 방문 맛집 endpoint만 한 번 호출한다', async () => {
  const { paths, fetchImpl } = createFetchRecorder(() => okResponse(pageBody(2)))

  const result = await loadCreatorListPage(
    CREATOR_ID,
    'restaurants',
    2,
    FALLBACK,
    fetchImpl,
  )

  creatorListAssert.equal(result.ok, true)
  creatorListAssert.equal(paths.length, 1)
  creatorListAssert.ok(paths[0].includes(`/api/creators/${CREATOR_ID}/restaurants`))
  creatorListAssert.equal(
    paths.filter((path) => path.includes('/videos')).length,
    0,
  )
})

creatorListTest('근거 영상 페이지 이동은 근거 영상 endpoint만 한 번 호출한다', async () => {
  const { paths, fetchImpl } = createFetchRecorder(() => okResponse(pageBody(3)))

  await loadCreatorListPage(CREATOR_ID, 'videos', 3, FALLBACK, fetchImpl)

  creatorListAssert.equal(paths.length, 1)
  creatorListAssert.ok(paths[0].includes(`/api/creators/${CREATOR_ID}/videos`))
  creatorListAssert.equal(
    paths.filter((path) => path.includes('/restaurants')).length,
    0,
  )
})

creatorListTest('한 목록을 여러 번 이동해도 상대 목록 endpoint는 호출되지 않는다', async () => {
  const { paths, fetchImpl } = createFetchRecorder(() => okResponse(pageBody(2)))

  await loadCreatorListPage(CREATOR_ID, 'restaurants', 2, FALLBACK, fetchImpl)
  await loadCreatorListPage(CREATOR_ID, 'restaurants', 3, FALLBACK, fetchImpl)
  await loadCreatorListPage(CREATOR_ID, 'restaurants', 2, FALLBACK, fetchImpl)

  creatorListAssert.equal(paths.length, 3)
  creatorListAssert.equal(
    paths.every((path) => path.includes('/restaurants')),
    true,
  )
})

creatorListTest('재시도는 같은 목록의 현재 페이지만 다시 요청한다', async () => {
  let attempt = 0
  const { paths, fetchImpl } = createFetchRecorder(() => {
    attempt += 1
    return attempt === 1
      ? { ok: false, status: 500, json: () => Promise.resolve({ traceId: 'trace-1' }) }
      : okResponse(pageBody(2))
  })

  const failed = await loadCreatorListPage(
    CREATOR_ID,
    'restaurants',
    2,
    FALLBACK,
    fetchImpl,
  )
  creatorListAssert.equal(failed.ok, false)
  creatorListAssert.equal(failed.traceId, 'trace-1')

  const retried = await loadCreatorListPage(
    CREATOR_ID,
    'restaurants',
    2,
    FALLBACK,
    fetchImpl,
  )
  creatorListAssert.equal(retried.ok, true)

  creatorListAssert.equal(paths.length, 2)
  creatorListAssert.equal(paths[0], paths[1])
  creatorListAssert.equal(
    paths.filter((path) => path.includes('/videos')).length,
    0,
  )
})

creatorListTest('한 목록의 페이지를 바꿔도 상대 목록의 페이지 상태는 유지된다', () => {
  const current = { restaurantsPage: 1, videosPage: 4 }

  const movedRestaurants = withCreatorListPage(current, 'restaurants', 2)
  creatorListAssert.deepEqual(movedRestaurants, { restaurantsPage: 2, videosPage: 4 })

  const movedVideos = withCreatorListPage(movedRestaurants, 'videos', 5)
  creatorListAssert.deepEqual(movedVideos, { restaurantsPage: 2, videosPage: 5 })
})

creatorListTest('주소 갱신은 이동한 목록의 페이지 파라미터만 바꾼다', () => {
  const search = nextCreatorListSearch('restaurantsPage=1&videosPage=4', 'videos', 5)
  const params = new URLSearchParams(search)

  creatorListAssert.equal(params.get('restaurantsPage'), '1')
  creatorListAssert.equal(params.get('videosPage'), '5')
})

creatorListTest('목록 종류별 페이지 파라미터 이름과 요청 경로가 서로 섞이지 않는다', () => {
  creatorListAssert.equal(creatorListPageParamName('restaurants'), 'restaurantsPage')
  creatorListAssert.equal(creatorListPageParamName('videos'), 'videosPage')

  const restaurantsPath = creatorListRequestPath(CREATOR_ID, 'restaurants', 1)
  const videosPath = creatorListRequestPath(CREATOR_ID, 'videos', 1)

  creatorListAssert.equal(restaurantsPath.includes('/videos'), false)
  creatorListAssert.equal(videosPath.includes('/restaurants'), false)
  creatorListAssert.ok(restaurantsPath.endsWith('?page=1&size=20'))
})

creatorListTest('네트워크 실패는 예외 대신 이 목록만의 오류 상태로 돌려준다', async () => {
  const fetchImpl = () => Promise.reject(new Error('network down'))

  const result = await loadCreatorListPage(
    CREATOR_ID,
    'videos',
    1,
    FALLBACK,
    fetchImpl,
  )

  creatorListAssert.equal(result.ok, false)
  creatorListAssert.equal(result.message, FALLBACK)
})
