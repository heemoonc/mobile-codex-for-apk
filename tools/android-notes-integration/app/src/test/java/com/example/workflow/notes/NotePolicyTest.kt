package com.example.workflow.notes
import org.junit.Assert.*
import org.junit.Test
class NotePolicyTest {
 @Test fun trimsTitleAndPreservesKoreanMultilineBody() {assertEquals(NoteInput("검증 메모","첫 줄\n둘째 줄  "),validateNote("  검증 메모  ","첫 줄\n둘째 줄  "))}
 @Test fun rejectsBlankTitle() {assertNull(validateNote(" \n ","내용"))}
 @Test fun enforcesBoundaryLengths() {
  assertNotNull(validateNote("가".repeat(120),"나".repeat(10000)))
  assertNull(validateNote("가".repeat(121),"내용"))
  assertNull(validateNote("제목","나".repeat(10001)))
 }
}
