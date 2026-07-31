type LockManagerLike = {
  request<T>(
    name: string,
    options: { mode: 'exclusive' },
    callback: () => Promise<T>,
  ): Promise<T>
}

type StorageLike = Pick<Storage, 'length' | 'key' | 'getItem' | 'setItem' | 'removeItem'>

type BakeryEntry = {
  id: string
  choosing: boolean
  number: number
  expiresAt: number
}

type MemberAuthCookieLockOptions = {
  lockManager?: () => LockManagerLike | null
  storage?: () => StorageLike | null
  randomId?: () => string
  delay?: (milliseconds: number) => Promise<void>
  now?: () => number
  maxWaitMilliseconds?: number
}

const MEMBER_AUTH_COOKIE_LOCK = 'masit-on:member-auth-cookie'
const FALLBACK_ENTRY_PREFIX = `${MEMBER_AUTH_COOKIE_LOCK}:entry:`
const FALLBACK_POLL_MILLISECONDS = 20
const FALLBACK_MAX_WAIT_MILLISECONDS = 10_000
const FALLBACK_LEASE_MILLISECONDS = 300_000
const FALLBACK_HEARTBEAT_MILLISECONDS = 30_000
const FALLBACK_SNAPSHOT_MAX_ATTEMPTS = 4

function browserLockManager(): LockManagerLike | null {
  return typeof navigator === 'undefined' ? null : navigator.locks ?? null
}

function browserStorage(): StorageLike | null {
  if (typeof window === 'undefined') return null
  try {
    return window.localStorage
  } catch {
    return null
  }
}

function browserRandomId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

function readEntryKeys(storage: StorageLike): string[] {
  const keySet = new Set<string>()
  for (let index = storage.length - 1; index >= 0; index -= 1) {
    const key = storage.key(index)
    if (key?.startsWith(FALLBACK_ENTRY_PREFIX)) keySet.add(key)
  }
  return [...keySet].sort()
}

function readEntries(storage: StorageLike, currentTime: number): BakeryEntry[] {
  let previousKeys: string[] | null = null
  let keys: string[] | null = null

  // Storage.key() 순서는 다른 탭의 삽입·삭제 중 바뀔 수 있으므로 안정된 두 관측만 사용한다.
  for (let attempt = 0; attempt < FALLBACK_SNAPSHOT_MAX_ATTEMPTS; attempt += 1) {
    const nextKeys = readEntryKeys(storage)
    if (
      previousKeys !== null &&
      nextKeys.length === previousKeys.length &&
      nextKeys.every((key, index) => key === previousKeys?.[index])
    ) {
      keys = nextKeys
      break
    }
    previousKeys = nextKeys
  }

  if (!keys) {
    throw new Error('Member authentication coordination is unstable')
  }

  const entries: BakeryEntry[] = []
  for (const key of keys) {
    const rawEntry = storage.getItem(key)
    if (!rawEntry) continue

    let entry: Partial<BakeryEntry>
    try {
      entry = JSON.parse(rawEntry) as Partial<BakeryEntry>
    } catch {
      storage.removeItem(key)
      continue
    }
    if (
      typeof entry.id !== 'string' ||
      typeof entry.choosing !== 'boolean' ||
      !Number.isSafeInteger(entry.number) ||
      (entry.number ?? -1) < 0 ||
      !Number.isSafeInteger(entry.expiresAt) ||
      entry.expiresAt !== undefined && entry.expiresAt <= currentTime ||
      key !== `${FALLBACK_ENTRY_PREFIX}${entry.id}`
    ) {
      storage.removeItem(key)
      continue
    }
    entries.push(entry as BakeryEntry)
  }
  return entries
}

function writeEntry(storage: StorageLike, entry: BakeryEntry): void {
  storage.setItem(`${FALLBACK_ENTRY_PREFIX}${entry.id}`, JSON.stringify(entry))
}

function hasPriority(left: BakeryEntry, right: BakeryEntry): boolean {
  if (left.number !== right.number) return left.number < right.number
  return left.id < right.id
}

export function createMemberAuthCookieLock(
  options: MemberAuthCookieLockOptions = {},
): <T>(task: () => Promise<T>) => Promise<T> {
  const getLockManager = options.lockManager ?? browserLockManager
  const getStorage = options.storage ?? browserStorage
  const randomId = options.randomId ?? browserRandomId
  const wait = options.delay ?? delay
  const now = options.now ?? Date.now
  const maxWaitMilliseconds =
    options.maxWaitMilliseconds ?? FALLBACK_MAX_WAIT_MILLISECONDS

  return async function withMemberAuthCookieLock<T>(
    task: () => Promise<T>,
  ): Promise<T> {
    const lockManager = getLockManager()
    if (lockManager) {
      return lockManager.request(
        MEMBER_AUTH_COOKIE_LOCK,
        { mode: 'exclusive' },
        task,
      )
    }

    const storage = getStorage()
    if (!storage) {
      throw new Error('Member authentication coordination is unavailable')
    }

    const id = randomId()
    const key = `${FALLBACK_ENTRY_PREFIX}${id}`
    const startedAt = now()
    let ownEntry: BakeryEntry = {
      id,
      choosing: true,
      number: 0,
      expiresAt: now() + FALLBACK_LEASE_MILLISECONDS,
    }
    let heartbeat: ReturnType<typeof setInterval> | null = null

    try {
      writeEntry(storage, ownEntry)
      const highestNumber = readEntries(storage, now()).reduce(
        (highest, entry) => Math.max(highest, entry.number),
        0,
      )
      ownEntry = {
        id,
        choosing: false,
        number: highestNumber + 1,
        expiresAt: now() + FALLBACK_LEASE_MILLISECONDS,
      }
      writeEntry(storage, ownEntry)
      heartbeat = setInterval(() => {
        ownEntry = {
          ...ownEntry,
          expiresAt: now() + FALLBACK_LEASE_MILLISECONDS,
        }
        writeEntry(storage, ownEntry)
      }, FALLBACK_HEARTBEAT_MILLISECONDS)

      while (true) {
        const entries = readEntries(storage, now())
        const predecessor = entries.some(
          (entry) =>
            entry.id !== id &&
            (entry.choosing || hasPriority(entry, ownEntry)),
        )
        if (!predecessor) {
          return await task()
        }
        if (now() - startedAt >= maxWaitMilliseconds) {
          throw new Error('Member authentication coordination timed out')
        }
        await wait(FALLBACK_POLL_MILLISECONDS)
      }
    } finally {
      if (heartbeat) clearInterval(heartbeat)
      storage.removeItem(key)
    }
  }
}

export const withMemberAuthCookieLock = createMemberAuthCookieLock()
