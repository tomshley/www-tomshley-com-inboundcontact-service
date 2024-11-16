package com.tomshley.www.contact.models

import java.time.Instant
import java.util.UUID

trait ContactCard {
  def contactUUID: UUID
  def name: String
  def phone: String
  def email: String
  def message: String
}