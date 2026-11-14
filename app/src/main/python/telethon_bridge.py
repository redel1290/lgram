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
import threading

from telethon import TelegramClient
from telethon.errors import SessionPasswordNeededError, PhoneCodeInvalidError

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
