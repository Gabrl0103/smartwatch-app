from scripts.try_speech import MIN_SIMILARITY, VERIFY_PHRASES, normalize, number_in_words, similarity


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


def test_verify_uses_two_phrases():
    assert len(VERIFY_PHRASES) == 2


def test_numbers_in_words():
    assert number_in_words(50) == "cincuenta"
    assert number_in_words(26) == "veintiseis"
    assert number_in_words(31) == "treinta y uno"
    assert number_in_words(105) == "ciento cinco"
    assert number_in_words(1200) == "1200"


def test_digits_heard_equal_words_expected():
    assert normalize("En 50 metros, gira a la izquierda.") == normalize("En cincuenta metros, gira a la izquierda.")
    assert normalize("a 1,6 kilómetros") == "a uno coma seis kilometros"
    assert similarity("En cincuenta metros, gira a la izquierda.", "En 50 metros gira a la izquierda") == 1.0
