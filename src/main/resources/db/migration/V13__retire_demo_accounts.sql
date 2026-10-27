-- Preserve user-modified accounts and all business records.
UPDATE app_users SET active=false
WHERE id IN ('usr-admin-default', 'usr-morkraksa-default')
AND password_hash='$2a$12$R.vL0z15pZq9.KkJ2V8zgehD6QoQ.Fh8kF7wz7kM0U8k4YqO1Q9P.';
-- Remove unchanged bundled templates only when no catalog exists.
DELETE FROM uom_configs WHERE (
(id='uom-case-24' AND name='Case of 24' AND base_unit='Can' AND case_unit_name='Case' AND case_qty=24)
OR (id='uom-case-12' AND name='Case of 12' AND base_unit='Bottle' AND case_unit_name='Case' AND case_qty=12)
OR (id='uom-pack-6' AND name='Pack of 6' AND base_unit='Bottle' AND case_unit_name='Pack' AND case_qty=6)
OR (id='uom-box-1' AND name='Box of 1' AND base_unit='Piece' AND case_unit_name='Box' AND case_qty=1)
OR (id='uom-carton-48' AND name='Carton of 48' AND base_unit='Piece' AND case_unit_name='Carton' AND case_qty=48)
) AND NOT EXISTS (SELECT 1 FROM products);
