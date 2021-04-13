package net.jp.ytake.embulk.input.kintone

import com.kintone.client.model.record.{FieldValue, Record}

class Field {
  private val rec = new Record

  def add(code: String, value: FieldValue): Field = {
    rec.putField(code, value)
    this
  }

  def getRecords: Record = rec
}