import { CollectionDetail } from '@/components/personal/CollectionDetail'
import {
  allowedCollectionPageSize,
  positiveCollectionPage,
} from '@/lib/member/collections-coordination'

type SearchParams = Record<string, string | string[] | undefined>

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

export default async function CollectionDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ collectionId: string }>
  searchParams: Promise<SearchParams>
}) {
  const [{ collectionId }, query] = await Promise.all([params, searchParams])
  return (
    <CollectionDetail
      collectionId={collectionId}
      page={positiveCollectionPage(first(query.page))}
      size={allowedCollectionPageSize(first(query.size))}
    />
  )
}
