package com.pauzfirst.app

import android.content.Context

object Defaults {
    fun load(ctx: Context): List<String> {
        return try {
            ctx.assets.open("default_questions.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
