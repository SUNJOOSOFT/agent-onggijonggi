from uuid import uuid4

from fastapi import Request


def request_id(request: Request) -> str:
    return getattr(request.state, "request_id", uuid4().hex)
