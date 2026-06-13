-- Демо-каталог: factory 08:00, группы, станки, справочник деталей (без заказов).
-- Не часть prod-деплоя: только профиль demo / scripts/seed-demo-catalog.sh

INSERT INTO factory_state (id, factory_started_at)
VALUES (1, '2026-05-22T08:00:00Z')
ON CONFLICT (id) DO NOTHING;

INSERT INTO machine_group (group_id, name, setup_minutes) VALUES
  ('cnc', 'ЧПУ (фрезерный и токарный)', 30),
  ('heavy', 'Тяжёлое оборудование', 45),
  ('finish', 'Сварка и сборка', 20)
ON CONFLICT (group_id) DO NOTHING;

INSERT INTO machine (machine_id, group_id, available_at, status) VALUES
  ('ФРЕЗ-ЧПУ-01', 'cnc', '2026-05-22T08:00:00Z', 'IDLE'),
  ('ТОКАР-ЧПУ-02', 'cnc', '2026-05-22T08:00:00Z', 'IDLE'),
  ('РАСТОЧ-03', 'heavy', '2026-05-22T08:00:00Z', 'IDLE'),
  ('ШЛИФ-04', 'heavy', '2026-05-22T08:00:00Z', 'IDLE'),
  ('СВАРКА-05', 'finish', '2026-05-22T08:00:00Z', 'IDLE'),
  ('СБОРКА-06', 'finish', '2026-05-22T08:00:00Z', 'IDLE')
ON CONFLICT (machine_id) DO NOTHING;

INSERT INTO machine_capability (machine_id, capability) VALUES
  ('ФРЕЗ-ЧПУ-01', 'MILLING'),
  ('ТОКАР-ЧПУ-02', 'TURNING'),
  ('РАСТОЧ-03', 'DEEP_BORING'),
  ('ШЛИФ-04', 'GRINDING'),
  ('СВАРКА-05', 'WELDING'),
  ('СБОРКА-06', 'ASSEMBLY')
ON CONFLICT (machine_id, capability) DO NOTHING;

INSERT INTO part_definition (part_id, priority) VALUES
  ('корпус-бура', 10),
  ('вал-буровой', 8),
  ('гидроблок', 5),
  ('муфта-зажимная', 3),
  ('ниппель-соединительный', 4)
ON CONFLICT (part_id) DO NOTHING;

INSERT INTO part_task (part_id, task_id, sequence, duration_seconds, required_capability) VALUES
  ('корпус-бура', 'черновая-фрезеровка', 0, 5400, 'MILLING'),
  ('корпус-бура', 'расточивание-отверстий', 1, 7200, 'DEEP_BORING'),
  ('корпус-бура', 'чистовая-фрезеровка', 2, 3600, 'MILLING'),
  ('вал-буровой', 'черновая-токарка', 0, 4200, 'TURNING'),
  ('вал-буровой', 'чистовая-токарка', 1, 2700, 'TURNING'),
  ('вал-буровой', 'шлифование-сегментов', 2, 3000, 'GRINDING'),
  ('гидроблок', 'фрезерование-плоскостей', 0, 3300, 'MILLING'),
  ('гидроблок', 'сверление-гидроканалов', 1, 2400, 'DEEP_BORING'),
  ('муфта-зажимная', 'токарка-муфты', 0, 2100, 'TURNING'),
  ('муфта-зажимная', 'сварка-шва-MIG', 1, 1500, 'WELDING'),
  ('ниппель-соединительный', 'сборка-уплотнения', 0, 1800, 'ASSEMBLY')
ON CONFLICT (part_id, task_id) DO NOTHING;
