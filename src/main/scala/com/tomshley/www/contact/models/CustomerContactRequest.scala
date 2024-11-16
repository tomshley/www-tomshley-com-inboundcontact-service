package com.tomshley.www.contact.models

import java.time.Instant
import java.util.UUID

case class CustomerContactRequest(contactUUID: UUID,
                                  name: String,
                                  phone: String,
                                  email: String,
                                  message: String,
                                  inboundTime: Instant) extends ContactCard

object CustomerContactRequest {
  def apply(contactIdVarchar: String, name: String, phone: String, email: String, message: String, inboundTimeEpochMilli: Long) =
    new CustomerContactRequest(
      UUID.fromString(contactIdVarchar),
      name,
      phone,
      email,
      message,
      Instant.ofEpochMilli(inboundTimeEpochMilli: Long))
}
