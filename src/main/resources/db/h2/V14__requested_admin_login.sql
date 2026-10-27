-- Explicitly requested demo administrator. Applied once, not on every login.
UPDATE app_users SET password_hash='$2a$12$dKoAevQaks7OvbNuD7UDMOWBnECU0V9DcxnOZ/Y2U4asi1a0Emzmi', role='ADMIN', active=true, updated_at=CURRENT_TIMESTAMP
WHERE LOWER(username)='admin';
INSERT INTO app_users (id,username,email,full_name,password_hash,role,active,created_at,updated_at)
SELECT 'usr-admin-requested','admin','admin@retailflow.local','Administrator','$2a$12$dKoAevQaks7OvbNuD7UDMOWBnECU0V9DcxnOZ/Y2U4asi1a0Emzmi','ADMIN',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE LOWER(username)='admin');
DELETE FROM auth_sessions WHERE user_id IN (SELECT id FROM app_users WHERE LOWER(username)='admin');
