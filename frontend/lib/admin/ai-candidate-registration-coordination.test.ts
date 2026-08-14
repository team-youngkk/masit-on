import assert from 'node:assert/strict'
import test from 'node:test'

import {
  addressCandidates,
  buildVisitRelationshipPayload,
  channelUrlFromChannelId,
  placeSearchSeed,
  placeSearchSeedIdentity,
  prefillFromPlace,
  restaurantNameCandidates,
  visitEvidenceCandidates,
  isCurrentAsyncRequest,
} from './ai-candidate-registration-coordination.ts'
import type { AiCandidate } from './ai-video-extractions.ts'

const evidence = { type: 'UNKNOWN' as const }

const candidates: AiCandidate[] = [
  { field: 'restaurantName', value: '아코', confidence: 0.9, evidence },
  { field: 'restaurantName', value: '아코 본점', confidence: 0.6, evidence },
  { field: 'address', value: '서울 강동구 성내동 12-38', confidence: 0.8, evidence },
  { field: 'visitEvidence', value: '직접 방문해 먹었습니다', confidence: 0.7, evidence },
  { field: 'tag', tagType: 'MENU', confidence: 0.5, evidence },
]

test('field로 후보를 골라낸다. 같은 field가 여러 번 나와도 모두 포함한다', () => {
  assert.equal(restaurantNameCandidates(candidates).length, 2)
  assert.equal(addressCandidates(candidates).length, 1)
  assert.equal(visitEvidenceCandidates(candidates).length, 1)
})

test('장소 검색 힌트는 상호명을 trim하고 주소 힌트가 없으면 null로 둔다', () => {
  assert.deepEqual(placeSearchSeed(' 아코 ', ' 서울 강동구 성내동 12-38 '), { name: '아코', roadAddressHint: '서울 강동구 성내동 12-38' })
  assert.deepEqual(placeSearchSeed('아코', null), { name: '아코', roadAddressHint: null })
  assert.deepEqual(placeSearchSeed('아코', '   '), { name: '아코', roadAddressHint: null })
})

test('장소 검색 seed identity는 정규화된 상호명과 주소를 구분한다', () => {
  assert.equal(placeSearchSeedIdentity(placeSearchSeed(' 아코 ', ' 서울 ')), placeSearchSeedIdentity(placeSearchSeed('아코', '서울')))
  assert.notEqual(placeSearchSeedIdentity(placeSearchSeed('아코', '서울')), placeSearchSeedIdentity(placeSearchSeed('아코', '부산')))
})

test('전화번호가 없는 장소는 빈 문자열과 누락 표시로 프리필한다', () => {
  assert.deepEqual(
    prefillFromPlace({ kakaoPlaceUrl: 'https://place.map.kakao.com/example', roadAddress: '서울특별시 강동구 성내동 12-38', phoneNumber: null }),
    { kakaoPlaceUrl: 'https://place.map.kakao.com/example', roadAddress: '서울특별시 강동구 성내동 12-38', phoneNumber: '', phoneNumberMissing: true },
  )
})

test('전화번호가 있는 장소는 값과 누락 아님을 프리필한다', () => {
  assert.deepEqual(
    prefillFromPlace({ kakaoPlaceUrl: 'https://place.map.kakao.com/example', roadAddress: '서울특별시 강동구 성내동 12-38', phoneNumber: '02-000-0000' }),
    { kakaoPlaceUrl: 'https://place.map.kakao.com/example', roadAddress: '서울특별시 강동구 성내동 12-38', phoneNumber: '02-000-0000', phoneNumberMissing: false },
  )
})

test('장소 프리필 값의 앞뒤 공백을 제거한다', () => {
  assert.deepEqual(
    prefillFromPlace({ kakaoPlaceUrl: ' https://place.map.kakao.com/example ', roadAddress: ' 서울특별시 강동구 ', phoneNumber: ' 02-000-0000 ' }),
    { kakaoPlaceUrl: 'https://place.map.kakao.com/example', roadAddress: '서울특별시 강동구', phoneNumber: '02-000-0000', phoneNumberMissing: false },
  )
})

test('채널 URL은 channelId로 채널 URL을 구성한다', () => {
  assert.equal(channelUrlFromChannelId('UC_x5XG1OV2P6uZZ5FSM9Ttw'), 'https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw')
})

test('채널 ID와 방문 관계 ID의 앞뒤 공백을 제거한다', () => {
  assert.equal(channelUrlFromChannelId(' channel-id '), 'https://www.youtube.com/channel/channel-id')
  assert.deepEqual(buildVisitRelationshipPayload(' r-1 ', ' c-1 ', ' v-1 ', true), {
    restaurantId: 'r-1', creatorId: 'c-1', videoId: 'v-1', visitEvidenceConfirmed: true,
  })
})

test('visitEvidenceConfirmed는 관리자 확인이 true일 때만 true가 된다', () => {
  assert.deepEqual(buildVisitRelationshipPayload('r-1', 'c-1', 'v-1', true), {
    restaurantId: 'r-1', creatorId: 'c-1', videoId: 'v-1', visitEvidenceConfirmed: true,
  })
  assert.deepEqual(buildVisitRelationshipPayload('r-1', 'c-1', 'v-1', false), {
    restaurantId: 'r-1', creatorId: 'c-1', videoId: 'v-1', visitEvidenceConfirmed: false,
  })
})

test('입력이 바뀐 뒤 도착한 장소 검색 응답은 무시한다', () => {
  const request = { generation: 2, identity: 'session:["아코","서울"]' }
  assert.equal(isCurrentAsyncRequest(request, 3, 'session:["아코","서울"]'), false)
  assert.equal(isCurrentAsyncRequest(request, 2, 'session:["아코","부산"]'), false)
  assert.equal(isCurrentAsyncRequest(request, 2, request.identity), true)
})
