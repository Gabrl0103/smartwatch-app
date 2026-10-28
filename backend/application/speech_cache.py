from collections import OrderedDict

DEFAULT_MAX_ENTRIES = 100


class SpeechCache:
    """Caché en memoria de audios por frase. Al llenarse descarta la menos usada."""

    def __init__(self, max_entries: int = DEFAULT_MAX_ENTRIES):
        self._max_entries = max_entries
        self._entries: OrderedDict[str, bytes] = OrderedDict()

    def get(self, text: str) -> bytes | None:
        audio = self._entries.get(text)
        if audio is not None:
            self._entries.move_to_end(text)
        return audio

    def put(self, text: str, audio: bytes) -> None:
        self._entries[text] = audio
        self._entries.move_to_end(text)
        while len(self._entries) > self._max_entries:
            self._entries.popitem(last=False)

    def __len__(self) -> int:
        return len(self._entries)
