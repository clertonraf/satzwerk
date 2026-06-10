package com.satzwerk.common

class NotFoundException(message: String) : RuntimeException(message)

class ForbiddenException(message: String) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)

class BadRequestException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
