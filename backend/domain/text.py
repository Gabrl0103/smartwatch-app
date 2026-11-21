import re
import unicodedata


def plain_words(text: str) -> list[str]:
    """Las palabras de [text] en minúsculas y sin tildes: "Droguería Más" → ["drogueria", "mas"]."""
    plain = unicodedata.normalize("NFD", text.lower())
    plain = "".join(c for c in plain if unicodedata.category(c) != "Mn")
    return re.findall(r"[a-z0-9]+", plain)
