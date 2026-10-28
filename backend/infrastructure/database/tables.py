"""Tablas para armar consultas con SQLAlchemy Core.

El esquema real lo crean las migraciones de database/migrations/; esto debe coincidir con
ellas. En los tests se usa metadata.create_all sobre SQLite en memoria.
"""

from sqlalchemy import (
    Column,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    MetaData,
    String,
    Table,
    Text,
    UniqueConstraint,
    func,
)

metadata = MetaData()

users = Table(
    "users",
    metadata,
    Column("id", Integer, primary_key=True),
    Column("name", Text, nullable=False),
)

devices = Table(
    "devices",
    metadata,
    Column("id", String(64), primary_key=True),
    Column("user_id", Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
    Column("type", Text, nullable=False),
    Column("registered_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
)

saved_places = Table(
    "saved_places",
    metadata,
    Column("id", Integer, primary_key=True),
    Column("user_id", Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
    Column("name", Text, nullable=False),
    Column("lat", Float, nullable=False),
    Column("lng", Float, nullable=False),
    Column("created_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
)

routes = Table(
    "routes",
    metadata,
    Column("id", Integer, primary_key=True),
    Column("user_id", Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
    Column("origin_lat", Float, nullable=False),
    Column("origin_lng", Float, nullable=False),
    Column("destination_lat", Float, nullable=False),
    Column("destination_lng", Float, nullable=False),
    Column("destination_name", Text, nullable=False),
    Column("created_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
    Column("status", Text, nullable=False),
)

route_steps = Table(
    "route_steps",
    metadata,
    Column("id", Integer, primary_key=True),
    Column("route_id", Integer, ForeignKey("routes.id", ondelete="CASCADE"), nullable=False),
    Column("step_order", Integer, nullable=False),
    Column("maneuver", Text, nullable=False),
    Column("distance_to_next_m", Float, nullable=False),
    Column("lat", Float, nullable=False),
    Column("lng", Float, nullable=False),
    UniqueConstraint("route_id", "step_order", name="uq_route_steps_route_order"),
)

voice_commands = Table(
    "voice_commands",
    metadata,
    Column("id", Integer, primary_key=True),
    Column("user_id", Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
    Column("transcribed_text", Text, nullable=False),
    Column("intent", Text, nullable=False),
    Column("extracted_destination", Text),
    Column("created_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
)

vibration_events = Table(
    "vibration_events",
    metadata,
    Column("id", Integer, primary_key=True),
    Column("route_step_id", Integer, ForeignKey("route_steps.id", ondelete="SET NULL"), index=True),
    Column("pattern", Text, nullable=False),
    Column("emitted_at", DateTime(timezone=True), nullable=False, server_default=func.now()),
)
