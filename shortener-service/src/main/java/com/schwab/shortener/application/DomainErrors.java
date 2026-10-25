package com.schwab.shortener.application;
class InvalidRequest extends RuntimeException { InvalidRequest(String m) { super(m); } }
class NotFound extends RuntimeException { NotFound(String m) { super(m); } }
class Conflict extends RuntimeException { Conflict(String m) { super(m); } }
class ServiceUnavailable extends RuntimeException { ServiceUnavailable(String m) { super(m); } }
