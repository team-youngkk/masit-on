import { PersonalRestaurantList } from '@/components/personal/PersonalRestaurantList'

type SearchParams = Record<string, string | string[] | undefined>

function valueOf(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value
}

function positivePage(value: string | undefined): number {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 1 ? parsed : 1
}

function allowedSize(value: string | undefined): number {
  const parsed = Number(value)
  return parsed === 10 || parsed === 50 ? parsed : 20
}

export default async function RecentRestaurantsPage({
  searchParams,
}: {
  searchParams: Promise<SearchParams>
}) {
  const params = await searchParams
  return (
    <PersonalRestaurantList
      kind="recent"
      page={positivePage(valueOf(params.page))}
      size={allowedSize(valueOf(params.size))}
    />
  )
}
