from sqlalchemy import insert
from sqlalchemy.engine import Engine

from domain.models.voice import VoiceCommand
from infrastructure.database.tables import voice_commands


class SqlVoiceCommandRepository:
    def __init__(self, engine: Engine):
        self._engine = engine

    def save(self, user_id: int, command: VoiceCommand) -> int:
        statement = (
            insert(voice_commands)
            .values(
                user_id=user_id,
                transcribed_text=command.text,
                intent=command.intent.value,
                extracted_destination=command.destination,
            )
            .returning(voice_commands.c.id)
        )
        with self._engine.begin() as connection:
            return connection.execute(statement).scalar_one()
