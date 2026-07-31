import type { ReactNode } from 'react'

import { AdminQueryProvider } from '@/components/admin/AdminQueryProvider'

export default function AdminLayout({ children }: { children: ReactNode }) {
  return <AdminQueryProvider>{children}</AdminQueryProvider>
}
