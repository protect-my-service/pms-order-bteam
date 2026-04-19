-- V2: 주문 취소 시간 제한 + 부분 취소 도입
-- 기존 운영 DB 에 적용할 마이그레이션. 애플리케이션 기동 전 수동 적용.
-- (JPA ddl-auto=validate 이므로 스키마 선행 필수)

ALTER TABLE order_item ADD COLUMN cancelled_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE payment    ADD COLUMN cancelled_amount   DECIMAL(12,2) NOT NULL DEFAULT 0;
