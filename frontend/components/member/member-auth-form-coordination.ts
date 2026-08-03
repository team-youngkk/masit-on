export type AcceptedMemberRegistration = Readonly<{
  email: string
  emailReadOnly: true
}>

export function acceptMemberRegistration(email: string): AcceptedMemberRegistration {
  return { email, emailReadOnly: true }
}

export async function resendAcceptedMemberRegistration(
  registration: AcceptedMemberRegistration,
  resend: (email: string) => Promise<void>,
): Promise<void> {
  await resend(registration.email)
}
