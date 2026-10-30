import pytest

from application.use_cases.answer_question import MAX_CONTEXT_LENGTH, answer_question
from domain.errors import ValidationError


class FakeAssistant:
    def __init__(self):
        self.calls = []

    def answer_question(self, question, context):
        self.calls.append((question, context))
        return "Faltan unos trescientos metros."


def test_answers_with_the_route_context():
    assistant = FakeAssistant()
    answer = answer_question(assistant, "  ¿cuánto falta?  ", "  Navegando hacia Parque Bolívar.  ")
    assert answer == "Faltan unos trescientos metros."
    assert assistant.calls == [("¿cuánto falta?", "Navegando hacia Parque Bolívar.")]


def test_without_context_asks_without_route():
    assistant = FakeAssistant()
    answer_question(assistant, "¿qué hora es?", None)
    assert assistant.calls == [("¿qué hora es?", "")]


def test_long_context_is_cut():
    assistant = FakeAssistant()
    answer_question(assistant, "¿y ahora?", "x" * 2_000)
    assert len(assistant.calls[0][1]) == MAX_CONTEXT_LENGTH


@pytest.mark.parametrize("question", [None, "", "   ", "a" * 301])
def test_invalid_question_fails_without_asking(question):
    assistant = FakeAssistant()
    with pytest.raises(ValidationError):
        answer_question(assistant, question, "ruta")
    assert assistant.calls == []
