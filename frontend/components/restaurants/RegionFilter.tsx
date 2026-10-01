'use client'

import { useState } from 'react'

import { changeRegionSelection, regionSelection, type Province } from '@/lib/regions'
import { FilterSelect } from './FilterSelect'

type Props = {
  provinces: Province[]
  regionCode: string
  district: string
  disabled: boolean
  formId: string
  className: string
  controlClassName: string
  menuClassName: string
  optionClassName: string
  selectedOptionClassName: string
}

export function RegionFilter({ provinces, regionCode, district, disabled, ...selectProps }: Props) {
  const [selection, setSelection] = useState({ regionCode, district })
  const { province, child } = regionSelection(provinces, selection.regionCode, selection.district)
  const change = (code: string) => setSelection(changeRegionSelection(code))

  return <>
    {selection.regionCode ? <input type="hidden" name="regionCode" value={selection.regionCode} form={selectProps.formId} /> : null}
    {selection.district ? <input type="hidden" name="district" value={selection.district} form={selectProps.formId} /> : null}
    <FilterSelect
      {...selectProps}
      id="province"
      options={provinces.map(({ code, name }) => ({ value: code, label: name }))}
      value={province?.code ?? ''}
      placeholder="시·도 전체"
      disabled={disabled || provinces.length === 0}
      onValueChange={change}
    />
    <FilterSelect
      {...selectProps}
      key={province?.code ?? ''}
      id="municipality"
      options={province?.children.map(({ code, name }) => ({ value: code, label: name })) ?? []}
      value={child?.code ?? ''}
      placeholder={province && province.children.length === 0 ? '하위 지역 없음' : '시·군·구 전체'}
      disabled={disabled || !province?.children.length}
      onValueChange={(code) => change(code || province?.code || '')}
    />
  </>
}
