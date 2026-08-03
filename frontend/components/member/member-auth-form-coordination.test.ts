const memberAuthFormAssert = require('node:assert/strict')
const memberAuthFormTest = require('node:test')
const {
  acceptMemberRegistration,
  resendAcceptedMemberRegistration,
} = require('./member-auth-form-coordination.ts')

memberAuthFormTest('가입 접수 후 입력값이 바뀌어도 재발송은 접수한 이메일을 사용한다', async () => {
  let currentEmail = 'registered@example.com'
  const registration = acceptMemberRegistration(currentEmail)
  const resentEmails: string[] = []

  currentEmail = 'changed@example.com'
  memberAuthFormAssert.deepEqual(registration, {
    email: 'registered@example.com',
    emailReadOnly: true,
  })
  await resendAcceptedMemberRegistration(
    registration,
    async (email: string) => {
      resentEmails.push(email)
    },
  )

  memberAuthFormAssert.equal(currentEmail, 'changed@example.com')
  memberAuthFormAssert.deepEqual(resentEmails, ['registered@example.com'])
})
