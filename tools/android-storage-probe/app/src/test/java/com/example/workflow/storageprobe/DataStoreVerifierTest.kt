package com.example.workflow.storageprobe
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class DataStoreVerifierTest {
 @Test fun realFileContractsExecuteAndCleanTheirOwnedFiles() = runBlocking {
  val dir = Files.createTempDirectory("datastore-verification-").toFile()
  try {
   val results = DataStoreVerifier.run(dir)
   assertEquals(setOf("datastore-write-read", "datastore-reopen", "datastore-atomic-updates", "datastore-types"), results.map { it.name }.toSet())
   assertEquals(4, results.size)
   assertTrue(results.filterNot { it.passed }.toString(), results.all { it.passed })
   assertEquals("owned temporary files must be cleaned", 0, dir.listFiles()!!.size)
  } finally { dir.deleteRecursively() }
 }
}
