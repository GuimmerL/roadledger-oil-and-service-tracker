package ca.gllavoie.vehicletracker.data.repository

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvRecordReaderTest {
    @Test fun parsesQuotedCommasEscapedQuotesNewlinesAndTrailingEmptyCells() {
        val input = "id,name,note,empty\r\n1,Plain,\"Has,comma and \"\"quote\"\"\nand newline\",\r\n"
        val rows = mutableListOf<List<String>>()

        val count = CsvRecordReader.read(StringReader(input), rows::add)

        assertEquals(2, count)
        assertEquals(listOf("id", "name", "note", "empty"), rows[0])
        assertEquals(listOf("1", "Plain", "Has,comma and \"quote\"\nand newline", ""), rows[1])
    }
}
