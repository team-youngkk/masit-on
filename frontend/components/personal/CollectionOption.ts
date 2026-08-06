import { createElement } from 'react'

import {
  collectionOptionStatusLabel,
  isCollectionOptionDisabled,
} from '../../lib/member/collections-coordination.ts'
import type { CollectionOption as CollectionOptionValue } from '../../lib/member/collections.ts'

export function CollectionOption({ option }: { option: CollectionOptionValue }) {
  const status = collectionOptionStatusLabel(option.additionStatus)
  return createElement(
    'option',
    {
      value: option.collectionId,
      disabled: isCollectionOptionDisabled(option.additionStatus),
    },
    `${option.name} · 공개·활성 맛집 ${option.restaurantCount}곳 · ${status}`,
  )
}
