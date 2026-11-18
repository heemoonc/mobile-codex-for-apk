"""Execute production migration/query SQL using SQLite; Android Room wiring is device-tested."""
from pathlib import Path
import re, sqlite3, unittest
SOURCE=Path(__file__).resolve().parents[1]/"app/src/main/java/com/example/workflow/notes/NotesDatabase.kt"
class HistoryContract(unittest.TestCase):
 def test_migration_preserves_notes_and_remote_and_history_survives_reopen(self):
  import tempfile
  with tempfile.TemporaryDirectory() as d:
   path=Path(d)/"notes.db"
   db=sqlite3.connect(path)
   db.executescript("CREATE TABLE notes(id INTEGER PRIMARY KEY,title TEXT,body TEXT,updatedAt INTEGER);INSERT INTO notes VALUES(1,'keep','original',7);CREATE TABLE remote_items(id INTEGER PRIMARY KEY,title TEXT,imagePath TEXT);INSERT INTO remote_items VALUES(101,'cached','/sample.png');")
   source=SOURCE.read_text()
   self.assertIn('NOTES_MIGRATION_2_3',source)
   migration=source.split('val NOTES_MIGRATION_2_3=',1)[1].split('@Database',1)[0]
   sql=re.findall(r'execSQL\("([^"\n]+)"\)',migration)
   self.assertTrue(sql)
   for statement in sql:db.execute(statement)
   self.assertEqual([('keep','original')],db.execute('SELECT title,body FROM notes').fetchall())
   self.assertEqual([(101,)],db.execute('SELECT id FROM remote_items').fetchall())
   for i in range(35):db.execute('INSERT INTO sync_history VALUES(?,?,?,?,?)',(str(i),'SUCCEEDED',1,i,'done'))
   prune=re.search(r'@Query\("(DELETE FROM sync_history[^"\n]+)"\)',source).group(1)
   db.execute(prune);db.commit();db.close();db=sqlite3.connect(path)
   query=re.search(r'@Query\("(SELECT \* FROM sync_history[^"\n]+)"\)',source).group(1)
   rows=db.execute(query).fetchall()
   self.assertEqual(30,len(rows));self.assertEqual('34',rows[0][0]);self.assertEqual('5',rows[-1][0]);db.close()
if __name__=='__main__':unittest.main()
