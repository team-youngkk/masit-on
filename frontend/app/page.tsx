import { redirect } from 'next/navigation'

/* ADR-WEB-003 6.2 화면 경로: `/`는 `/restaurants`로 이동한다. */
export default function HomePage() {
  redirect('/restaurants')
}
