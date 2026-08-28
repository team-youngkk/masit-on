run "renders_shell_parameter_expansions" {
  command = plan

  module {
    source = "./tests/template-render"
  }

  assert {
    condition     = strcontains(output.rendered_user_data, "DATA_VOLUME_ID=vol-0123abcd")
    error_message = "templatefile()이 fixture volume ID를 렌더링하지 않았다."
  }

  assert {
    condition     = strcontains(output.rendered_user_data, "DATA_VOLUME_SERIAL=\"$${DATA_VOLUME_ID//-/}\"")
    error_message = "렌더링 결과에 Bash volume serial 확장이 남아 있지 않다."
  }

  assert {
    condition     = strcontains(output.rendered_user_data, "nvme-Amazon_Elastic_Block_Store_$${DATA_VOLUME_SERIAL}")
    error_message = "렌더링 결과에 Bash serial 변수가 남아 있지 않다."
  }

  assert {
    condition     = strcontains(output.rendered_user_data, "Environment=REDIS_PASSWORD_BUCKET=$BUCKET") && strcontains(output.rendered_user_data, "Environment=REDIS_PASSWORD_OBJECT_KEY=$PASSWORD_OBJECT_KEY")
    error_message = "Redis secret 객체 위치가 user-data drop-in에 전달되지 않았다."
  }
}
