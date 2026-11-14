"""
telethon_bridge.py

Python-шар lgram. Увесь мережевий код Telegram (Telethon) живе тут.
Kotlin-сторона (bridge/TelethonBridge.kt) викликає ці функції через Chaquopy.

Кожен акаунт має власний asyncio event loop, що працює у фоновому потоці.
Усі публічні функції тут СИНХРОННІ (блокуючі) з точки зору Kotlin: вони самі
чекають на результат корутини через run_coroutine_threadsafe(...).result(...).
Тому викликати їх з Kotlin треба завжди з фонового потоку (Dispatchers.IO),
НІКОЛИ з головного UI-потоку.
"""

import asyncio
import json
import os
import threading

from telethon import TelegramClient
from telethon.errors import SessionPasswordNeededError, PhoneCodeInvalidError
from telethon.tl.types import (
    MessageMediaPhoto, MessageMediaDocument, DocumentAttributeVideo,
    DocumentAttributeAudio, DocumentAttributeSticker, DocumentAttributeAnimated,
)

# account_id -> {"client": TelegramClient, "loop": asyncio.AbstractEventLoop,
#                "thread": Thread, "phone_code_hash": str | None}
_accounts = {}
_lock = threading.Lock()


def _start_loop(loop: asyncio.AbstractEventLoop):
    asyncio.set_event_loop(loop)
    loop.run_forever()


def _ensure_runtime(account_id: str):
    """Створює (якщо ще нема) окремий event loop + потік для акаунта."""
    with _lock:
        if account_id not in _accounts:
            loop = asyncio.new_event_loop()
            thread = threading.Thread(target=_start_loop, args=(loop,), daemon=True)
            thread.start()
            _accounts[account_id] = {
                "client": None,
                "loop": loop,
                "thread": thread,
                "phone_code_hash": None,
            }
        return _accounts[account_id]


def _run(account_id: str, coro, timeout: float = 60.0):
    runtime = _ensure_runtime(account_id)
    future = asyncio.run_coroutine_threadsafe(coro, runtime["loop"])
    return future.result(timeout)


def _get_client(account_id: str, session_path: str, api_id, api_hash: str) -> TelegramClient:
    runtime = _ensure_runtime(account_id)
    if runtime["client"] is None:
        runtime["client"] = TelegramClient(session_path, int(api_id), api_hash, loop=runtime["loop"])
    return runtime["client"]


def send_code(account_id: str, session_path: str, api_id, api_hash: str, phone: str) -> str:
    """Запитує код підтвердження. Повертає статус: OK або TEXT помилки."""
    client = _get_client(account_id, session_path, api_id, api_hash)

    async def _do():
        if not client.is_connected():
            await client.connect()
        result = await client.send_code_request(phone)
        _accounts[account_id]["phone_code_hash"] = result.phone_code_hash
        return "OK"

    try:
        return _run(account_id, _do())
    except Exception as e:
        return "ERROR:" + str(e)


def sign_in_code(account_id: str, phone: str, code: str) -> str:
    """Повертає: OK | NEED_PASSWORD | ERROR:<текст>"""
    runtime = _accounts.get(account_id)
    if runtime is None or runtime["client"] is None:
        return "ERROR:no_client"
    client = runtime["client"]
    phone_code_hash = runtime.get("phone_code_hash")

    async def _do():
        try:
            await client.sign_in(phone=phone, code=code, phone_code_hash=phone_code_hash)
            return "OK"
        except SessionPasswordNeededError:
            return "NEED_PASSWORD"
        except PhoneCodeInvalidError:
            return "ERROR:invalid_code"

    try:
        return _run(account_id, _do())
    except Exception as e:
        return "ERROR:" + str(e)


def sign_in_password(account_id: str, password: str) -> str:
    runtime = _accounts.get(account_id)
    if runtime is None or runtime["client"] is None:
        return "ERROR:no_client"
    client = runtime["client"]

    async def _do():
        await client.sign_in(password=password)
        return "OK"

    try:
        return _run(account_id, _do())
    except Exception as e:
        return "ERROR:" + str(e)


