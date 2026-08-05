'use client'

import { authenticatedMemberFetch } from '@/lib/member/auth'
import {
  NotificationContractError,
  formatUnreadBadge,
  notificationErrorMessage,
  parseNotificationError,
} from './notifications-coordination'

export { formatUnreadBadge, notificationErrorMessage, parseNotificationError }

export type NotificationType = 'SUBMISSION_STATUS_CHANGED' | 'REPORT_STATUS_CHANGED'
export type NotificationRequestType = 'SUBMISSION' | 'REPORT'
export type NotificationStatus = 'IN_REVIEW' | 'ACCEPTED' | 'REJECTED' | 'COMPLETED'

export type NotificationItem = {
  notificationId: string
  type: NotificationType
  requestType: NotificationRequestType
  requestId: string
  status: NotificationStatus
  title: string
  message: string
  read: boolean
  readAt: string | null
  createdAt: string
}

export type NotificationPage = {
  items: NotificationItem[]
  unreadCount: number
  page: {
    number: number
    size: number
    totalElements: number
    totalPages: number
    hasNext: boolean
  }
}

export type ReadState = {
  notificationId: string
  read: boolean
  readAt: string | null
}

export type ReadAllState = {
  updatedCount: number
  unreadCount: number
  readAt: string | null
}

export type ContractError = NotificationContractError

async function json<T>(response: Response): Promise<T> {
  if (!response.ok) throw response
  return (await response.json()) as T
}

export async function getNotifications(page = 1, size = 20): Promise<NotificationPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return json<NotificationPage>(await authenticatedMemberFetch(
    `/api/me/notifications?${query.toString()}`,
    { cache: 'no-store' },
  ))
}

export async function getUnreadCount(): Promise<number> {
  const body = await json<{ unreadCount: number }>(await authenticatedMemberFetch(
    '/api/me/notifications/unread-count',
    { cache: 'no-store' },
  ))
  return body.unreadCount
}

export async function markNotificationRead(notificationId: string): Promise<ReadState> {
  return json<ReadState>(await authenticatedMemberFetch(
    `/api/me/notifications/${encodeURIComponent(notificationId)}/read`,
    { method: 'PUT' },
  ))
}

export async function markAllNotificationsRead(): Promise<ReadAllState> {
  return json<ReadAllState>(await authenticatedMemberFetch(
    '/api/me/notifications/read-all',
    { method: 'PUT' },
  ))
}
