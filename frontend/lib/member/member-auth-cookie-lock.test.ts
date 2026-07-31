const memberLockAssert = require('node:assert/strict')
const memberLockTest = require('node:test')
const { createMemberAuthCookieLock } = require('./member-auth-cookie-lock.ts')

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>()

  get length(): number {
    return this.values.size
  }

  clear(): void {
    this.values.clear()
  }

  getItem(key: string): string | null {
    return this.values.get(key) ?? null
  }

  key(index: number): string | null {
    return [...this.values.keys()][index] ?? null
  }

  removeItem(key: string): void {
    this.values.delete(key)
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value)
  }
}

function deferred(): { promise: Promise<void>; resolve: () => void } {
  let resolve!: () => void
  const promise = new Promise<void>((next) => {
    resolve = next
  })
  return { promise, resolve }
}

memberLockTest('Web Locks 미지원 탭도 인증 쿠키 작업을 직렬화한다', async () => {
  const storage = new MemoryStorage()
  const releaseFirst = deferred()
  const firstStarted = deferred()
  const events: string[] = []
  const commonOptions = {
    lockManager: () => null,
    storage: () => storage,
    delay: () => new Promise<void>((resolve) => setTimeout(resolve, 0)),
  }
  const firstLock = createMemberAuthCookieLock({
    ...commonOptions,
    randomId: () => 'tab-a',
  })
  const secondLock = createMemberAuthCookieLock({
    ...commonOptions,
    randomId: () => 'tab-b',
  })

  const first = firstLock(async () => {
    events.push('first:start')
    firstStarted.resolve()
    await releaseFirst.promise
    events.push('first:end')
  })
  await firstStarted.promise
  const second = secondLock(async () => {
    events.push('second:start')
  })

  await new Promise((resolve) => setTimeout(resolve, 5))
  memberLockAssert.deepEqual(events, ['first:start'])

  releaseFirst.resolve()
  await Promise.all([first, second])
  memberLockAssert.deepEqual(events, ['first:start', 'first:end', 'second:start'])
  memberLockAssert.equal(storage.length, 0)
})

memberLockTest('공유 잠금과 저장소가 모두 없으면 인증 쿠키 작업을 차단한다', async () => {
  const lock = createMemberAuthCookieLock({
    lockManager: () => null,
    storage: () => null,
  })

  await memberLockAssert.rejects(
    () => lock(async () => undefined),
    /coordination is unavailable/,
  )
})

memberLockTest('만료되거나 손상된 fallback 항목을 제거하고 잠금을 복구한다', async () => {
  const storage = new MemoryStorage()
  storage.setItem(
    'masit-on:member-auth-cookie:entry:stale',
    JSON.stringify({
      id: 'stale',
      choosing: false,
      number: 1,
      expiresAt: 999,
    }),
  )
  storage.setItem('masit-on:member-auth-cookie:entry:malformed', '{')
  const lock = createMemberAuthCookieLock({
    lockManager: () => null,
    storage: () => storage,
    randomId: () => 'current',
    now: () => 1_000,
  })
  let executed = false

  await lock(async () => {
    executed = true
  })

  memberLockAssert.equal(executed, true)
  memberLockAssert.equal(storage.length, 0)
})