def check_authorized(account_id: str, session_path: str, api_id, api_hash: str) -> bool:
    """
    Перевіряє, чи сесія жива. Використовується і при вході, і при старті
    застосунку, щоб позначити акаунт неактивним (а не видаляти файл!).
    """
    client = _get_client(account_id, session_path, api_id, api_hash)

    async def _do():
        if not client.is_connected():
            await client.connect()
        return await client.is_user_authorized()

    try:
        return bool(_run(account_id, _do(), timeout=30.0))
    except Exception:
        return False


def get_me_name(account_id: str) -> str:
    """Ім'я власника акаунта (для відображення в бічному меню)."""
    runtime = _accounts.get(account_id)
    if runtime is None or runtime["client"] is None:
        return ""
    client = runtime["client"]

    async def _do():
        me = await client.get_me()
        first = me.first_name or ""
        last = me.last_name or ""
        return (first + " " + last).strip()

    try:
        return _run(account_id, _do(), timeout=20.0)
    except Exception:
        return ""


def _active_client(account_id: str):
    runtime = _accounts.get(account_id)
    if runtime is None or runtime["client"] is None:
        return None
    return runtime["client"]


def _detect_media_type(message) -> str:
    """Повертає один із: '' (нема медіа), photo, video, video_note, voice,
    audio, sticker, gif, document."""
    if message.photo:
        return "photo"
    doc = message.document
    if doc is None:
        return ""
    for attr in doc.attributes:
        if isinstance(attr, DocumentAttributeSticker):
            return "sticker"
        if isinstance(attr, DocumentAttributeAnimated):
            return "gif"
        if isinstance(attr, DocumentAttributeVideo) and attr.round_message:
            return "video_note"
        if isinstance(attr, DocumentAttributeVideo):
            return "video"
        if isinstance(attr, DocumentAttributeAudio) and attr.voice:
            return "voice"
        if isinstance(attr, DocumentAttributeAudio):
            return "audio"
    return "document"


def _message_to_dict(message, me_id) -> dict:
    sender_name = ""
    try:
        sender = message.sender
        if sender is not None:
            sender_name = (getattr(sender, "first_name", "") or "") + " " + (getattr(sender, "last_name", "") or "")
            sender_name = sender_name.strip() or getattr(sender, "title", "") or ""
    except Exception:
        pass

    media_type = _detect_media_type(message)
    filename = ""
    if media_type in ("document", "video", "audio", "gif") and message.document:
        for attr in message.document.attributes:
            if hasattr(attr, "file_name") and attr.file_name:
                filename = attr.file_name
                break

    return {
        "id": message.id,
        "out": bool(message.out),
        "senderName": sender_name,
        "text": message.message or "",
        "date": int(message.date.timestamp()) if message.date else 0,
        "mediaType": media_type,
        "mediaFilename": filename,
        "isEdited": bool(message.edit_date),
    }


def get_dialogs(account_id: str, limit: int = 50) -> str:
    """Повертає JSON-список чатів поточного акаунта."""
    client = _active_client(account_id)
    if client is None:
        return json.dumps({"error": "no_client"})

    async def _do():
        result = []
        async for d in client.iter_dialogs(limit=limit):
            result.append({
                "id": d.id,
                "name": d.name or d.title or "Без назви",
                "lastMessage": (d.message.message if d.message else "") or "",
                "lastDate": int(d.date.timestamp()) if d.date else 0,
                "unreadCount": d.unread_count,
                "isGroup": bool(d.is_group or d.is_channel),
            })
        return result

    try:
        return json.dumps(_run(account_id, _do(), timeout=30.0))
    except Exception as e:
        return json.dumps({"error": str(e)})


