import type { ReactNode } from 'react'

import { AdminQueryProvider } from '@/components/admin/AdminQueryProvider'
import styles from '@/components/admin/admin.module.css'

export default function AdminLayout({ children }: { children: ReactNode }) {
  return <AdminQueryProvider><div className={styles.layout} data-admin-frame="true">{children}</div></AdminQueryProvider>
}
