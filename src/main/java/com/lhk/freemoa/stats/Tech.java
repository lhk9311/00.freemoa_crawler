package com.lhk.freemoa.stats;

/** 분류된 기술 하나. name은 화면에 보일 대표 이름 (예: "php 8.5", "PHP" -> "PHP") */
public record Tech(String name, TechGroup group) {
}