def get_messages(account_id: str, chat_id: int, limit: int = 40, offset_id: int = 0) -> str:
    """Повертає JSON-список повідомлень чату (найновіші спочатку)."""
    client = _active_client(account_id)
    if client is None:
        return json.dumps({"error": "no_client"})

    async def _do():
        me = await client.get_me()
        result = []
        async for m in client.iter_messages(chat_id, limit=limit, offset_id=offset_id):
            result.append(_message_to_dict(m, me.id))
        return result

    try:
        return json.dumps(_run(account_id, _do(), timeout=30.0))
    except Exception as e:
        return json.dumps({"error": str(e)})


def send_text(account_id: str, chat_id: int, text: str) -> str:
    client = _active_client(account_id)
    if client is None:
        return "ERROR:no_client"

    async def _do():
        await client.send_message(chat_id, text)
        return "OK"

    try:
        return _run(account_id, _do())
    except Exception as e:
        return "ERROR:" + str(e)


def download_media(account_id: str, chat_id: int, message_id: int, dest_dir: str) -> str:
    """Качає медіа повідомлення в dest_dir. Повертає повний шлях збереженого файлу
    або ERROR:<текст>."""
    client = _active_client(account_id)
    if client is None:
        return "ERROR:no_client"

    async def _do():
        message = await client.get_messages(chat_id, ids=message_id)
        if message is None or not message.media:
            return "ERROR:no_media"
        os.makedirs(dest_dir, exist_ok=True)
        path = await client.download_media(message, file=dest_dir + os.sep)
        if path is None:
            return "ERROR:download_failed"
        return path

    try:
        return _run(account_id, _do(), timeout=120.0)
    except Exception as e:
        return "ERROR:" + str(e)


def forward_message(account_id: str, from_chat_id: int, message_id: int, to_chat_id: int) -> str:
    client = _active_client(account_id)
    if client is None:
        return "ERROR:no_client"

    async def _do():
        await client.forward_messages(to_chat_id, message_id, from_chat_id)
        return "OK"

    try:
        return _run(account_id, _do())
    except Exception as e:
        return "ERROR:" + str(e)


def edit_message(account_id: str, chat_id: int, message_id: int, new_text: str) -> str:
    client = _active_client(account_id)
    if client is None:
        return "ERROR:no_client"

    async def _do():
        await client.edit_message(chat_id, message_id, new_text)
        return "OK"

    try:
        return _run(account_id, _do())
    except Exception as e:
        return "ERROR:" + str(e)


def delete_message(account_id: str, chat_id: int, message_id: int) -> str:
    client = _active_client(account_id)
    if client is None:
        return "ERROR:no_client"

    async def _do():
        await client.delete_messages(chat_id, message_id)
        return "OK"

    try:
        return _run(account_id, _do())
    except Exception as e:
        return "ERROR:" + str(e)


def get_profile(account_id: str) -> str:
    client = _active_client(account_id)
    if client is None:
        return json.dumps({"error": "no_client"})

    async def _do():
        me = await client.get_me()
        return {
            "firstName": me.first_name or "",
            "lastName": me.last_name or "",
            "username": me.username or "",
            "phone": me.phone or "",
            "bio": "",  # повний bio потребує окремого GetFullUserRequest — додамо за потреби
        }

    try:
        return json.dumps(_run(account_id, _do(), timeout=20.0))
    except Exception as e:
        return json.dumps({"error": str(e)})


def update_profile(account_id: str, first_name: str, last_name: str, bio: str) -> str:
    client = _active_client(account_id)
    if client is None:
        return "ERROR:no_client"

    async def _do():
        from telethon.tl.functions.account import UpdateProfileRequest
        await client(UpdateProfileRequest(first_name=first_name, last_name=last_name, about=bio))
        return "OK"

    try:
        return _run(account_id, _do())
    except Exception as e:
        return "ERROR:" + str(e)


def disconnect(account_id: str):
    runtime = _accounts.get(account_id)
    if runtime is None or runtime["client"] is None:
        return
    client = runtime["client"]

    async def _do():
        await client.disconnect()

    try:
        _run(account_id, _do(), timeout=10.0)
    except Exception:
        pass
