package com.tomshley.www.inboundcontact.models

import java.time.Instant
import java.util.UUID

trait ContactCard {
  def contactUUID: UUID
  def name: String
  def phone: String
  def email: String
  def message: String
}