-- Usuario único de la app (sin autenticación). Todo se guarda a su nombre.
INSERT INTO users (id, name) VALUES (1, 'Usuario') ON CONFLICT (id) DO NOTHING;
