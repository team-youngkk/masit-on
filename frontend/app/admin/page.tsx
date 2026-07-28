'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'

export default function AdminHomePage() {
  const router = useRouter()
  useEffect(() => { router.replace('/admin/restaurants/new') }, [router])
  return null
}
