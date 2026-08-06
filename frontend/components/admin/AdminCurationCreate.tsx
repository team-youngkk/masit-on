'use client'

import { useRouter } from 'next/navigation'
import { useRef, useState } from 'react'

import { Button } from '@/components/ui/Button'
import { createAdminCuration, curationMessageFor } from '@/lib/admin/curations'
import { IdempotencyAttempt, idempotencyAttempt, validateCurationText } from '@/lib/admin/curations-coordination'

import styles from './AdminCurationScreen.module.css'

export function AdminCurationCreate() {
  const router = useRouter()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const attempt = useRef<IdempotencyAttempt | null>(null)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    const errors = validateCurationText(title, description)
    if (errors.length) { setNotice(errors.join(' ')); return }
    setBusy(true); setNotice('초안을 만들고 있습니다.')
    try {
      const fingerprint = `${title.trim()}\n${description.trim()}`
      attempt.current = idempotencyAttempt(attempt.current, fingerprint, () => crypto.randomUUID())
      const created = await createAdminCuration(title, description, attempt.current.key)
      router.replace(`/admin/curations/${encodeURIComponent(created.curationId)}`)
    } catch (reason) { setNotice(curationMessageFor(reason)); setBusy(false) }
  }

  return <form className={styles.form} onSubmit={(event) => void submit(event)}>
    <p className={styles.hint}>새 큐레이션은 항상 초안으로 생성됩니다. 같은 제출의 중복 생성은 멱등 키로 방지합니다.</p>
    <label>제목 <span>1~100자</span><input required maxLength={100} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
    <label>설명 <span>0~1000자</span><textarea rows={6} maxLength={1000} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
    <Button type="submit" disabled={busy}>초안 만들기</Button>
    {notice ? <p className={busy ? styles.notice : styles.error} role={busy ? 'status' : 'alert'}>{notice}</p> : null}
  </form>
}
