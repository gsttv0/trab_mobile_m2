package com.example.trab_mobile_m2

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CardapioDBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "cardapio.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_NAME = "pratos"
        const val COLUMN_ID = "id"
        const val COLUMN_NOME = "nome"
        const val COLUMN_PRECO = "preco"
        const val COLUMN_IMAGEM_LOCAL = "imagem_local"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableStatement = ("CREATE TABLE " + TABLE_NAME + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY, "
                + COLUMN_NOME + " TEXT, "
                + COLUMN_PRECO + " REAL, "
                + COLUMN_IMAGEM_LOCAL + " TEXT)")
        db.execSQL(createTableStatement)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun salvarPrato(id: Int, nome: String, preco: Double, imagemLocal: String) {
        val db = this.writableDatabase
        val cv = ContentValues()

        cv.put(COLUMN_ID, id)
        cv.put(COLUMN_NOME, nome)
        cv.put(COLUMN_PRECO, preco)
        cv.put(COLUMN_IMAGEM_LOCAL, imagemLocal)

        db.insertWithOnConflict(TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun limparBanco() {
        val db = this.writableDatabase
        db.execSQL("DELETE FROM $TABLE_NAME")
        db.close()
    }
}
