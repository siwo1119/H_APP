package com.cardoctor.advisor.domain.detect

import com.cardoctor.advisor.domain.model.BaseSeverity
import com.cardoctor.advisor.domain.model.CarPart
import com.cardoctor.advisor.domain.model.PartCategory

/**
 * 경고등(telltale) → 진단 항목 매핑표.
 *
 * Pleos Vehicle SDK 2.3.2 의 `Safety.getTelltalesInfo()` 가 돌려주는 TelltaleType 중,
 * **정비 판단에 의미가 있는 것만** 추렸다. 방향지시등·하이빔 표시등처럼 단순 상태 표시등은
 * 고장이 아니므로 의도적으로 제외한다 (제외 목록은 [INFORMATIONAL] 참고).
 *
 * 기본 심각도는 기획안 정의(1~3)를 따른다. CRITICAL 은 환경과 무관하게 최소 3등급으로 올라간다.
 */
object TelltaleCatalog {

    data class Spec(
        val lampName: String,
        val title: String,
        val part: CarPart,
        val category: PartCategory,
        val severity: BaseSeverity,
    )

    /** 고장이 아닌 단순 표시등 — 점등되어도 진단 대상이 아니다. */
    val INFORMATIONAL: Set<String> = setOf(
        "TURN_SIGNAL_LEFT", "TURN_SIGNAL_RIGHT", "HEADLAMP_HIGH", "HEADLAMP_LOW",
        "POSITION_LAMP", "FRONT_FOG_LAMP", "REAR_FOG_LAMP", "AUTO_HIGH_BEAM",
        "AUTO_HOLD", "HEV_MODE", "ISG", "XEV_READY", "I_PEDAL", "AWD_LOCK",
        "EV_CHARGING_CONNECTOR", "PASSENGER_AIRBAG_ON", "PREHEAT", "REV", "DBC",
        "ESC_OFF", "SEAT_BELT", "DOOR_OPEN", "TRUNK_OPEN",
    )

