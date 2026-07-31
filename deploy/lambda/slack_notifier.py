"""CloudWatch 알람을 Slack Incoming Webhook으로 전달한다 — M2-10.

SNS 토픽 masiton-alerts가 이 함수를 호출한다. ADR-OBS-001이 알림 채널을 Slack
Webhook 하나로 정했고 운영 이메일 수신 체계가 없다.

Webhook URL은 코드에 넣지 않고 Parameter Store SecureString에서 읽는다
(NFR-SECURITY-003). URL 자체가 그 채널에 글을 쓸 수 있는 자격 증명이다.

표준 라이브러리만 쓴다. 의존성을 추가하면 배포 패키지를 만들어야 하고 M2 범위에서
그 복잡도를 늘릴 이유가 없다.
"""

import datetime
import json
import os
import urllib.error
import urllib.request

import boto3

# CloudWatch 알람의 StateChangeTime은 항상 UTC로 온다. 운영자가 9시간을 환산해
# 읽지 않도록 KST로 바꿔 보낸다. 오프셋은 명시한다(date-time-contract).
# zoneinfo 대신 고정 오프셋을 쓰는 이유는 KST에 일광 절약 시간이 없고 Lambda
# 런타임에 tzdata가 없을 수 있기 때문이다.
KST = datetime.timezone(datetime.timedelta(hours=9), "KST")

WEBHOOK_PARAMETER = os.environ.get("WEBHOOK_PARAMETER", "/masiton/alerts/slack-webhook-url")
_ssm = boto3.client("ssm")
_webhook_cache: dict[str, str] = {}

# 알람 상태별 표시. Slack 이모지는 상태를 한눈에 구분하려는 것이다.
STATE_MARK = {
    "ALARM": ":red_circle: ALARM",
    "OK": ":large_green_circle: OK",
    "INSUFFICIENT_DATA": ":white_circle: INSUFFICIENT_DATA",
}


def _webhook_url() -> str:
    """Webhook URL을 읽어 컨테이너 수명 동안 재사용한다."""
    if "url" not in _webhook_cache:
        response = _ssm.get_parameter(Name=WEBHOOK_PARAMETER, WithDecryption=True)
        _webhook_cache["url"] = response["Parameter"]["Value"].strip()
    return _webhook_cache["url"]


def _to_kst(value: str) -> str:
    """CloudWatch의 UTC 표기를 KST로 바꾼다. 해석하지 못하면 원문을 그대로 둔다."""
    if not value:
        return ""
    # `2026-07-30T06:10:00.000+0000`과 `...Z` 두 형태가 온다.
    normalized = value.strip().replace("Z", "+00:00")
    if len(normalized) >= 5 and normalized[-5] in "+-" and ":" not in normalized[-5:]:
        normalized = f"{normalized[:-2]}:{normalized[-2:]}"
    try:
        parsed = datetime.datetime.fromisoformat(normalized)
    except ValueError:
        return value
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=datetime.timezone.utc)
    return parsed.astimezone(KST).strftime("%Y-%m-%d %H:%M:%S +09:00")


def _format(message: dict) -> str:
    """CloudWatch 알람 메시지를 사람이 읽는 한 줄로 만든다."""
    name = message.get("AlarmName", "(이름 없음)")
    state = message.get("NewStateValue", "UNKNOWN")
    reason = message.get("NewStateReason", "")
    description = message.get("AlarmDescription") or ""
    region = message.get("Region", "")
    timestamp = _to_kst(message.get("StateChangeTime", ""))

    lines = [f"{STATE_MARK.get(state, state)}  *{name}*"]
    if description:
        lines.append(description)
    if reason:
        lines.append(f"> {reason}")
    if region or timestamp:
        lines.append(f"_{region} {timestamp}_".strip())
    return "\n".join(lines)


def handler(event, _context):
    """SNS 레코드를 순회해 각각 Slack에 보낸다."""
    url = _webhook_url()
    sent = 0

    for record in event.get("Records", []):
        raw = record.get("Sns", {}).get("Message", "")
        try:
            message = json.loads(raw)
        except json.JSONDecodeError:
            # 알람이 아닌 임의 문자열(시험 발행 등)도 그대로 전달한다.
            message = {"AlarmName": record["Sns"].get("Subject") or "(제목 없음)",
                       "NewStateValue": "OK",
                       "NewStateReason": raw}

        payload = json.dumps({"text": _format(message)}).encode("utf-8")
        request = urllib.request.Request(
            url,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                body = response.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as error:
            # 본문에 실패 이유가 들어온다. URL 자체는 로그에 남기지 않는다.
            detail = error.read().decode("utf-8", "replace")
            raise RuntimeError(f"Slack 전송 실패 {error.code}: {detail}") from error

        if body != "ok":
            raise RuntimeError(f"Slack이 ok를 반환하지 않았다: {body}")
        sent += 1

    return {"sent": sent}
