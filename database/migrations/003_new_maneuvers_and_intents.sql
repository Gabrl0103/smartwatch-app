-- Las maniobras y las órdenes de voz crecieron y estas restricciones aún tenían las primeras:
-- una ruta con un giro suave o unas escaleras no se guardaba, y las órdenes nuevas tampoco.
-- 'cancel' y 'repeat' se quedan porque así están escritas las órdenes anteriores del historial.

ALTER TABLE route_steps DROP CONSTRAINT route_steps_maneuver_check;
ALTER TABLE route_steps ADD CONSTRAINT route_steps_maneuver_check CHECK (maneuver IN (
    'left', 'right', 'slight_left', 'slight_right', 'sharp_left', 'sharp_right',
    'u_turn_left', 'u_turn_right', 'roundabout', 'unknown', 'straight', 'arrive'
));

ALTER TABLE voice_commands DROP CONSTRAINT voice_commands_intent_check;
ALTER TABLE voice_commands ADD CONSTRAINT voice_commands_intent_check CHECK (intent IN (
    'navigate', 'ask', 'guardar_destino', 'repetir_instruccion', 'cancelar_ruta',
    'cuanto_falta', 'estado_guardado', 'cancel', 'repeat'
));
