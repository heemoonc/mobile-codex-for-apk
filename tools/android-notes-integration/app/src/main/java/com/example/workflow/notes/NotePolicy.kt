package com.example.workflow.notes
data class NoteInput(val title:String,val body:String)
fun validateNote(title:String,body:String):NoteInput? {
 val clean=title.trim()
 return if(clean.isBlank() || clean.length>120 || body.length>10000)null else NoteInput(clean,body)
}
