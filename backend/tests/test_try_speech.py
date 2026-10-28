from scripts.try_speech import MIN_SIMILARITY, normalize, similarity


def test_normalize_ignores_case_accents_and_punctuation():
    assert normalize("¿Vamos? Gira a la IZQUIERDA, en Bolívar.") == "vamos gira a la izquierda en bolivar"


def test_same_phrase_heard_passes():
    heard = "en cincuenta metros gira a la izquierda"
    assert similarity("En cincuenta metros, gira a la izquierda.", heard) >= MIN_SIMILARITY


def test_spoken_instruction_before_the_phrase_fails():
    heard = (
        "Lee en español latinoamericano neutro, con voz clara, calmada y a ritmo pausado: "
        "en cincuenta metros, gira a la izquierda"
    )
    assert similarity("En cincuenta metros, gira a la izquierda.", heard) < MIN_SIMILARITY


def test_short_english_instruction_also_fails():
    heard = "Say in a calm voice: te saliste de la ruta. Recalculando."
    assert similarity("Te saliste de la ruta. Recalculando.", heard) < MIN_SIMILARITY