    private val SPECS: Map<String, Spec> = buildMap {
        // ── 안전 핵심 (CRITICAL) — 환경과 무관하게 최소 3등급 ──────────────
        put(
            "ENGINE_TEMPERATURE",
            Spec("엔진 과열", "냉각 계통 과열", CarPart.ENGINE, PartCategory.POWERTRAIN, BaseSeverity.CRITICAL)
        )
        put(
            "ENGINE_OIL_PRESSURE",
            Spec("엔진 오일 압력", "엔진 오일 압력 이상", CarPart.ENGINE, PartCategory.POWERTRAIN, BaseSeverity.CRITICAL)
        )
        put(
            "ENGINE",
            Spec("엔진 점검", "엔진 계통 이상", CarPart.ENGINE, PartCategory.POWERTRAIN, BaseSeverity.CRITICAL)
        )
        put(
            "AIRBAG",
            Spec("에어백", "에어백 시스템 이상", CarPart.AIRBAG, PartCategory.SAFETY, BaseSeverity.CRITICAL)
        )
        put(
            "EV_SYSTEM",
            Spec("EV 시스템", "구동 시스템 이상", CarPart.BATTERY, PartCategory.ENERGY, BaseSeverity.CRITICAL)
        )
        put(
            "HEV_SYSTEM",
            Spec("하이브리드 시스템", "구동 시스템 이상", CarPart.BATTERY, PartCategory.ENERGY, BaseSeverity.CRITICAL)
        )
        put(
            "FCEV_SYSTEM",
            Spec("수소 연료전지 시스템", "구동 시스템 이상", CarPart.BATTERY, PartCategory.ENERGY, BaseSeverity.CRITICAL)
        )
        put(
            "EREV_SYSTEM",
            Spec("주행거리 연장 시스템", "구동 시스템 이상", CarPart.BATTERY, PartCategory.ENERGY, BaseSeverity.CRITICAL)
        )
        put(
            "MASTER_WARNING",
            Spec("종합 경고", "차량 종합 경고", CarPart.BODY, PartCategory.SAFETY, BaseSeverity.CRITICAL)
        )
        put(
            "POWER_DOWN",
            Spec("전원 차단", "전원 차단 경고", CarPart.BATTERY, PartCategory.ENERGY, BaseSeverity.CRITICAL)
        )
        put(
            "H2_LEAK",
            Spec("수소 누출", "수소 누출 감지", CarPart.BODY, PartCategory.SAFETY, BaseSeverity.CRITICAL)
        )
        put(
            "EMERGENCY_CALL",
            Spec("긴급 통화", "긴급 상황 감지", CarPart.BODY, PartCategory.SAFETY, BaseSeverity.CRITICAL)
        )

        // ── 주행 안전 보조 (MEDIUM) — 환경 가중치로 상향될 수 있다 ──────────
        put(
            "ABS",
            Spec("ABS", "ABS 제동 보조 이상", CarPart.BRAKE, PartCategory.BRAKING, BaseSeverity.MEDIUM)
        )
        put(
            "ESC",
            Spec("차체 자세 제어", "차체 자세 제어 장치 이상", CarPart.BRAKE, PartCategory.BRAKING, BaseSeverity.MEDIUM)
        )
        put(
            "RBS",
            Spec("회생 제동", "회생 제동 시스템 이상", CarPart.BRAKE, PartCategory.BRAKING, BaseSeverity.MEDIUM)
        )
        put(
            "MDPS",
            Spec("전동식 조향", "전동식 조향 장치 이상", CarPart.STEERING, PartCategory.STEERING, BaseSeverity.MEDIUM)
        )
        put(
            "TPMS",
            Spec("타이어 공기압", "타이어 공기압 경고", CarPart.TIRE_FL, PartCategory.TIRE, BaseSeverity.MEDIUM)
        )
        put(
            "PARKING_BRAKE",
            Spec("주차 브레이크", "주차 브레이크 경고", CarPart.BRAKE, PartCategory.BRAKING, BaseSeverity.MEDIUM)
        )
        put(
            "ELECTRONIC_PARKING_BRAKE",
            Spec("전자식 주차 브레이크", "전자식 주차 브레이크 이상", CarPart.BRAKE, PartCategory.BRAKING, BaseSeverity.MEDIUM)
        )
        put(
            "LOW_VOLT_BATTERY",
            Spec("보조 배터리", "12V 보조 배터리 전압 저하", CarPart.BATTERY, PartCategory.ENERGY, BaseSeverity.MEDIUM)
        )
        put(
            "ICY_ROAD",
            Spec("결빙 주의", "노면 결빙 가능 온도", CarPart.TIRE_FL, PartCategory.TIRE, BaseSeverity.MEDIUM)
        )
        put(
            "IMMOBILIZER",
            Spec("이모빌라이저", "도난 방지 장치 이상", CarPart.BODY, PartCategory.BODY, BaseSeverity.MEDIUM)
        )
        put(
            "AFS",
            Spec("적응형 전조등", "적응형 전조등 이상", CarPart.HEADLAMP_LEFT, PartCategory.LIGHTING, BaseSeverity.MEDIUM)
        )
        put(
            "LED_HEADLAMP",
            Spec("LED 전조등", "LED 전조등 이상", CarPart.HEADLAMP_LEFT, PartCategory.LIGHTING, BaseSeverity.MEDIUM)
        )
        put(
            "H2_SENSOR",
            Spec("수소 센서", "수소 센서 이상", CarPart.BODY, PartCategory.SAFETY, BaseSeverity.MEDIUM)
        )
        put(
            "SCR",
            Spec("요소수 시스템", "배기 후처리 시스템 이상", CarPart.ENGINE, PartCategory.POWERTRAIN, BaseSeverity.MEDIUM)
        )
        put(
            "WATER_SEPARATOR",
            Spec("연료 수분 분리기", "연료 계통 수분 경고", CarPart.ENGINE, PartCategory.POWERTRAIN, BaseSeverity.MEDIUM)
        )
        put(
            "PARTICLE_FILTER",
            Spec("매연 필터", "매연 필터 포집량 경고", CarPart.ENGINE, PartCategory.POWERTRAIN, BaseSeverity.MEDIUM)
        )

        // ── 소모·보충 (LOW) ────────────────────────────────────────────────
        put(
            "LOW_FUEL",
            Spec("연료 부족", "연료 부족", CarPart.ENGINE, PartCategory.ENERGY, BaseSeverity.LOW)
        )
        put(
            "LOW_FUEL_FCEV",
            Spec("수소 부족", "수소 연료 부족", CarPart.ENGINE, PartCategory.ENERGY, BaseSeverity.LOW)
        )
        put(
            "LOW_EV_BATTERY",
            Spec("구동 배터리 부족", "구동 배터리 잔량 부족", CarPart.BATTERY, PartCategory.ENERGY, BaseSeverity.LOW)
        )
        put(
            "LPI",
            Spec("LPG 연료", "LPG 연료 경고", CarPart.ENGINE, PartCategory.ENERGY, BaseSeverity.LOW)
        )
        put(
            "EV_BATTERY_CONDITIONING",
            Spec("배터리 컨디셔닝", "구동 배터리 온도 조절 중", CarPart.BATTERY, PartCategory.ENERGY, BaseSeverity.LOW)
        )
        put(
            "OVER_SPEED",
            Spec("과속", "과속 경고", CarPart.BODY, PartCategory.BODY, BaseSeverity.LOW)
        )
        put(
            "CHILD_PRESENCE_DETECTION_OFF",
            Spec("뒷좌석 승객 감지 해제", "뒷좌석 승객 감지 기능 꺼짐", CarPart.BODY, PartCategory.SAFETY, BaseSeverity.LOW)
        )
        put(
            "PASSENGER_AIRBAG_OFF",
            Spec("동승석 에어백 해제", "동승석 에어백이 해제되어 있습니다", CarPart.AIRBAG, PartCategory.SAFETY, BaseSeverity.LOW)
        )
    }

    fun lookup(type: String): Spec? = SPECS[type.uppercase()]

    /** 진단 대상으로 다루는 경고등 종류 수 — 화면·발표 자료에서 커버리지로 쓴다. */
    val diagnosableCount: Int get() = SPECS.size
}
